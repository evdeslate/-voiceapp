package com.example.speak;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DistilBERT Text Analyzer
 * Uses DistilBERT to analyze text understanding and compare spoken vs expected text
 * Provides semantic comprehension scoring beyond word-level matching
 */
public class DistilBERTTextAnalyzer {
    private static final String TAG = "DistilBERTAnalyzer";
    private static final String MODEL_PATH = "distilbert_fp16.tflite";
    private static final String VOCAB_PATH = "vocab.txt";
    
    // DistilBERT configuration
    private static final int MAX_SEQ_LENGTH = 128; // Reduced for mobile performance
    private static final String CLS_TOKEN = "[CLS]";
    private static final String SEP_TOKEN = "[SEP]";
    private static final String PAD_TOKEN = "[PAD]";
    private static final String UNK_TOKEN = "[UNK]";
    
    private Interpreter interpreter;
    private Map<String, Integer> vocab;
    private boolean isModelLoaded = false;
    
    /**
     * Constructor - loads DistilBERT model and vocabulary
     */
    public DistilBERTTextAnalyzer(Context context) {
        try {
            Log.d(TAG, "Loading DistilBERT model...");
            
            // Load vocabulary
            vocab = loadVocabulary(context);
            Log.d(TAG, "Vocabulary loaded: " + vocab.size() + " tokens");
            
            // Load model
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_PATH);
            
            // Configure interpreter
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2); // Use 2 threads to balance performance
            
            interpreter = new Interpreter(modelBuffer, options);
            isModelLoaded = true;
            
            Log.d(TAG, "✅ DistilBERT model loaded successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load DistilBERT model: " + e.getMessage(), e);
            isModelLoaded = false;
        }
    }
    
    /**
     * Analyze text comprehension by comparing spoken text with expected passage
     * Returns comprehension score (0.0 - 1.0)
     */
    public float analyzeComprehension(String spokenText, String expectedText) {
        if (!isModelLoaded) {
            Log.w(TAG, "Model not loaded, returning default score");
            return 0.5f;
        }
        
        try {
            Log.d(TAG, "Analyzing comprehension...");
            
            // Get embeddings for both texts
            float[] spokenEmbedding = getTextEmbedding(spokenText);
            float[] expectedEmbedding = getTextEmbedding(expectedText);
            
            // Calculate semantic similarity (cosine similarity)
            float similarity = cosineSimilarity(spokenEmbedding, expectedEmbedding);
            
            // Normalize to 0-1 range (cosine similarity is -1 to 1)
            float comprehensionScore = (similarity + 1.0f) / 2.0f;
            
            Log.d(TAG, String.format("Comprehension score: %.2f (similarity: %.2f)", 
                comprehensionScore, similarity));
            
            return comprehensionScore;
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing comprehension", e);
            return 0.5f;
        }
    }
    
    /**
     * Get text embedding from DistilBERT
     */
    private float[] getTextEmbedding(String text) {
        // Tokenize text
        List<Integer> tokenIds = tokenize(text);
        
        // Prepare input tensors
        int[][] inputIds = new int[1][MAX_SEQ_LENGTH];
        int[][] attentionMask = new int[1][MAX_SEQ_LENGTH];
        
        // Fill input arrays
        for (int i = 0; i < Math.min(tokenIds.size(), MAX_SEQ_LENGTH); i++) {
            inputIds[0][i] = tokenIds.get(i);
            attentionMask[0][i] = 1;
        }
        
        // Run inference to get output shape first
        Object[] inputs = {inputIds, attentionMask};
        Map<Integer, Object> outputs = new HashMap<>();
        
        // Try with 768 dimensions first (standard DistilBERT)
        float[][] output768 = new float[1][768];
        outputs.put(0, output768);
        
        try {
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            return output768[0];
        } catch (IllegalArgumentException e) {
            // Model outputs different shape - try with 2 dimensions (classification output)
            Log.w(TAG, "Model outputs [1,2] instead of [1,768] - using classification output");
            float[][] output2 = new float[1][2];
            outputs.clear();
            outputs.put(0, output2);
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            
            // Convert 2D classification to pseudo-embedding by repeating
            float[] embedding = new float[768];
            for (int i = 0; i < 768; i++) {
                embedding[i] = output2[0][i % 2];
            }
            return embedding;
        }
    }
    
    /**
     * Tokenize text using WordPiece tokenization
     */
    private List<Integer> tokenize(String text) {
        List<Integer> tokenIds = new ArrayList<>();
        
        // Add [CLS] token
        tokenIds.add(vocab.getOrDefault(CLS_TOKEN, vocab.get(UNK_TOKEN)));
        
        // Clean and split text
        String cleanText = text.toLowerCase().trim();
        String[] words = cleanText.split("\\s+");
        
        // Tokenize each word
        for (String word : words) {
            // Remove punctuation
            word = word.replaceAll("[^a-z0-9]", "");
            
            if (word.isEmpty()) continue;
            
            // Try to find word in vocabulary
            if (vocab.containsKey(word)) {
                tokenIds.add(vocab.get(word));
            } else {
                // Try WordPiece subword tokenization
                List<Integer> subwordIds = tokenizeWordPiece(word);
                tokenIds.addAll(subwordIds);
            }
            
            // Stop if we're approaching max length
            if (tokenIds.size() >= MAX_SEQ_LENGTH - 1) break;
        }
        
        // Add [SEP] token
        tokenIds.add(vocab.getOrDefault(SEP_TOKEN, vocab.get(UNK_TOKEN)));
        
        return tokenIds;
    }
    
    /**
     * WordPiece subword tokenization
     */
    private List<Integer> tokenizeWordPiece(String word) {
        List<Integer> subwordIds = new ArrayList<>();
        
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            Integer tokenId = null;
            
            // Try to find longest matching subword
            while (start < end) {
                String subword = word.substring(start, end);
                if (start > 0) {
                    subword = "##" + subword; // WordPiece continuation marker
                }
                
                if (vocab.containsKey(subword)) {
                    tokenId = vocab.get(subword);
                    break;
                }
                end--;
            }
            
            if (tokenId != null) {
                subwordIds.add(tokenId);
                start = end;
            } else {
                // Unknown token
                subwordIds.add(vocab.get(UNK_TOKEN));
                start++;
            }
        }
        
        return subwordIds;
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private float cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }
        
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        norm1 = (float) Math.sqrt(norm1);
        norm2 = (float) Math.sqrt(norm2);
        
        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }
        
        return dotProduct / (norm1 * norm2);
    }
    
    /**
     * Load vocabulary from assets
     */
    private Map<String, Integer> loadVocabulary(Context context) throws IOException {
        Map<String, Integer> vocab = new HashMap<>();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(context.getAssets().open(VOCAB_PATH))
        );
        
        String line;
        int index = 0;
        while ((line = reader.readLine()) != null) {
            vocab.put(line.trim(), index);
            index++;
        }
        
        reader.close();
        return vocab;
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
     * Check if model is loaded
     */
    public boolean isReady() {
        return isModelLoaded;
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
        Log.d(TAG, "DistilBERT resources released");
    }
}
