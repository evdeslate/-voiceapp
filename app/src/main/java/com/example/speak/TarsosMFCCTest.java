package com.example.speak;

import android.util.Log;

/**
 * Test TarsosDSP MFCC extraction
 */
public class TarsosMFCCTest {
    private static final String TAG = "TarsosMFCCTest";
    
    /**
     * Test MFCC extraction with synthetic audio
     */
    public static void testMFCCExtraction() {
        Log.d(TAG, "=== TESTING TARSOS MFCC EXTRACTION ===");
        
        try {
            // Create MFCC extractor
            TarsosMFCCExtractor extractor = new TarsosMFCCExtractor();
            
            Log.d(TAG, "✅ TarsosMFCCExtractor created");
            Log.d(TAG, "Sample rate: " + extractor.getSampleRate() + " Hz");
            Log.d(TAG, "FFT size: " + extractor.getFftSize());
            Log.d(TAG, "Coefficients: " + extractor.getNumCoefficients());
            
            // Generate test audio (1 second of 440 Hz sine wave)
            int sampleRate = 16000;
            int duration = 1; // seconds
            int numSamples = sampleRate * duration;
            float[] testAudio = new float[numSamples];
            
            double frequency = 440.0; // A4 note
            for (int i = 0; i < numSamples; i++) {
                testAudio[i] = (float) Math.sin(2.0 * Math.PI * frequency * i / sampleRate);
            }
            
            Log.d(TAG, "Generated test audio: " + numSamples + " samples, 440 Hz sine wave");
            
            // Extract MFCC features
            long startTime = System.currentTimeMillis();
            float[][] mfcc = extractor.extractFeatures(testAudio);
            long endTime = System.currentTimeMillis();
            
            Log.d(TAG, "✅ MFCC extraction complete");
            Log.d(TAG, "Processing time: " + (endTime - startTime) + " ms");
            Log.d(TAG, "MFCC shape: [" + mfcc.length + " frames][" + 
                  (mfcc.length > 0 ? mfcc[0].length : 0) + " coefficients]");
            
            // Display first frame
            if (mfcc.length > 0) {
                Log.d(TAG, "First frame MFCC coefficients:");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(13, mfcc[0].length); i++) {
                    sb.append(String.format("%.3f ", mfcc[0][i]));
                }
                Log.d(TAG, sb.toString());
            }
            
            // Verify output
            if (mfcc.length > 0 && mfcc[0].length == 13) {
                Log.d(TAG, "✅ MFCC extraction successful!");
            } else {
                Log.e(TAG, "❌ MFCC extraction failed - unexpected shape");
            }
            
            Log.d(TAG, "=== TEST COMPLETE ===");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Test failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test MFCC extraction with real audio recording
     */
    public static void testWithRecording(float[] audioSamples) {
        Log.d(TAG, "=== TESTING MFCC WITH REAL AUDIO ===");
        
        try {
            TarsosMFCCExtractor extractor = new TarsosMFCCExtractor();
            
            Log.d(TAG, "Audio samples: " + audioSamples.length);
            Log.d(TAG, "Duration: " + (audioSamples.length / 16000.0f) + " seconds");
            
            // Extract MFCC
            long startTime = System.currentTimeMillis();
            float[][] mfcc = extractor.extractFeatures(audioSamples);
            long endTime = System.currentTimeMillis();
            
            Log.d(TAG, "✅ MFCC extracted from real audio");
            Log.d(TAG, "Processing time: " + (endTime - startTime) + " ms");
            Log.d(TAG, "MFCC shape: [" + mfcc.length + "][" + 
                  (mfcc.length > 0 ? mfcc[0].length : 0) + "]");
            
            // Calculate statistics
            if (mfcc.length > 0) {
                float[] firstCoeff = new float[mfcc.length];
                for (int i = 0; i < mfcc.length; i++) {
                    firstCoeff[i] = mfcc[i][0];
                }
                
                float min = Float.MAX_VALUE;
                float max = Float.MIN_VALUE;
                float sum = 0;
                
                for (float val : firstCoeff) {
                    min = Math.min(min, val);
                    max = Math.max(max, val);
                    sum += val;
                }
                
                float mean = sum / firstCoeff.length;
                
                Log.d(TAG, String.format("First coefficient stats: min=%.3f, max=%.3f, mean=%.3f",
                    min, max, mean));
            }
            
            Log.d(TAG, "=== TEST COMPLETE ===");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Test failed: " + e.getMessage(), e);
        }
    }
}
