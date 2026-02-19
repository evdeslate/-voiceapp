package com.example.speak;

import android.util.Log;

/**
 * Audio Denoiser - Reduces background noise to improve speech recognition
 * Uses spectral subtraction and high-pass filtering
 */
public class AudioDenoiser {
    private static final String TAG = "AudioDenoiser";
    
    // Noise reduction parameters
    private static final int SAMPLE_RATE = 16000;
    private static final float HIGH_PASS_CUTOFF = 100.0f; // Remove low-frequency noise (< 100 Hz) - increased from 80Hz
    private static final float NOISE_FLOOR = 0.015f; // Minimum signal threshold - lowered from 0.02f for better sensitivity
    private static final float NOISE_REDUCTION_FACTOR = 0.8f; // How much to reduce noise - increased from 0.7f
    
    // Noise profile (estimated from first few frames)
    private float[] noiseProfile = null;
    private int noiseProfileFrames = 0;
    private static final int NOISE_PROFILE_DURATION = 10; // frames
    
    /**
     * Apply lightweight denoising for real-time processing
     * Enhanced for better noise reduction in mildly noisy environments
     * @param audioData Raw audio samples (16-bit PCM)
     * @return Denoised audio samples
     */
    public short[] applyLightweightDenoising(short[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return audioData;
        }
        
        // Convert to float for processing
        float[] floatData = shortToFloat(audioData);
        
        // Apply high-pass filter (remove low-frequency noise)
        floatData = highPassFilter(floatData);
        
        // Apply noise gate (remove very quiet sounds)
        floatData = noiseGate(floatData);
        
        // Apply spectral subtraction if noise profile is available
        if (noiseProfile != null) {
            floatData = spectralSubtraction(floatData);
        }
        
        // Apply smoothing to reduce abrupt changes
        floatData = smoothSignal(floatData);
        
        // Convert back to short
        return floatToShort(floatData);
    }
    
    /**
     * Apply denoising to audio buffer
     * @param audioData Raw audio samples (16-bit PCM)
     * @return Denoised audio samples
     */
    public short[] denoise(short[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return audioData;
        }
        
        // Convert to float for processing
        float[] floatData = shortToFloat(audioData);
        
        // Apply high-pass filter (remove low-frequency noise)
        floatData = highPassFilter(floatData);
        
        // Apply noise gate (remove very quiet sounds)
        floatData = noiseGate(floatData);
        
        // Apply spectral subtraction (if noise profile available)
        if (noiseProfile != null) {
            floatData = spectralSubtraction(floatData);
        }
        
        // Convert back to short
        return floatToShort(floatData);
    }
    
    /**
     * High-pass filter to remove low-frequency noise (rumble, hum)
     */
    private float[] highPassFilter(float[] data) {
        // Simple first-order high-pass filter
        float alpha = calculateAlpha(HIGH_PASS_CUTOFF, SAMPLE_RATE);
        float[] filtered = new float[data.length];
        
        filtered[0] = data[0];
        for (int i = 1; i < data.length; i++) {
            filtered[i] = alpha * (filtered[i - 1] + data[i] - data[i - 1]);
        }
        
        return filtered;
    }
    
    /**
     * Calculate filter coefficient
     */
    private float calculateAlpha(float cutoffFreq, int sampleRate) {
        float rc = 1.0f / (2.0f * (float) Math.PI * cutoffFreq);
        float dt = 1.0f / sampleRate;
        return rc / (rc + dt);
    }
    
    /**
     * Noise gate - remove very quiet sounds below threshold
     */
    private float[] noiseGate(float[] data) {
        float[] gated = new float[data.length];
        
        for (int i = 0; i < data.length; i++) {
            if (Math.abs(data[i]) < NOISE_FLOOR) {
                gated[i] = 0.0f; // Silence below threshold
            } else {
                gated[i] = data[i];
            }
        }
        
        return gated;
    }
    
    /**
     * Spectral subtraction - estimate and remove background noise
     */
    private float[] spectralSubtraction(float[] data) {
        // Simple time-domain approximation of spectral subtraction
        // For production, use FFT-based spectral subtraction
        
        float[] denoised = new float[data.length];
        
        for (int i = 0; i < data.length; i++) {
            float signal = data[i];
            float noise = noiseProfile[i % noiseProfile.length];
            
            // Subtract noise estimate
            float cleaned = signal - (noise * NOISE_REDUCTION_FACTOR);
            
            // Prevent over-subtraction (musical noise)
            if (Math.abs(cleaned) < Math.abs(signal) * 0.1f) {
                cleaned = signal * 0.1f;
            }
            
            denoised[i] = cleaned;
        }
        
        return denoised;
    }
    
    /**
     * Update noise profile from audio (call during silence or initial frames)
     */
    public void updateNoiseProfile(short[] audioData) {
        if (noiseProfileFrames >= NOISE_PROFILE_DURATION) {
            return; // Noise profile already established
        }
        
        float[] floatData = shortToFloat(audioData);
        
        if (noiseProfile == null) {
            noiseProfile = new float[floatData.length];
            System.arraycopy(floatData, 0, noiseProfile, 0, floatData.length);
        } else {
            // Average with existing profile
            for (int i = 0; i < Math.min(noiseProfile.length, floatData.length); i++) {
                noiseProfile[i] = (noiseProfile[i] * noiseProfileFrames + floatData[i]) / (noiseProfileFrames + 1);
            }
        }
        
        noiseProfileFrames++;
        
        if (noiseProfileFrames >= NOISE_PROFILE_DURATION) {
            Log.d(TAG, "✅ Noise profile established");
        }
    }
    
    /**
     * Reset noise profile (call when starting new recording)
     */
    public void reset() {
        noiseProfile = null;
        noiseProfileFrames = 0;
        Log.d(TAG, "Noise profile reset");
    }
    
    /**
     * Smooth signal to reduce abrupt changes and artifacts
     */
    private float[] smoothSignal(float[] data) {
        if (data.length < 3) {
            return data;
        }
        
        float[] smoothed = new float[data.length];
        
        // Simple moving average (3-point)
        smoothed[0] = data[0];
        for (int i = 1; i < data.length - 1; i++) {
            smoothed[i] = (data[i - 1] + data[i] + data[i + 1]) / 3.0f;
        }
        smoothed[data.length - 1] = data[data.length - 1];
        
        return smoothed;
    }
    
    /**
     * Convert short[] to float[] (normalized to [-1, 1])
     */
    private float[] shortToFloat(short[] data) {
        float[] floatData = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            floatData[i] = data[i] / 32768.0f;
        }
        return floatData;
    }
    
    /**
     * Convert float[] to short[] (denormalized from [-1, 1])
     */
    private short[] floatToShort(float[] data) {
        short[] shortData = new short[data.length];
        for (int i = 0; i < data.length; i++) {
            // Clamp to prevent overflow
            float value = Math.max(-1.0f, Math.min(1.0f, data[i]));
            shortData[i] = (short) (value * 32767.0f);
        }
        return shortData;
    }
    
    /**
     * Apply automatic gain control (AGC) to normalize volume
     */
    public short[] applyAGC(short[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return audioData;
        }
        
        // Find peak amplitude
        float peak = 0.0f;
        for (short sample : audioData) {
            float abs = Math.abs(sample / 32768.0f);
            if (abs > peak) {
                peak = abs;
            }
        }
        
        // Calculate gain to normalize to 70% of max
        float targetLevel = 0.7f;
        float gain = (peak > 0.01f) ? (targetLevel / peak) : 1.0f;
        
        // Limit gain to prevent over-amplification
        gain = Math.min(gain, 4.0f);
        
        // Apply gain
        short[] normalized = new short[audioData.length];
        for (int i = 0; i < audioData.length; i++) {
            float value = (audioData[i] / 32768.0f) * gain;
            value = Math.max(-1.0f, Math.min(1.0f, value)); // Clamp
            normalized[i] = (short) (value * 32767.0f);
        }
        
        return normalized;
    }
    
    /**
     * Detect if audio contains speech (vs silence/noise)
     */
    public boolean containsSpeech(short[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return false;
        }
        
        // Calculate RMS energy
        float sumSquares = 0.0f;
        for (short sample : audioData) {
            float normalized = sample / 32768.0f;
            sumSquares += normalized * normalized;
        }
        float rms = (float) Math.sqrt(sumSquares / audioData.length);
        
        // Speech typically has RMS > 0.02
        return rms > 0.02f;
    }
}
