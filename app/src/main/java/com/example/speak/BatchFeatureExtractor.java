package com.example.speak;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch Feature Extractor
 * Processes all WAV files and extracts TarsosDSP features for retraining
 */
public class BatchFeatureExtractor {
    
    private static final String TAG = "BatchExtractor";
    private static final String INPUT_DIR = "preprocessed_output_v2";
    private static final String OUTPUT_FILE = "mfcc_features.csv";
    
    private Context context;
    private ONNXRandomForestScorer scorer;
    private AudioPreProcessor audioPreProcessor;
    private AudioDenoiser audioDenoiser;
    private ProgressCallback callback;
    
    public interface ProgressCallback {
        void onProgress(int current, int total, String filename);
        void onComplete(int processed, int skipped, String outputPath);
        void onError(String error);
    }

    
    public BatchFeatureExtractor(Context context, ProgressCallback callback) {
        this.context = context;
        this.callback = callback;
        this.scorer = new ONNXRandomForestScorer(context);
        this.audioPreProcessor = new AudioPreProcessor(16000);
        this.audioDenoiser = new AudioDenoiser();
    }
    
    public void extractAll() {
        new Thread(() -> {
            try {
                // Get input directory
                File inputDir = new File(Environment.getExternalStorageDirectory(), INPUT_DIR);
                if (!inputDir.exists()) {
                    callback.onError("Directory not found: " + inputDir.getAbsolutePath());
                    return;
                }
                
                // Get all WAV files
                File[] wavFiles = inputDir.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".wav"));
                
                if (wavFiles == null || wavFiles.length == 0) {
                    callback.onError("No WAV files found in: " + inputDir.getAbsolutePath());
                    return;
                }
                
                Log.i(TAG, "Found " + wavFiles.length + " WAV files");
                
                // Create output file
                File outputFile = new File(Environment.getExternalStorageDirectory(), OUTPUT_FILE);
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
                
                // Write header
                writeHeader(writer);

                
                // Process each file
                int processed = 0;
                int skipped = 0;
                
                for (int i = 0; i < wavFiles.length; i++) {
                    File wavFile = wavFiles[i];
                    String filename = wavFile.getName();
                    
                    callback.onProgress(i + 1, wavFiles.length, filename);
                    
                    try {
                        // Extract word and label from filename
                        String word = extractWord(filename);
                        int label = extractLabel(filename);
                        
                        if (label == -1) {
                            Log.w(TAG, "Unknown label for: " + filename);
                            skipped++;
                            continue;
                        }
                        
                        // Load audio
                        short[] audio = loadWavFile(wavFile);
                        if (audio == null || audio.length < 3200) {
                            Log.w(TAG, "Invalid audio: " + filename);
                            skipped++;
                            continue;
                        }
                        
                        // Apply preprocessing
                        audio = audioDenoiser.applyLightweightDenoising(audio);
                        audio = audioDenoiser.applyAGC(audio);
                        audio = audioPreProcessor.rmsNormalize(audio);
                        
                        // Extract features
                        float[] features = extractFeatures(audio);
                        if (features == null) {
                            Log.w(TAG, "Feature extraction failed: " + filename);
                            skipped++;
                            continue;
                        }

                        
                        // Write to CSV
                        writeFeatureRow(writer, filename, word, features, label);
                        processed++;
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing " + filename + ": " + e.getMessage());
                        skipped++;
                    }
                }
                
                writer.close();
                
                callback.onComplete(processed, skipped, outputFile.getAbsolutePath());
                
            } catch (Exception e) {
                callback.onError("Extraction failed: " + e.getMessage());
            }
        }).start();
    }
    
    private void writeHeader(BufferedWriter writer) throws IOException {
        StringBuilder header = new StringBuilder("filename,word,");
        for (int i = 0; i < 39; i++) {
            header.append("f").append(i).append(",");
        }
        header.append("label");
        writer.write(header.toString());
        writer.newLine();
    }
    
    private void writeFeatureRow(BufferedWriter writer, String filename, String word, 
                                  float[] features, int label) throws IOException {
        StringBuilder row = new StringBuilder();
        row.append(filename).append(",");
        row.append(word).append(",");
        for (float f : features) {
            row.append(String.format("%.6f", f)).append(",");
        }
        row.append(label);
        writer.write(row.toString());
        writer.newLine();
    }

    
    private float[] extractFeatures(short[] audio) {
        try {
            TarsosMFCCExtractor extractor = new TarsosMFCCExtractor();
            float[][] mfccFrames = extractor.extractFeatures(audio);
            
            if (mfccFrames.length == 0) {
                return null;
            }
            
            // Calculate means, deltas, delta-deltas (same as ONNXRandomForestScorer)
            int numCoeffs = mfccFrames[0].length;
            int numFrames = mfccFrames.length;
            
            float[] features = new float[numCoeffs * 3];
            
            // Means
            for (int c = 0; c < numCoeffs; c++) {
                float sum = 0;
                for (int f = 0; f < numFrames; f++) {
                    sum += mfccFrames[f][c];
                }
                features[c] = sum / numFrames;
            }
            
            // Deltas
            float[] deltas = computeDelta(mfccFrames, numCoeffs, numFrames);
            System.arraycopy(deltas, 0, features, numCoeffs, numCoeffs);
            
            // Delta-deltas
            float[][] deltaFrames = new float[numFrames][numCoeffs];
            for (int f = 0; f < numFrames; f++) {
                for (int c = 0; c < numCoeffs; c++) {
                    if (f == 0) {
                        deltaFrames[f][c] = 0;
                    } else {
                        deltaFrames[f][c] = mfccFrames[f][c] - mfccFrames[f-1][c];
                    }
                }
            }
            float[] deltaDeltas = computeDelta(deltaFrames, numCoeffs, numFrames);
            System.arraycopy(deltaDeltas, 0, features, numCoeffs * 2, numCoeffs);
            
            return features;
            
        } catch (Exception e) {
            Log.e(TAG, "Feature extraction error: " + e.getMessage());
            return null;
        }
    }

    
    private float[] computeDelta(float[][] frames, int numCoeffs, int numFrames) {
        float[] deltas = new float[numCoeffs];
        for (int c = 0; c < numCoeffs; c++) {
            float sum = 0;
            int count = 0;
            for (int f = 1; f < numFrames; f++) {
                sum += frames[f][c] - frames[f-1][c];
                count++;
            }
            deltas[c] = (count > 0) ? (sum / count) : 0;
        }
        return deltas;
    }
    
    private String extractWord(String filename) {
        // Extract word from filename
        // Example: "31keep_mispronounced.wav" -> "keep"
        String name = filename.replace(".wav", "").replace(".WAV", "");
        
        // Remove speaker ID (numbers at start)
        name = name.replaceAll("^\\d+", "");
        
        // Remove label suffix (handle both correct spelling and typo)
        name = name.replace("_mispronounced", "")
                   .replace("_mispronunced", "")  // Handle typo
                   .replace("_correctlypronounced", "")
                   .replace("_correct", "")
                   .replace("_incorrect", "");
        
        return name.trim();
    }
    
    private int extractLabel(String filename) {
        // Extract label from filename
        String lower = filename.toLowerCase();
        
        if (lower.contains("correctlypronounced") || lower.contains("_correct")) {
            return 1;
        } else if (lower.contains("mispronounced") || lower.contains("mispronunced") || lower.contains("_incorrect")) {
            return 0;  // Handle both "mispronounced" and "mispronunced" (typo in some files)
        }
        
        return -1; // Unknown
    }

    
    private short[] loadWavFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            
            // Skip WAV header (44 bytes)
            fis.skip(44);
            
            // Read audio data
            byte[] audioBytes = new byte[(int) (file.length() - 44)];
            fis.read(audioBytes);
            fis.close();
            
            // Convert bytes to short[]
            short[] audioData = new short[audioBytes.length / 2];
            ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
                      .asShortBuffer().get(audioData);
            
            return audioData;
            
        } catch (IOException e) {
            Log.e(TAG, "Error loading WAV: " + e.getMessage());
            return null;
        }
    }
}
