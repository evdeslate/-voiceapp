package com.example.speak;

import android.util.Log;

/**
 * MFCC (Mel-Frequency Cepstral Coefficients) Extractor
 * Extracts acoustic features from audio for pronunciation analysis
 */
public class MFCCExtractor {
    private static final String TAG = "MFCCExtractor";
    
    // MFCC Parameters
    private final int sampleRate;
    private final int numMFCC;
    private final int numFilters;
    private final int fftSize;
    private final int hopSize;
    
    // Mel filterbank (precomputed for performance)
    private float[][] melFilterbank;
    
    // Reusable buffers to avoid GC (performance optimization)
    private float[] frameBuffer;
    private float[] fftRealBuffer;
    private float[] fftImagBuffer;
    private float[] powerSpectrumBuffer;
    private float[] melSpectrumBuffer;
    
    /**
     * Constructor with default parameters
     * @param sampleRate Audio sample rate (typically 16000 Hz)
     */
    public MFCCExtractor(int sampleRate) {
        this(sampleRate, 13, 26, 512, 160);
    }
    
    /**
     * Constructor with custom parameters
     * @param sampleRate Audio sample rate
     * @param numMFCC Number of MFCC coefficients to extract
     * @param numFilters Number of mel filters
     * @param fftSize FFT window size
     * @param hopSize Hop size between frames
     */
    public MFCCExtractor(int sampleRate, int numMFCC, int numFilters, int fftSize, int hopSize) {
        this.sampleRate = sampleRate;
        this.numMFCC = numMFCC;
        this.numFilters = numFilters;
        this.fftSize = fftSize;
        this.hopSize = hopSize;
        
        // Initialize mel filterbank (precomputed once for performance)
        initializeMelFilterbank();
        
        // Preallocate buffers to avoid GC during processing (performance optimization)
        this.frameBuffer = new float[fftSize];
        this.fftRealBuffer = new float[fftSize];
        this.fftImagBuffer = new float[fftSize];
        this.powerSpectrumBuffer = new float[fftSize / 2 + 1];
        this.melSpectrumBuffer = new float[numFilters];
        
        Log.d(TAG, String.format("✅ MFCCExtractor initialized: FFT=%d, Mel=%d, MFCC=%d (optimized with buffer reuse)", 
            fftSize, numFilters, numMFCC));
    }
    
    /**
     * Extract MFCC features from audio samples
     * @param audioData Raw audio samples (16-bit PCM)
     * @return 2D array of MFCC coefficients [numFrames][numMFCC]
     */
    public float[][] extractMFCC(short[] audioData) {
        try {
            // Convert short[] to float[] and normalize
            float[] normalizedAudio = normalizeAudio(audioData);
            
            // Calculate number of frames
            int numFrames = (normalizedAudio.length - fftSize) / hopSize + 1;
            float[][] mfccFeatures = new float[numFrames][numMFCC];
            
            // Process each frame
            for (int frame = 0; frame < numFrames; frame++) {
                int startIdx = frame * hopSize;
                
                // Extract frame
                float[] frameData = extractFrame(normalizedAudio, startIdx);
                
                // Apply window function (Hamming)
                applyHammingWindow(frameData);
                
                // Compute FFT
                float[] powerSpectrum = computePowerSpectrum(frameData);
                
                // Apply mel filterbank
                float[] melSpectrum = applyMelFilterbank(powerSpectrum);
                
                // Compute log
                for (int i = 0; i < melSpectrum.length; i++) {
                    melSpectrum[i] = (float) Math.log(melSpectrum[i] + 1e-10);
                }
                
                // Apply DCT to get MFCC
                mfccFeatures[frame] = computeDCT(melSpectrum);
            }
            
            return mfccFeatures;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting MFCC: " + e.getMessage());
            return new float[0][0];
        }
    }
    
    /**
     * Normalize audio samples to [-1, 1] range
     */
    private float[] normalizeAudio(short[] audioData) {
        float[] normalized = new float[audioData.length];
        for (int i = 0; i < audioData.length; i++) {
            normalized[i] = audioData[i] / 32768.0f;
        }
        return normalized;
    }
    
    /**
     * Extract a frame from audio data (reuses buffer for performance)
     */
    private float[] extractFrame(float[] audio, int startIdx) {
        // Reuse preallocated buffer instead of creating new array
        java.util.Arrays.fill(frameBuffer, 0); // Clear buffer
        for (int i = 0; i < fftSize && (startIdx + i) < audio.length; i++) {
            frameBuffer[i] = audio[startIdx + i];
        }
        return frameBuffer;
    }
    
    /**
     * Apply Hamming window to reduce spectral leakage
     */
    private void applyHammingWindow(float[] frame) {
        for (int i = 0; i < frame.length; i++) {
            frame[i] *= 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (frame.length - 1));
        }
    }
    
    /**
     * Compute power spectrum using FFT (reuses buffers for performance)
     */
    private float[] computePowerSpectrum(float[] frame) {
        // Reuse preallocated buffers instead of creating new arrays
        java.util.Arrays.fill(fftRealBuffer, 0);
        java.util.Arrays.fill(fftImagBuffer, 0);
        System.arraycopy(frame, 0, fftRealBuffer, 0, frame.length);
        
        // Compute power spectrum (reuse buffer)
        for (int k = 0; k < powerSpectrumBuffer.length; k++) {
            float sumReal = 0;
            float sumImag = 0;
            
            for (int n = 0; n < fftSize; n++) {
                double angle = -2 * Math.PI * k * n / fftSize;
                sumReal += fftRealBuffer[n] * Math.cos(angle);
                sumImag += fftRealBuffer[n] * Math.sin(angle);
            }
            
            powerSpectrumBuffer[k] = sumReal * sumReal + sumImag * sumImag;
        }
        
        return powerSpectrumBuffer;
    }
    
    /**
     * Initialize mel filterbank
     */
    private void initializeMelFilterbank() {
        melFilterbank = new float[numFilters][fftSize / 2 + 1];
        
        float lowFreqMel = hzToMel(0);
        float highFreqMel = hzToMel(sampleRate / 2.0f);
        
        // Create mel-spaced frequencies
        float[] melPoints = new float[numFilters + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = lowFreqMel + (highFreqMel - lowFreqMel) * i / (numFilters + 1);
        }
        
        // Convert back to Hz
        int[] binPoints = new int[numFilters + 2];
        for (int i = 0; i < binPoints.length; i++) {
            binPoints[i] = (int) ((fftSize + 1) * melToHz(melPoints[i]) / sampleRate);
        }
        
        // Create triangular filters
        for (int i = 0; i < numFilters; i++) {
            int leftBin = binPoints[i];
            int centerBin = binPoints[i + 1];
            int rightBin = binPoints[i + 2];
            
            // Rising slope
            for (int k = leftBin; k < centerBin; k++) {
                melFilterbank[i][k] = (float) (k - leftBin) / (centerBin - leftBin);
            }
            
            // Falling slope
            for (int k = centerBin; k < rightBin; k++) {
                melFilterbank[i][k] = (float) (rightBin - k) / (rightBin - centerBin);
            }
        }
    }
    
    /**
     * Apply mel filterbank to power spectrum (reuses buffer for performance)
     */
    private float[] applyMelFilterbank(float[] powerSpectrum) {
        // Reuse preallocated buffer instead of creating new array
        for (int i = 0; i < numFilters; i++) {
            float sum = 0;
            for (int k = 0; k < powerSpectrum.length; k++) {
                sum += powerSpectrum[k] * melFilterbank[i][k];
            }
            melSpectrumBuffer[i] = sum;
        }
        
        return melSpectrumBuffer;
    }
    
    /**
     * Compute Discrete Cosine Transform (DCT)
     */
    private float[] computeDCT(float[] melSpectrum) {
        float[] mfcc = new float[numMFCC];
        
        for (int i = 0; i < numMFCC; i++) {
            float sum = 0;
            for (int j = 0; j < melSpectrum.length; j++) {
                sum += melSpectrum[j] * Math.cos(Math.PI * i * (j + 0.5) / melSpectrum.length);
            }
            mfcc[i] = sum;
        }
        
        return mfcc;
    }
    
    /**
     * Convert Hz to Mel scale
     */
    private float hzToMel(float hz) {
        return 2595.0f * (float) Math.log10(1 + hz / 700.0f);
    }
    
    /**
     * Convert Mel to Hz scale
     */
    private float melToHz(float mel) {
        return 700.0f * ((float) Math.pow(10, mel / 2595.0f) - 1);
    }
    
    /**
     * Get statistics from MFCC features (mean, std, delta)
     * Useful for creating fixed-size feature vectors
     * Returns 39 features: 13 mean + 13 std + 13 delta (for ONNX model compatibility)
     */
    public float[] getMFCCStatistics(float[][] mfccFeatures) {
        if (mfccFeatures.length == 0) {
            return new float[numMFCC * 3]; // mean + std + delta
        }
        
        float[] statistics = new float[numMFCC * 3];
        
        // Calculate mean, std, and delta for each MFCC coefficient
        for (int coef = 0; coef < numMFCC; coef++) {
            float sum = 0;
            float sumSq = 0;
            
            for (int frame = 0; frame < mfccFeatures.length; frame++) {
                float value = mfccFeatures[frame][coef];
                sum += value;
                sumSq += value * value;
            }
            
            float mean = sum / mfccFeatures.length;
            float variance = (sumSq / mfccFeatures.length) - (mean * mean);
            float std = (float) Math.sqrt(Math.max(0, variance));
            
            // Calculate delta (rate of change) - simple first-order difference
            float delta = 0;
            if (mfccFeatures.length > 1) {
                float firstValue = mfccFeatures[0][coef];
                float lastValue = mfccFeatures[mfccFeatures.length - 1][coef];
                delta = (lastValue - firstValue) / mfccFeatures.length;
            }
            
            statistics[coef] = mean;                    // First 13: mean
            statistics[numMFCC + coef] = std;           // Next 13: std
            statistics[numMFCC * 2 + coef] = delta;     // Last 13: delta
        }
        
        return statistics;
    }
}
