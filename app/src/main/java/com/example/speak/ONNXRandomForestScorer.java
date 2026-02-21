package com.example.speak;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    private static final String MODEL_PATH = "random_forest_model_retrained.onnx"; // Using retrained model
    
    // Feature logging mode for retraining
    // Set to true to log features to CSV file, false for normal operation
    private static final boolean LOGGING_MODE = false;
    private static final String LOG_FILE = "mfcc_features.csv";
    private int currentLabel = -1; // Set before each prediction: 1=correct, 0=incorrect
    
    public static final int INCORRECT_PRONUNCIATION = 0;
    public static final int CORRECT_PRONUNCIATION = 1;
    
    // Global min/max values from training dataset
    // These values were extracted from the UNNORMALIZED MFCC features (after RMS normalization but before Min-Max)
    // Used for Min-Max normalization: (x - min) / (max - min)
    // 
    // CRITICAL: These MUST match the training data preprocessing exactly
    // Training pipeline: Audio → RMS normalize (0.1) → MFCC extract → Calculate stats (mean/std/max) → Min-Max normalize
    private static final float[] TRAINING_MIN_VALS = {
        -414.423096f, -80.241104f, -166.024902f, -51.319424f, -129.907089f, -53.916298f, -72.291557f, 
        -52.450481f, -51.326775f, -46.281498f, -38.478420f, -33.771980f, -34.021969f, -80.496361f, 
        -44.807404f, -37.762089f, -24.930162f, -23.122988f, -21.161055f, -12.215257f, -17.746891f, 
        -12.131637f, -9.176967f, -10.535486f, -7.217824f, -6.472048f, -124.090370f, -20.766090f, 
        -13.785049f, -20.920406f, -8.031868f, -14.517561f, -9.959176f, -10.545534f, -6.193355f, 
        -7.070988f, -7.626590f, -6.611162f, -6.964625f
    };
    
    private static final float[] TRAINING_MAX_VALS = {
        -124.297523f, 217.845215f, 75.084946f, 119.203186f, 34.772194f, 50.537827f, 31.583584f, 
        27.873722f, 29.042034f, 25.168472f, 20.860155f, 28.103399f, 19.849575f, 63.911774f, 
        53.212135f, 33.866341f, 21.843294f, 36.844193f, 17.593575f, 16.230043f, 11.392966f, 
        10.353777f, 9.431038f, 10.551718f, 8.934065f, 9.334108f, 33.158714f, 29.981518f, 
        35.376286f, 24.160303f, 26.653269f, 8.700697f, 13.841467f, 10.550929f, 10.368282f, 
        6.424534f, 9.235283f, 7.907293f, 10.498958f
    };
    
    private OrtEnvironment env;
    private OrtSession session;
    private TarsosMFCCExtractor mfccExtractor; // Using TarsosDSP
    private boolean isModelLoaded = false;
    private Context context; // Store context for file logging
    
    public ONNXRandomForestScorer(Context context) {
        this.context = context;
        try {
            Log.d(TAG, "🔄 Loading ONNX Random Forest model from: " + MODEL_PATH);
            
            // Initialize TarsosDSP MFCC extractor
            mfccExtractor = new TarsosMFCCExtractor();
            Log.d(TAG, "✅ TarsosDSP MFCC extractor initialized");
            
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
            
        } catch (OrtException e) {
            // Handle ONNX-specific errors (like IR version mismatch)
            if (e.getMessage() != null && e.getMessage().contains("IR version")) {
                Log.e(TAG, "❌ ONNX Model IR version incompatibility detected!");
                Log.e(TAG, "   The ONNX model was exported with a newer version than supported.");
                Log.e(TAG, "   Current ONNX Runtime: 1.16.3 (supports IR version up to 9)");
                Log.e(TAG, "   Model requires: IR version 10+");
                Log.e(TAG, "   Solution: Re-export the model with ONNX opset_version=13 or lower");
                Log.w(TAG, "⚠️  Continuing WITHOUT ONNX Random Forest - using fallback scoring");
            } else {
                Log.e(TAG, "❌ ONNX Runtime error: " + e.getMessage(), e);
            }
            isModelLoaded = false;
        } catch (Exception e) {
            Log.e(TAG, "❌❌❌ Failed to load ONNX model: " + e.getMessage(), e);
            e.printStackTrace();
            isModelLoaded = false;
        }
        
        // Log final status
        if (!isModelLoaded) {
            Log.w(TAG, "⚠️  ONNX Random Forest NOT available - app will use fallback pronunciation scoring");
            Log.w(TAG, "   Speech recognition will still work, but pronunciation scoring may be less accurate");
        }
    }
    
    /**
     * Set the true label for the next pronunciation scoring
     * Call this BEFORE scorePronunciation() when in logging mode
     * 
     * @param label 1 = correct pronunciation, 0 = mispronunciation
     */
    public void setTrueLabel(int label) {
        this.currentLabel = label;
        if (LOGGING_MODE) {
            Log.d(TAG, "True label set: " + (label == 1 ? "CORRECT" : "INCORRECT"));
        }
    }
    
    public PronunciationResult scorePronunciation(short[] audioSamples, String expectedWord) {
        return scorePronunciation(audioSamples, expectedWord, -1);
    }
    
    /**
     * Score pronunciation with optional manual label for feature logging
     * 
     * @param audioSamples Audio samples
     * @param expectedWord Expected word
     * @param manualLabel Manual label for logging: 0=incorrect, 1=correct, -1=unknown (use model prediction)
     * @return Pronunciation result
     */
    public PronunciationResult scorePronunciation(short[] audioSamples, String expectedWord, int manualLabel) {
        if (!isModelLoaded || session == null) {
            Log.w(TAG, "❌ Model not loaded, returning default 50% result");
            return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.5f, 0.5f);
        }
        
        if (audioSamples == null || audioSamples.length == 0) {
            Log.w(TAG, "❌ Empty audio samples for word: " + expectedWord);
            return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.0f, 1.0f);
        }
        
        try {
            // Extract MFCC features using TarsosDSP
            float[][] mfccFeatures = mfccExtractor.extractFeatures(audioSamples);
            
            if (mfccFeatures.length == 0) {
                Log.w(TAG, "❌ Failed to extract MFCC features for: " + expectedWord);
                return new PronunciationResult(INCORRECT_PRONUNCIATION, 0.0f, 1.0f);
            }
            
            // Calculate MFCC statistics (mean, delta, delta-delta)
            float[] mfccStats = calculateMFCCStatistics(mfccFeatures);
            
            // Log features to CSV file if in logging mode
            if (LOGGING_MODE && currentLabel != -1) {
                logFeaturesToFile(context, mfccStats, currentLabel, expectedWord);
                currentLabel = -1; // Reset after logging
            }
            
            // Prepare input tensor [1, num_features]
            long[] shape = {1, mfccStats.length};
            FloatBuffer buffer = FloatBuffer.wrap(mfccStats);
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
            
            // Run inference - model expects input name "float_input"
            Map<String, OnnxTensor> inputs = Collections.singletonMap("float_input", inputTensor);
            OrtSession.Result result = session.run(inputs);
            
            Log.d(TAG, "📊 ONNX Model Inference for '" + expectedWord + "':");
            
            // Get output - handle both probability and label outputs
            Object outputValue = result.get(0).getValue();
            
            Log.d(TAG, "   Output type: " + outputValue.getClass().getName());
            
            float incorrectProb = 0.5f;
            float correctProb = 0.5f;
            int classification = INCORRECT_PRONUNCIATION;
            
            // Check output type and handle accordingly
            if (outputValue instanceof float[][]) {
                // Model outputs probabilities [batch_size, num_classes]
                float[][] output = (float[][]) outputValue;
                incorrectProb = output[0][0];
                correctProb = output[0][1];
                
                Log.d(TAG, String.format("   Raw probabilities: [%.4f, %.4f]", incorrectProb, correctProb));
                
                // Normalize
                float sum = incorrectProb + correctProb;
                if (sum > 0) {
                    incorrectProb /= sum;
                    correctProb /= sum;
                }
                
                classification = correctProb > incorrectProb ? CORRECT_PRONUNCIATION : INCORRECT_PRONUNCIATION;
                
                Log.d(TAG, String.format("   Normalized: Incorrect=%.1f%%, Correct=%.1f%%", 
                    incorrectProb * 100, correctProb * 100));
                Log.d(TAG, String.format("   ✅ Classification: %s (confidence: %.1f%%)", 
                    classification == CORRECT_PRONUNCIATION ? "CORRECT" : "INCORRECT",
                    Math.max(incorrectProb, correctProb) * 100));
                
            } else if (outputValue instanceof long[]) {
                // Model outputs class labels [batch_size]
                long[] output = (long[]) outputValue;
                classification = (int) output[0];
                
                Log.d(TAG, String.format("   Raw class label: %d", classification));
                
                // For label output, use high confidence (0.8) since model is certain
                if (classification == CORRECT_PRONUNCIATION) {
                    correctProb = 0.8f;
                    incorrectProb = 0.2f;
                } else {
                    correctProb = 0.2f;
                    incorrectProb = 0.8f;
                }
                
                Log.d(TAG, String.format("   Assigned: Incorrect=%.1f%%, Correct=%.1f%%", 
                    incorrectProb * 100, correctProb * 100));
                Log.d(TAG, String.format("   ✅ Classification: %s (confidence: %.1f%%)", 
                    classification == CORRECT_PRONUNCIATION ? "CORRECT" : "INCORRECT",
                    Math.max(incorrectProb, correctProb) * 100));
                
            } else if (outputValue instanceof float[]) {
                // Model outputs flat probabilities [num_classes]
                float[] output = (float[]) outputValue;
                if (output.length >= 2) {
                    incorrectProb = output[0];
                    correctProb = output[1];
                    
                    Log.d(TAG, String.format("   Raw probabilities: [%.4f, %.4f]", incorrectProb, correctProb));
                    
                    // Normalize
                    float sum = incorrectProb + correctProb;
                    if (sum > 0) {
                        incorrectProb /= sum;
                        correctProb /= sum;
                    }
                    
                    classification = correctProb > incorrectProb ? CORRECT_PRONUNCIATION : INCORRECT_PRONUNCIATION;
                    
                    Log.d(TAG, String.format("   Normalized: Incorrect=%.1f%%, Correct=%.1f%%", 
                        incorrectProb * 100, correctProb * 100));
                    Log.d(TAG, String.format("   ✅ Classification: %s (confidence: %.1f%%)", 
                        classification == CORRECT_PRONUNCIATION ? "CORRECT" : "INCORRECT",
                        Math.max(incorrectProb, correctProb) * 100));
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
    
    /**
     * Calculate MFCC features with deltas and delta-deltas
     * Returns: [mean_c1, mean_c2, ..., delta_c1, delta_c2, ..., deltaDelta_c1, deltaDelta_c2, ...]
     * Total: 39 features (13 means + 13 deltas + 13 delta-deltas)
     * 
     * IMPORTANT: This matches the training data feature extraction:
     * - Means: Average MFCC coefficients across all frames
     * - Deltas: First derivative (rate of change) of means
     * - Delta-Deltas: Second derivative (acceleration) of means
     * 
     * Input audio has already been RMS-normalized in AudioPreProcessor
     */
    private float[] calculateMFCCStatistics(float[][] mfccFeatures) {
        int numCoeffs = mfccFeatures[0].length;
        int numFrames = mfccFeatures.length;
        
        float[] features = new float[numCoeffs * 3]; // means + deltas + delta-deltas
        
        // Step 1: Calculate mean for each coefficient
        float[] means = new float[numCoeffs];
        for (int c = 0; c < numCoeffs; c++) {
            float sum = 0;
            for (int f = 0; f < numFrames; f++) {
                sum += mfccFeatures[f][c];
            }
            means[c] = sum / numFrames;
        }
        
        // Step 2: Calculate deltas (first derivative)
        // Delta represents the rate of change of MFCC coefficients over time
        float[] deltas = computeDelta(mfccFeatures, numCoeffs, numFrames);
        
        // Step 3: Calculate delta-deltas (second derivative)
        // Delta-delta represents the acceleration of MFCC coefficients
        // We compute deltas of the delta coefficients
        float[][] deltaFrames = new float[numFrames][numCoeffs];
        for (int f = 0; f < numFrames; f++) {
            for (int c = 0; c < numCoeffs; c++) {
                // For delta frames, we use the instantaneous delta at each frame
                if (f == 0) {
                    deltaFrames[f][c] = 0; // No previous frame
                } else {
                    deltaFrames[f][c] = mfccFeatures[f][c] - mfccFeatures[f-1][c];
                }
            }
        }
        float[] deltaDeltas = computeDelta(deltaFrames, numCoeffs, numFrames);
        
        // Concatenate: means + deltas + delta-deltas
        System.arraycopy(means, 0, features, 0, numCoeffs);
        System.arraycopy(deltas, 0, features, numCoeffs, numCoeffs);
        System.arraycopy(deltaDeltas, 0, features, numCoeffs * 2, numCoeffs);
        
        // Log features for debugging
        StringBuilder meanStr = new StringBuilder("MFCC means: [");
        for (int i = 0; i < numCoeffs; i++) {
            meanStr.append(String.format("%.3f", means[i]));
            if (i < numCoeffs - 1) meanStr.append(", ");
        }
        meanStr.append("]");
        Log.d(TAG, meanStr.toString());
        
        StringBuilder deltaStr = new StringBuilder("MFCC deltas: [");
        for (int i = 0; i < numCoeffs; i++) {
            deltaStr.append(String.format("%.3f", deltas[i]));
            if (i < numCoeffs - 1) deltaStr.append(", ");
        }
        deltaStr.append("]");
        Log.d(TAG, deltaStr.toString());
        
        StringBuilder deltaDeltaStr = new StringBuilder("MFCC delta-deltas: [");
        for (int i = 0; i < numCoeffs; i++) {
            deltaDeltaStr.append(String.format("%.3f", deltaDeltas[i]));
            if (i < numCoeffs - 1) deltaDeltaStr.append(", ");
        }
        deltaDeltaStr.append("]");
        Log.d(TAG, deltaDeltaStr.toString());
        
        Log.d(TAG, String.format("Feature vector size: %d (from %d frames x %d coeffs)", 
            features.length, numFrames, numCoeffs));
        
        // NO normalization - model was trained on raw features
        Log.d(TAG, "Using RAW features (no normalization)");
        
        // Log raw features for debugging
        StringBuilder rawStr = new StringBuilder("Raw features (first 10): [");
        for (int i = 0; i < Math.min(10, features.length); i++) {
            rawStr.append(String.format("%.3f", features[i]));
            if (i < Math.min(10, features.length) - 1) rawStr.append(", ");
        }
        rawStr.append("...]");
        Log.d(TAG, rawStr.toString());
        
        return features;
    }
    
    /**
     * Compute delta coefficients (first derivative)
     * Delta represents the rate of change over time
     * 
     * Formula: delta[c] = mean(frame[t+1][c] - frame[t][c]) for all frames
     */
    private float[] computeDelta(float[][] frames, int numCoeffs, int numFrames) {
        float[] deltas = new float[numCoeffs];
        
        for (int c = 0; c < numCoeffs; c++) {
            float sum = 0;
            int count = 0;
            
            // Calculate differences between consecutive frames
            for (int f = 1; f < numFrames; f++) {
                sum += frames[f][c] - frames[f-1][c];
                count++;
            }
            
            // Average the differences
            deltas[c] = (count > 0) ? (sum / count) : 0;
        }
        
        return deltas;
    }
    
    /**
     * Log features to CSV file for retraining
     * Format: word,f0,f1,...,f38,label
     * 
     * @param context Android context for file access
     * @param features Normalized feature vector (39 values)
     * @param label True label: 0=incorrect, 1=correct
     * @param word Expected word
     */
    private void logFeaturesToFile(Context context, float[] features, int label, String word) {
        try {
            File dir = context.getExternalFilesDir(null);
            File file = new File(dir, LOG_FILE);
            boolean writeHeader = !file.exists();
            
            FileWriter fw = new FileWriter(file, true); // Append mode
            BufferedWriter bw = new BufferedWriter(fw);
            
            // Write header once
            if (writeHeader) {
                StringBuilder header = new StringBuilder("word,");
                for (int i = 0; i < 39; i++) {
                    header.append("f").append(i).append(",");
                }
                header.append("label");
                bw.write(header.toString());
                bw.newLine();
                Log.i(TAG, "📝 Created CSV file: " + file.getAbsolutePath());
            }
            
            // Write feature row
            StringBuilder row = new StringBuilder(word).append(",");
            for (float f : features) {
                row.append(String.format("%.6f", f)).append(",");
            }
            row.append(label);
            bw.write(row.toString());
            bw.newLine();
            bw.close();
            
            Log.d(TAG, "✅ Logged: " + word + " (label=" + label + ") to " + LOG_FILE);
            
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to log features: " + e.getMessage(), e);
        }
    }
    
    /**
     * Log features in CSV format for retraining (logcat version - deprecated)
     * Use logFeaturesToFile() instead for better data collection
     */
    @Deprecated
    private void logFeaturesForRetraining(float[] features, String word, int label) {
        StringBuilder csv = new StringBuilder();
        
        // Add all 39 features
        for (int i = 0; i < features.length; i++) {
            csv.append(String.format("%.6f", features[i]));
            if (i < features.length - 1) {
                csv.append(",");
            }
        }
        
        // Add word and label
        csv.append(",").append(word);
        csv.append(",").append(label);
        
        // Log with special tag for easy filtering
        Log.i("FEATURE_CSV", csv.toString());
    }
    
    /**
     * Min-Max normalization using FIXED training dataset min/max values
     * This is critical - we must use the same min/max from training, not per-sample values
     * 
     * Training used global min/max calculated from entire dataset
     * Using per-sample min/max would normalize everything to [0,1] and destroy discrimination
     */
    private float[] minMaxNormalize(float[] features) {
        if (features.length != TRAINING_MIN_VALS.length) {
            Log.e(TAG, String.format("Feature length mismatch: got %d, expected %d", 
                features.length, TRAINING_MIN_VALS.length));
            return features;
        }
        
        float[] normalized = new float[features.length];
        
        // Normalize each feature using its corresponding training min/max
        for (int i = 0; i < features.length; i++) {
            float range = TRAINING_MAX_VALS[i] - TRAINING_MIN_VALS[i];
            
            if (range < 1e-10f) {
                // If range is zero, feature is constant in training data
                normalized[i] = 0.0f;
            } else {
                // Apply same normalization as training: (x - min) / (max - min)
                normalized[i] = (features[i] - TRAINING_MIN_VALS[i]) / range;
                
                // Clamp to [0, 1] range (values outside training range get clamped)
                normalized[i] = Math.max(0.0f, Math.min(1.0f, normalized[i]));
            }
        }
        
        Log.d(TAG, "Applied fixed Min-Max normalization using training dataset min/max");
        
        return normalized;
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
