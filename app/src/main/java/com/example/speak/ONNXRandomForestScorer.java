package com.example.speak;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtException;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

/**
 * ONNX Random Forest Pronunciation Scorer
 * Uses ONNX Runtime to run Random Forest models
 */
public class ONNXRandomForestScorer {
    private static final String TAG = "ONNXRFScorer";
    private static final String MODEL_PATH = "rf_model.onnx"; // Using new RF model
    
    public static final int INCORRECT_PRONUNCIATION = 0;
    public static final int CORRECT_PRONUNCIATION = 1;
    
    private OrtEnvironment env;
    private OrtSession session;
    private MFCCExtractor mfccExtractor;
    private boolean isModelLoaded = false;
    
    public ONNXRandomForestScorer(Context context) {
        try {
            Log.d(TAG, "🔄 Loading ONNX Random Forest model from: " + MODEL_PATH);
            
            // Initialize MFCC extractor
            mfccExtractor = new MFCCExtractor(16000);
            Log.d(TAG, "✅ MFCC extractor initialized");
            
            // Create ONNX Runtime environment
            env = OrtEnvironment.getEnvironment();
            Log.d(TAG, "✅ ONNX Runtime environment created");
            
            // Load model from assets
            byte[] modelBytes = loadModelFromAssets(context, MODEL_PATH);
            Log.d(TAG, String.format("✅ Model loaded from assets: %d bytes", modelBytes.length));
            
            // Create ONNX session
            session = env.createSession(modelBytes);
            isModelLoaded = true;
            
            Log.d(TAG, "✅✅✅ ONNX Random Forest model loaded successfully and ready!");
            logModelInfo();
            
        } catch (Exception e) {
            Log.e(TAG, "❌❌❌ Failed to load ONNX model: " + e.getMessage(), e);
            e.printStackTrace();
            isModelLoaded = false;
        }
    }
    
    public PronunciationResult scorePronunciation(short[] audioSamples, String expectedWord) {
        if (!isModelLoaded || session == null) {
            Log.w(TAG, "❌ Model not loaded, returning default 50% result");
            return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.5f, 0.5f);
        }
        
        if (audioSamples == null || audioSamples.length == 0) {
            Log.w(TAG, "❌ Empty audio samples for word: " + expectedWord);
            return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.0f, 1.0f);
        }
        
        try {
            // Extract MFCC features (most time-consuming operation)
            float[][] mfccFeatures = mfccExtractor.extractMFCC(audioSamples);
            
            if (mfccFeatures.length == 0) {
                Log.w(TAG, "❌ Failed to extract MFCC features for: " + expectedWord);
                return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.0f, 1.0f);
            }
            
            // Get MFCC statistics
            float[] mfccStats = mfccExtractor.getMFCCStatistics(mfccFeatures);
            
            // Prepare input tensor [1, num_features]
            long[] shape = {1, mfccStats.length};
            FloatBuffer buffer = FloatBuffer.wrap(mfccStats);
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
            
            // Run inference - model expects input name "mfcc_input"
            Map<String, OnnxTensor> inputs = Collections.singletonMap("mfcc_input", inputTensor);
            OrtSession.Result result = session.run(inputs);
            
            // Get output - handle both probability and label outputs
            Object outputValue = result.get(0).getValue();
            
            float incorrectProb = 0.5f;
            float correctProb = 0.5f;
            int classification = INCORRECT_PRONUNCIATION;
            
            // Check output type and handle accordingly
            if (outputValue instanceof float[][]) {
                // Model outputs probabilities [batch_size, num_classes]
                float[][] output = (float[][]) outputValue;
                incorrectProb = output[0][0];
                correctProb = output[0][1];
                
                // Normalize
                float sum = incorrectProb + correctProb;
                if (sum > 0) {
                    incorrectProb /= sum;
                    correctProb /= sum;
                }
                
                classification = correctProb > incorrectProb ? CORRECT_PRONUNCIATION : INCORRECT_PRONUNCIATION;
                
            } else if (outputValue instanceof long[]) {
                // Model outputs class labels [batch_size]
                long[] output = (long[]) outputValue;
                classification = (int) output[0];
                
                // For label output, use high confidence (0.8) since model is certain
                if (classification == CORRECT_PRONUNCIATION) {
                    correctProb = 0.8f;
                    incorrectProb = 0.2f;
                } else {
                    correctProb = 0.2f;
                    incorrectProb = 0.8f;
                }
                
            } else if (outputValue instanceof float[]) {
                // Model outputs flat probabilities [num_classes]
                float[] output = (float[]) outputValue;
                if (output.length >= 2) {
                    incorrectProb = output[0];
                    correctProb = output[1];
                    
                    // Normalize
                    float sum = incorrectProb + correctProb;
                    if (sum > 0) {
                        incorrectProb /= sum;
                        correctProb /= sum;
                    }
                    
                    classification = correctProb > incorrectProb ? CORRECT_PRONUNCIATION : INCORRECT_PRONUNCIATION;
                }
            } else {
                Log.e(TAG, "❌ Unexpected output type: " + outputValue.getClass().getName());
                // Return default 50/50
            }
            
            // Cleanup
            inputTensor.close();
            result.close();
            
            return new PronunciationResult(classification, correctProb, incorrectProb);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during ONNX inference for '" + expectedWord + "': " + e.getMessage(), e);
            return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.5f, 0.5f);
        }
    }
    
    public float getPronunciationScore(short[] audioSamples, String expectedWord) {
        PronunciationResult result = scorePronunciation(audioSamples, expectedWord);
        return result.correctConfidence;
    }
    
    public boolean isReady() {
        return isModelLoaded && session != null;
    }
    
    private byte[] loadModelFromAssets(Context context, String modelPath) throws Exception {
        InputStream inputStream = context.getAssets().open(modelPath);
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();
        return buffer;
    }
    
    private void logModelInfo() {
        try {
            Log.d(TAG, "ONNX Model Info:");
            Log.d(TAG, "  Input count: " + session.getInputNames().size());
            Log.d(TAG, "  Output count: " + session.getOutputNames().size());
            Log.d(TAG, "  Input names: " + session.getInputNames());
            Log.d(TAG, "  Output names: " + session.getOutputNames());
        } catch (Exception e) {
            Log.w(TAG, "Could not log model info: " + e.getMessage());
        }
    }
    
    public void release() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            isModelLoaded = false;
            Log.d(TAG, "ONNX model released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing model: " + e.getMessage());
        }
    }
    
    public static class PronunciationResult {
        public final int classification;
        public final float correctConfidence;
        public final float incorrectConfidence;
        
        public PronunciationResult(int classification, float correctConfidence, float incorrectConfidence) {
            this.classification = classification;
            this.correctConfidence = correctConfidence;
            this.incorrectConfidence = incorrectConfidence;
        }
        
        public boolean isCorrect() {
            return classification == CORRECT_PRONUNCIATION;
        }
        
        public float getConfidence() {
            return Math.max(correctConfidence, incorrectConfidence);
        }
        
        public float getScore() {
            return correctConfidence;
        }
    }
}
