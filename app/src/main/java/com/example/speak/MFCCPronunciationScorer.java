package com.example.speak;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * MFCC-based Pronunciation Scorer
 * Uses TensorFlow Lite model to score pronunciation from MFCC features
 * Works offline without internet connection
 */
public class MFCCPronunciationScorer {
    private static final String TAG = "MFCCScorer";
    private static final String MODEL_PATH = "pronunciation_mfcc.tflite";
    
    private Interpreter interpreter;
    private boolean isModelLoaded = false;
    
    /**
     * Constructor - loads the TFLite model
     */
    public MFCCPronunciationScorer(Context context) {
        try {
            Log.d(TAG, "Loading MFCC pronunciation model...");
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_PATH);
            
            // Configure interpreter options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Use 4 threads for faster inference
            
            interpreter = new Interpreter(modelBuffer, options);
            isModelLoaded = true;
            
            Log.d(TAG, "✅ MFCC model loaded successfully");
            
            // Log model info
            logModelInfo();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load MFCC model: " + e.getMessage(), e);
            isModelLoaded = false;
        }
    }
    
    /**
     * Score pronunciation from MFCC features
     * 
     * @param mfccFeatures 2D array of MFCC coefficients [numFrames][13]
     * @return Pronunciation score (0.0 to 1.0, where 1.0 is perfect)
     */
    public float scorePronunciation(float[][] mfccFeatures) {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, returning default score");
            return 0.5f;
        }
        
        if (mfccFeatures == null || mfccFeatures.length == 0) {
            Log.w(TAG, "Empty MFCC features");
            return 0.0f;
        }
        
        try {
            // Extract statistics from MFCC features
            MFCCExtractor extractor = new MFCCExtractor(16000);
            float[] mfccStats = extractor.getMFCCStatistics(mfccFeatures);
            
            // Prepare input (batch size = 1)
            float[][] input = new float[1][mfccStats.length];
            input[0] = mfccStats;
            
            // Prepare output
            float[][] output = new float[1][1];
            
            // Run inference
            interpreter.run(input, output);
            
            // Get score (clamp between 0 and 1)
            float score = Math.max(0.0f, Math.min(1.0f, output[0][0]));
            
            Log.d(TAG, String.format("MFCC Score: %.3f (frames: %d)", score, mfccFeatures.length));
            
            return score;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during inference: " + e.getMessage(), e);
            return 0.5f;
        }
    }
    
    /**
     * Score pronunciation from audio samples directly
     * 
     * @param audioSamples Raw audio samples (16-bit PCM)
     * @return Pronunciation score (0.0 to 1.0)
     */
    public float scorePronunciationFromAudio(short[] audioSamples) {
        if (audioSamples == null || audioSamples.length == 0) {
            Log.w(TAG, "Empty audio samples");
            return 0.0f;
        }
        
        try {
            // Extract MFCC features
            MFCCExtractor extractor = new MFCCExtractor(16000);
            float[][] mfccFeatures = extractor.extractMFCC(audioSamples);
            
            // Score pronunciation
            return scorePronunciation(mfccFeatures);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting MFCC from audio: " + e.getMessage(), e);
            return 0.5f;
        }
    }
    
    /**
     * Get pronunciation feedback based on score
     */
    public String getFeedback(float score) {
        if (score >= 0.9f) {
            return "Excellent pronunciation! 🌟";
        } else if (score >= 0.8f) {
            return "Very good! Keep it up! 👍";
        } else if (score >= 0.7f) {
            return "Good job! Practice more. 😊";
        } else if (score >= 0.6f) {
            return "Not bad, but needs improvement. 📚";
        } else if (score >= 0.5f) {
            return "Keep practicing! You can do it! 💪";
        } else {
            return "Try again! Listen carefully. 🎧";
        }
    }
    
    /**
     * Check if model is loaded and ready
     */
    public boolean isReady() {
        return isModelLoaded && interpreter != null;
    }
    
    /**
     * Load TFLite model from assets
     */
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Log model information
     */
    private void logModelInfo() {
        try {
            int inputCount = interpreter.getInputTensorCount();
            int outputCount = interpreter.getOutputTensorCount();
            
            Log.d(TAG, "Model Info:");
            Log.d(TAG, "  Input tensors: " + inputCount);
            Log.d(TAG, "  Output tensors: " + outputCount);
            
            if (inputCount > 0) {
                int[] inputShape = interpreter.getInputTensor(0).shape();
                Log.d(TAG, "  Input shape: [" + inputShape[0] + ", " + inputShape[1] + "]");
            }
            
            if (outputCount > 0) {
                int[] outputShape = interpreter.getOutputTensor(0).shape();
                Log.d(TAG, "  Output shape: [" + outputShape[0] + ", " + outputShape[1] + "]");
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Could not log model info: " + e.getMessage());
        }
    }
    
    /**
     * Release resources
     */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            isModelLoaded = false;
            Log.d(TAG, "MFCC model closed");
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
