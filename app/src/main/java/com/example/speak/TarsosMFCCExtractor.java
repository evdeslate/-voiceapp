package com.example.speak;

import android.util.Log;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.mfcc.MFCC;
import java.util.ArrayList;
import java.util.List;

/**
 * MFCC extraction using TarsosDSP library
 * Pure Java implementation, Android-compatible
 * 
 * Extracts MFCC coefficients from audio for pronunciation scoring
 */
public class TarsosMFCCExtractor {
    private static final String TAG = "TarsosMFCCExtractor";
    
    // Audio configuration
    private final int sampleRate;
    private final int fftSize;
    private final int numCoefficients;
    private final int numFilters;
    
    // TarsosDSP MFCC processor
    private MFCC mfccProcessor;
    
    // Audio format for TarsosDSP
    private TarsosDSPAudioFormat audioFormat;
    
    /**
     * Create MFCC extractor with default settings
     * Sample rate: 16000 Hz (standard for speech)
     * FFT size: 512
     * Coefficients: 13 (standard for speech recognition)
     * Mel filters: 40
     */
    public TarsosMFCCExtractor() {
        this(16000, 512, 13, 40);
    }
    
    /**
     * Create MFCC extractor with custom settings
     */
    public TarsosMFCCExtractor(int sampleRate, int fftSize, int numCoefficients, int numFilters) {
        this.sampleRate = sampleRate;
        this.fftSize = fftSize;
        this.numCoefficients = numCoefficients;
        this.numFilters = numFilters;
        
        // Create audio format for TarsosDSP
        // Parameters: sampleRate, sampleSizeInBits, channels, signed, bigEndian
        this.audioFormat = new TarsosDSPAudioFormat(
            sampleRate,  // Sample rate (Hz)
            16,          // 16-bit samples
            1,           // Mono
            true,        // Signed
            false        // Little endian
        );
        
        // Initialize TarsosDSP MFCC processor
        mfccProcessor = new MFCC(fftSize, sampleRate, numCoefficients, numFilters, 
                                 133.3334f, // Lower frequency (Hz)
                                 6855.4976f); // Upper frequency (Hz)
        
        Log.d(TAG, String.format("Initialized TarsosMFCC: %d Hz, FFT=%d, coeffs=%d, filters=%d",
            sampleRate, fftSize, numCoefficients, numFilters));
    }
    
    /**
     * Extract MFCC features from audio samples
     * 
     * @param audioSamples Audio samples (float array, -1.0 to 1.0)
     * @return MFCC coefficients [numFrames][numCoefficients]
     */
    public float[][] extractFeatures(float[] audioSamples) {
        if (audioSamples == null || audioSamples.length == 0) {
            Log.w(TAG, "Empty audio samples");
            return new float[0][0];
        }
        
        List<float[]> mfccFrames = new ArrayList<>();
        
        // Frame parameters
        int hopSize = fftSize / 2; // 50% overlap
        int numFrames = (audioSamples.length - fftSize) / hopSize + 1;
        
        Log.d(TAG, String.format("Processing %d samples -> %d frames", 
            audioSamples.length, numFrames));
        
        // Process each frame
        for (int i = 0; i < numFrames; i++) {
            int startIdx = i * hopSize;
            int endIdx = Math.min(startIdx + fftSize, audioSamples.length);
            
            // Extract frame
            float[] frame = new float[fftSize];
            int frameLength = endIdx - startIdx;
            System.arraycopy(audioSamples, startIdx, frame, 0, frameLength);
            
            // Zero-pad if needed
            if (frameLength < fftSize) {
                for (int j = frameLength; j < fftSize; j++) {
                    frame[j] = 0.0f;
                }
            }
            
            // Create AudioEvent for TarsosDSP with proper audio format
            AudioEvent audioEvent = new AudioEvent(audioFormat);
            audioEvent.setFloatBuffer(frame);
            
            // Process with MFCC
            mfccProcessor.process(audioEvent);
            
            // Get MFCC coefficients
            float[] mfcc = mfccProcessor.getMFCC();
            
            // Copy coefficients
            float[] mfccCopy = new float[numCoefficients];
            System.arraycopy(mfcc, 0, mfccCopy, 0, numCoefficients);
            
            mfccFrames.add(mfccCopy);
        }
        
        // Convert list to 2D array
        float[][] result = new float[mfccFrames.size()][numCoefficients];
        for (int i = 0; i < mfccFrames.size(); i++) {
            result[i] = mfccFrames.get(i);
        }
        
        Log.d(TAG, String.format("Extracted MFCC: %d frames x %d coefficients", 
            result.length, numCoefficients));
        
        // Log first frame coefficients for debugging
        if (result.length > 0) {
            StringBuilder coeffStr = new StringBuilder("First frame MFCCs: [");
            for (int i = 0; i < Math.min(numCoefficients, result[0].length); i++) {
                coeffStr.append(String.format("%.3f", result[0][i]));
                if (i < numCoefficients - 1) coeffStr.append(", ");
            }
            coeffStr.append("]");
            Log.d(TAG, coeffStr.toString());
        }
        
        // Log statistics
        if (result.length > 0) {
            float[] means = new float[numCoefficients];
            for (int j = 0; j < numCoefficients; j++) {
                float sum = 0;
                for (int i = 0; i < result.length; i++) {
                    sum += result[i][j];
                }
                means[j] = sum / result.length;
            }
            
            StringBuilder meanStr = new StringBuilder("MFCC means: [");
            for (int i = 0; i < numCoefficients; i++) {
                meanStr.append(String.format("%.3f", means[i]));
                if (i < numCoefficients - 1) meanStr.append(", ");
            }
            meanStr.append("]");
            Log.d(TAG, meanStr.toString());
        }
        
        return result;
    }
    
    /**
     * Extract MFCC features from short audio samples
     * Converts short to float first
     */
    public float[][] extractFeatures(short[] audioSamples) {
        // Convert short to float
        float[] floatSamples = new float[audioSamples.length];
        for (int i = 0; i < audioSamples.length; i++) {
            floatSamples[i] = audioSamples[i] / 32768.0f;
        }
        return extractFeatures(floatSamples);
    }
    
    /**
     * Get sample rate
     */
    public int getSampleRate() {
        return sampleRate;
    }
    
    /**
     * Get number of MFCC coefficients
     */
    public int getNumCoefficients() {
        return numCoefficients;
    }
    
    /**
     * Get FFT size
     */
    public int getFftSize() {
        return fftSize;
    }
}
