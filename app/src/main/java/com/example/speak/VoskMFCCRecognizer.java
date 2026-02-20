package com.example.speak;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.json.JSONArray;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Vosk + MFCC Combined Recognizer with Confidence-Based Sampling
 * Captures audio once, processes with both Vosk (word detection) and MFCC (pronunciation scoring)
 * Implements selective re-analysis for suspicious words using circular audio buffer
 */
public class VoskMFCCRecognizer {
    private static final String TAG = "VoskMFCCRecognizer";
    
    // Audio parameters
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Circular buffer for confidence-based sampling
    private static final int MAX_AUDIO_BUFFER_SECONDS = 15;
    private static final int MAX_AUDIO_BUFFER_SIZE = SAMPLE_RATE * MAX_AUDIO_BUFFER_SECONDS;
    
    /**
     * Word timestamp information from Vosk
     */
    public static class WordTimestamp {
        public String word;
        public float startTime;  // seconds
        public float endTime;    // seconds
        public float confidence;
        public int wordIndex;
        
        public int getStartSample() {
            return (int) (startTime * SAMPLE_RATE);
        }
        
        public int getEndSample() {
            return (int) (endTime * SAMPLE_RATE);
        }
        
        public int getDurationSamples() {
            return getEndSample() - getStartSample();
        }
    }
    
    /**
     * Circular audio buffer for storing recent audio
     */
    private static class CircularAudioBuffer {
        private short[] buffer;
        private int writePos = 0;
        private int size;
        private long totalSamplesWritten = 0;
        
        public CircularAudioBuffer(int maxSize) {
            this.buffer = new short[maxSize];
            this.size = maxSize;
        }
        
        public synchronized void write(short[] data, int length) {
            for (int i = 0; i < length; i++) {
                buffer[writePos] = data[i];
                writePos = (writePos + 1) % size;
                totalSamplesWritten++;
            }
        }
        
        public synchronized short[] extractSegment(long startSample, long endSample) {
            long currentPos = totalSamplesWritten;
            long oldestSample = Math.max(0, currentPos - size);
            
            // Check if requested segment is still in buffer
            if (startSample < oldestSample) {
                Log.w(TAG, String.format("Requested audio segment too old (start: %d, oldest: %d)", startSample, oldestSample));
                return null;
            }
            
            int length = (int) (endSample - startSample);
            if (length <= 0 || length > size) {
                Log.w(TAG, "Invalid segment length: " + length);
                return null;
            }
            
            short[] segment = new short[length];
            
            // Calculate circular buffer positions
            int bufferStart = (int) ((startSample - oldestSample + writePos) % size);
            
            for (int i = 0; i < length; i++) {
                int pos = (bufferStart + i) % size;
                segment[i] = buffer[pos];
            }
            
            return segment;
        }
        
        public synchronized long getTotalSamplesWritten() {
            return totalSamplesWritten;
        }
    }
    
    private Context context;
    private Model voskModel;
    private SpeechService speechService;
    private MFCCExtractor mfccExtractor;
    private ONNXRandomForestScorer onnxRandomForestScorer; // ONNX Random Forest for pronunciation
    private AudioDenoiser audioDenoiser; // Audio denoising for better recognition
    private AudioPreProcessor audioPreProcessor; // NEW: Audio preprocessing (noise gate + bandpass filter)
    private DistilBERTTextAnalyzer textAnalyzer; // Text comprehension analysis (async)
    private ReadingLevelClassifier levelClassifier; // Reading level classification
    
    // Audio recording for MFCC analysis
    private AudioRecord audioRecord;
    private Thread audioRecordingThread;
    private boolean isRecordingAudio = false;
    private List<short[]> audioBuffers;
    private List<short[]> allWordAudioBuffers; // Store audio for each word for post-processing
    private List<String> allRecognizedWords; // Store recognized words for matching with audio
    private List<String> allExpectedWords; // Store expected words for matching with audio
    private List<Boolean> matchBasedCorrectness; // Store match-based correctness for fallback
    
    // Confidence-based sampling components
    private CircularAudioBuffer circularAudioBuffer;
    private List<WordTimestamp> wordTimestamps;
    private long audioRecordingStartTime = 0; // System time when recording started
    
    private String[] expectedWords;
    private String expectedPassageText = ""; // Store full expected passage for comprehension analysis
    private String currentStudentId = "";
    private String currentStudentName = "";
    private String currentPassageTitle = "";
    private StringBuilder recognizedTextBuilder; // Build full recognized text
    private int currentWordIndex = 0;
    private int maxWordIndexReached = 0; // Track the maximum word index reached (for completion check)
    private List<Float> pronunciationScores;
    private int correctWordsCount = 0;  // Track correct words
    private int incorrectWordsCount = 0;  // Track incorrect words
    private long recognitionStartTime = 0; // Track reading time for WPM calculation
    
    private RecognitionCallback callback;
    private boolean isRecognizing = false;
    
    /**
     * Callback interface for recognition events
     */
    public interface RecognitionCallback {
        void onReady();
        void onWordRecognized(String recognizedWord, String expectedWord, int wordIndex, float pronunciationScore, boolean isCorrect);
        void onPartialResult(String partial);
        void onComplete(float overallAccuracy, float averagePronunciation, float comprehensionScore, ReadingLevelClassifier.ReadingLevelResult readingLevel);
        void onComprehensionUpdated(float comprehensionScore); // Optional: called when comprehension finishes async
        void onPronunciationUpdated(float pronunciationScore); // Optional: called when Random Forest analysis finishes async
        void onRFAnalysisComplete(List<Boolean> wordCorrectness); // NEW: Called when RF analysis completes with per-word results
        void onRFAnalysisCompleteWithConfidence(List<Boolean> wordCorrectness, List<Float> wordConfidences); // NEW: Called with confidence scores for sampling
        void onSessionSaved(ReadingSession savedSession); // Optional: called when session is saved to database with final scores
        void onError(String error);
    }
    
    /**
     * Constructor
     */
    public VoskMFCCRecognizer(Context context) {
        this.context = context;
        this.mfccExtractor = new MFCCExtractor(SAMPLE_RATE);
        
        // Initialize ONNX Random Forest scorer (may fail if model incompatible)
        try {
            this.onnxRandomForestScorer = new ONNXRandomForestScorer(context);
            if (this.onnxRandomForestScorer.isReady()) {
                Log.d(TAG, "✅ ONNX Random Forest scorer ready");
            } else {
                Log.w(TAG, "⚠️  ONNX Random Forest scorer not ready - will use fallback");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize ONNX scorer: " + e.getMessage());
            this.onnxRandomForestScorer = null;
        }
        
        this.audioDenoiser = new AudioDenoiser(); // Initialize denoiser
        this.audioPreProcessor = new AudioPreProcessor(SAMPLE_RATE); // NEW: Initialize audio preprocessor
        this.textAnalyzer = new DistilBERTTextAnalyzer(context); // Initialize DistilBERT (async)
        this.levelClassifier = new ReadingLevelClassifier(context); // Initialize Random Forest for reading level
        this.pronunciationScores = new ArrayList<>();
        this.audioBuffers = new ArrayList<>();
        this.allWordAudioBuffers = new ArrayList<>();
        this.allRecognizedWords = new ArrayList<>();
        this.allExpectedWords = new ArrayList<>();
        this.matchBasedCorrectness = new ArrayList<>();
        this.recognizedTextBuilder = new StringBuilder();
        
        // Initialize confidence-based sampling components
        this.circularAudioBuffer = new CircularAudioBuffer(MAX_AUDIO_BUFFER_SIZE);
        this.wordTimestamps = new ArrayList<>();
        
        Log.d(TAG, "✅ VoskMFCCRecognizer initialized with confidence-based sampling + audio preprocessing");
        Log.d(TAG, String.format("   Circular buffer: %d seconds capacity", MAX_AUDIO_BUFFER_SECONDS));
        Log.d(TAG, "   Audio preprocessing: noise gate + bandpass filter enabled");
        Log.d(TAG, "   ONNX Random Forest: " + (onnxRandomForestScorer != null && onnxRandomForestScorer.isReady() ? "Available" : "Not available (using fallback)"));
    }
    
    /**
     * Initialize Vosk model (call this once, preferably in background)
     * Now uses singleton model from SpeakApplication
     */
    public void initializeModel(final ModelInitCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Checking Vosk model status...");
                
                // Check if model is already loaded in Application class
                if (SpeakApplication.isVoskModelReady && SpeakApplication.voskModel != null) {
                    Log.d(TAG, "✅ Using preloaded Vosk model from Application");
                    voskModel = SpeakApplication.voskModel;
                    callback.onModelReady();
                    return;
                }
                
                // Check if model is currently loading
                if (SpeakApplication.isVoskModelLoading) {
                    Log.d(TAG, "⏳ Vosk model is loading in background, waiting...");
                    
                    // Wait for model to finish loading (max 60 seconds)
                    int waitCount = 0;
                    while (SpeakApplication.isVoskModelLoading && waitCount < 120) {
                        Thread.sleep(500);
                        waitCount++;
                    }
                    
                    if (SpeakApplication.isVoskModelReady && SpeakApplication.voskModel != null) {
                        Log.d(TAG, "✅ Vosk model loaded successfully");
                        voskModel = SpeakApplication.voskModel;
                        callback.onModelReady();
                        return;
                    } else {
                        throw new Exception("Model loading timed out or failed");
                    }
                }
                
                // If model failed to load in Application, try loading here
                if (SpeakApplication.voskModelError != null) {
                    throw new Exception("Model failed in Application: " + SpeakApplication.voskModelError);
                }
                
                // Fallback: Load model here (shouldn't happen normally)
                Log.w(TAG, "⚠️ Model not loaded in Application, loading now...");
                loadModelDirectly(callback);
                    
            } catch (Exception e) {
                Log.e(TAG, "❌ Error initializing Vosk model", e);
                e.printStackTrace();
                callback.onModelError("Failed to load model: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Fallback method to load model directly (if Application loading failed)
     */
    private void loadModelDirectly(ModelInitCallback callback) {
        try {
            Log.d(TAG, "Loading model directly...");
            
            File modelDir = new File(context.getFilesDir(), "vosk-model-en-us-0.22-lgraph");
            
            if (!modelDir.exists() || !isModelComplete(modelDir)) {
                Log.d(TAG, "Extracting model from assets...");
                extractModelFromAssets(modelDir);
                Log.d(TAG, "Model extraction complete");
            }
            
            String modelPath = modelDir.getAbsolutePath();
            Log.d(TAG, "Loading model from: " + modelPath);
            
            voskModel = new Model(modelPath);
            SpeakApplication.voskModel = voskModel;
            SpeakApplication.isVoskModelReady = true;
            
            Log.d(TAG, "✅ Vosk model initialized successfully");
            callback.onModelReady();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load model directly", e);
            callback.onModelError("Failed to load model: " + e.getMessage());
        }
    }
    
    /**
     * Check if model directory has all required files
     */
    private boolean isModelComplete(java.io.File modelDir) {
        if (!modelDir.exists()) return false;
        
        // Check for essential model files
        java.io.File amDir = new java.io.File(modelDir, "am");
        java.io.File confDir = new java.io.File(modelDir, "conf");
        java.io.File graphDir = new java.io.File(modelDir, "graph");
        java.io.File ivectorDir = new java.io.File(modelDir, "ivector");
        
        return amDir.exists() && confDir.exists() && graphDir.exists() && ivectorDir.exists();
    }
    
    /**
     * Extract model from assets to files directory
     */
    private void extractModelFromAssets(java.io.File targetDir) throws java.io.IOException {
        String assetPath = "sync/vosk-model-en-us-0.22-lgraph";
        
        // Create target directory
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        // Copy all files from assets
        copyAssetFolder(assetPath, targetDir.getAbsolutePath());
    }
    
    /**
     * Recursively copy folder from assets
     */
    private void copyAssetFolder(String assetPath, String targetPath) throws java.io.IOException {
        android.content.res.AssetManager assetManager = context.getAssets();
        String[] files = assetManager.list(assetPath);
        
        if (files == null || files.length == 0) {
            // It's a file, copy it
            copyAssetFile(assetPath, targetPath);
        } else {
            // It's a folder, create it and copy contents
            java.io.File targetDir = new java.io.File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            for (String file : files) {
                String assetFilePath = assetPath + "/" + file;
                String targetFilePath = targetPath + "/" + file;
                copyAssetFolder(assetFilePath, targetFilePath);
            }
        }
    }
    
    /**
     * Copy single file from assets
     */
    private void copyAssetFile(String assetPath, String targetPath) throws java.io.IOException {
        android.content.res.AssetManager assetManager = context.getAssets();
        
        java.io.InputStream in = assetManager.open(assetPath);
        java.io.File targetFile = new java.io.File(targetPath);
        
        // Create parent directories if needed
        java.io.File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        java.io.OutputStream out = new java.io.FileOutputStream(targetFile);
        
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        
        in.close();
        out.flush();
        out.close();
    }
    
    /**
     * Start recognition with expected words and passage text
     */
    public void startRecognition(String[] expectedWords, String passageText, String studentId, 
                                 String studentName, String passageTitle, RecognitionCallback callback) {
        Log.d(TAG, "=== START RECOGNITION CALLED ===");
        Log.d(TAG, "Expected words count: " + (expectedWords != null ? expectedWords.length : 0));
        Log.d(TAG, "Passage text length: " + (passageText != null ? passageText.length() : 0));
        
        // Check if model is ready
        Log.d(TAG, "Checking model status...");
        Log.d(TAG, "  voskModel: " + (voskModel != null ? "NOT NULL" : "NULL"));
        Log.d(TAG, "  SpeakApplication.voskModel: " + (SpeakApplication.voskModel != null ? "NOT NULL" : "NULL"));
        Log.d(TAG, "  SpeakApplication.isVoskModelLoading: " + SpeakApplication.isVoskModelLoading);
        Log.d(TAG, "  SpeakApplication.isVoskModelReady: " + SpeakApplication.isVoskModelReady);
        Log.d(TAG, "  SpeakApplication.voskModelError: " + SpeakApplication.voskModelError);
        
        if (voskModel == null) {
            String errorMsg = "Vosk model not loaded";
            
            // Provide more specific error information
            if (SpeakApplication.isVoskModelLoading) {
                errorMsg = "Model is still loading, please wait a moment and try again...";
                Log.w(TAG, "⏳ Model still loading, user should wait");
            } else if (SpeakApplication.voskModelError != null) {
                errorMsg = "Model failed to load: " + SpeakApplication.voskModelError;
                Log.e(TAG, "❌ Model load error: " + SpeakApplication.voskModelError);
            } else {
                errorMsg = "Model not initialized. Please restart the app.";
                Log.e(TAG, "❌ Model not initialized");
            }
            
            Log.e(TAG, "❌ Cannot start recognition: " + errorMsg);
            callback.onError(errorMsg);
            return;
        }
        
        Log.d(TAG, "✅ Model check passed, voskModel is available");
        
        if (isRecognizing) {
            Log.w(TAG, "Already recognizing");
            return;
        }
        
        this.expectedWords = expectedWords;
        this.expectedPassageText = passageText;
        this.currentStudentId = studentId;
        this.currentStudentName = studentName;
        this.currentPassageTitle = passageTitle;
        this.callback = callback;
        this.currentWordIndex = 0;
        this.maxWordIndexReached = 0; // Reset max word index
        this.pronunciationScores.clear();
        this.recognizedTextBuilder = new StringBuilder();
        this.audioBuffers.clear();
        this.allWordAudioBuffers.clear(); // Clear audio buffers for Random Forest
        this.allRecognizedWords.clear(); // Clear recognized words list
        this.allExpectedWords.clear(); // Clear expected words list
        this.matchBasedCorrectness.clear(); // Clear match-based correctness list
        this.correctWordsCount = 0;  // Reset correct count
        this.incorrectWordsCount = 0;  // Reset incorrect count
        this.recognitionStartTime = System.currentTimeMillis(); // Track start time
        this.isRecognizing = true;
        
        // Reset audio denoiser for new recording
        audioDenoiser.reset();
        audioPreProcessor.reset(); // NEW: Reset audio preprocessor
        Log.d(TAG, "🎤 Audio denoiser + preprocessor initialized");
        
        // Start audio recording for MFCC analysis
        startAudioRecording();
        
        try {
            // ═══════════════════════════════════════════════════════════════════════════
            // FREE-FORM RECOGNITION: Let Vosk output what child actually says
            // ═══════════════════════════════════════════════════════════════════════════
            // 
            // SOLUTION: Use free-form recognition so RF model gets accurate audio data
            //
            // HOW IT WORKS:
            // 1. Vosk outputs what child actually says (e.g., "pader" for "father")
            // 2. Text matching compares "pader" vs "father" → INCORRECT ✅
            // 3. RF model analyzes audio → confirms mispronunciation ✅
            //
            // EXAMPLE:
            // - User says "pader" (mispronounced)
            // - Vosk outputs "pader" (what was actually said)
            // - Text matching: "pader" != "father" ❌ CORRECT!
            // - RF model: Analyzes audio, confirms mispronunciation ✅
            //
            // BENEFIT:
            // - Both text matching AND RF model can catch mispronunciations
            // - More accurate assessment of pronunciation
            // - Catches Filipino L1 interference (f→p, v→b, th→d)
            // ═══════════════════════════════════════════════════════════════════════════
            
            Log.d(TAG, "✅ Using FREE-FORM recognition for accurate mispronunciation detection");
            Log.d(TAG, "   Vosk: Outputs what child actually says");
            Log.d(TAG, "   Text matching: Detects mispronunciations");
            Log.d(TAG, "   RF Model: Confirms pronunciation accuracy");
            
            // Double-check model is still accessible
            if (voskModel == null) {
                throw new IOException("Model became null before creating recognizer. Please restart the app.");
            }
            
            // Create free-form Vosk recognizer (no grammar constraints)
            Log.d(TAG, "Creating Vosk recognizer with sample rate: " + SAMPLE_RATE);
            Log.d(TAG, "Model status: " + (voskModel != null ? "Available" : "NULL"));
            Log.d(TAG, "Model class: " + (voskModel != null ? voskModel.getClass().getName() : "N/A"));
            
            Recognizer recognizer = null;
            try {
                Log.d(TAG, "Attempting to create Recognizer object...");
                
                // Use free-form recognition to capture actual pronunciation
                recognizer = new Recognizer(voskModel, SAMPLE_RATE);
                
                Log.d(TAG, "✅ Recognizer object created successfully");
                
                // PERFORMANCE OPTIMIZATION: Enable faster partial results
                recognizer.setMaxAlternatives(1); // Only need best match (faster)
                recognizer.setWords(true); // Enable word-level timestamps
                
                Log.d(TAG, "✅ Free-form recognizer configured successfully:");
                Log.d(TAG, "   - Free-form recognition (captures actual pronunciation)");
                Log.d(TAG, "   - Max alternatives: 1");
                Log.d(TAG, "   - Word timestamps: enabled");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Recognizer creation failed: " + e.getMessage(), e);
                Log.e(TAG, "Exception type: " + e.getClass().getName());
                if (e.getCause() != null) {
                    Log.e(TAG, "Cause: " + e.getCause().getMessage());
                }
                throw new IOException("Failed to create recognizer. The Vosk model may not be fully loaded. " +
                    "Please wait a moment and try again, or restart the app if the problem persists.", e);
            }
            
            if (recognizer == null) {
                throw new IOException("Recognizer creation returned null. Model may be corrupted.");
            }
            
            Log.d(TAG, "✅ Free-form recognizer created successfully");
            
            // Create speech service
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            Log.d(TAG, "✅ Speech service created successfully");
            
            // Set up recognition listener
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                    Log.d(TAG, "🔊 onPartialResult called: " + hypothesis);
                    try {
                        JSONObject json = new JSONObject(hypothesis);
                        String partial = "";
                        
                        // Try to extract partial text
                        if (json.has("partial")) {
                            partial = json.optString("partial", "");
                            if (!partial.isEmpty()) {
                                Log.d(TAG, "✅ Extracted partial from json.partial: '" + partial + "'");
                            } else {
                                Log.w(TAG, "⚠️ Partial field exists but is empty");
                            }
                        } else {
                            Log.w(TAG, "⚠️ No 'partial' field in JSON");
                        }
                        
                        // If partial is empty, log the JSON structure
                        if (partial.isEmpty()) {
                            Log.w(TAG, "⚠️ Empty partial result");
                            Log.w(TAG, "JSON keys: " + json.keys().toString());
                            Log.w(TAG, "Full JSON: " + hypothesis);
                        }
                        
                        // Partial results are for UI preview ONLY - do NOT process for scoring
                        if (!partial.isEmpty() && VoskMFCCRecognizer.this.callback != null) {
                            VoskMFCCRecognizer.this.callback.onPartialResult(partial);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing partial result", e);
                        e.printStackTrace();
                    }
                }
                
                @Override
                public void onResult(String hypothesis) {
                    Log.d(TAG, "🔊 onResult called: " + hypothesis);
                    try {
                        JSONObject json = new JSONObject(hypothesis);
                        String text = "";
                        
                        // COMPREHENSIVE JSON EXTRACTION: Try multiple paths to get text
                        
                        // Method 1: Direct "text" field (most common)
                        if (json.has("text")) {
                            text = json.optString("text", "");
                            if (!text.isEmpty()) {
                                Log.d(TAG, "✅ Extracted text from json.text: '" + text + "'");
                            }
                        }
                        
                        // Method 2: Extract from alternatives array
                        if (text.isEmpty() && json.has("alternatives")) {
                            JSONArray alternatives = json.getJSONArray("alternatives");
                            Log.d(TAG, String.format("Found %d alternatives", alternatives.length()));
                            
                            if (alternatives.length() > 0) {
                                JSONObject firstAlt = alternatives.getJSONObject(0);
                                text = firstAlt.optString("text", "");
                                if (!text.isEmpty()) {
                                    Log.d(TAG, "✅ Extracted text from alternatives[0].text: '" + text + "'");
                                }
                            }
                        }
                        
                        // Method 3: Extract from result array (word-by-word)
                        if (text.isEmpty() && json.has("result")) {
                            JSONArray resultArray = json.getJSONArray("result");
                            StringBuilder extractedText = new StringBuilder();
                            
                            Log.d(TAG, String.format("Extracting from %d result items", resultArray.length()));
                            
                            for (int i = 0; i < resultArray.length(); i++) {
                                JSONObject wordObj = resultArray.getJSONObject(i);
                                String word = wordObj.optString("word", "");
                                if (!word.isEmpty()) {
                                    if (extractedText.length() > 0) {
                                        extractedText.append(" ");
                                    }
                                    extractedText.append(word);
                                }
                            }
                            
                            text = extractedText.toString();
                            if (!text.isEmpty()) {
                                Log.d(TAG, "✅ Extracted text from result array: '" + text + "'");
                            }
                        }
                        
                        // Method 4: Log full JSON if all methods failed
                        if (text.isEmpty()) {
                            Log.e(TAG, "❌ FAILED TO EXTRACT TEXT FROM JSON");
                            Log.e(TAG, "Full JSON: " + hypothesis);
                            Log.e(TAG, "JSON keys: " + json.keys().toString());
                        }
                        
                        // Process intermediate results (word by word)
                        if (!text.isEmpty()) {
                            Log.d(TAG, "Processing intermediate result: '" + text + "'");
                            processRecognizedText(text);
                        } else {
                            Log.w(TAG, "⚠️ Empty intermediate result after all extraction attempts");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing result", e);
                        e.printStackTrace();
                    }
                }
                
                @Override
                public void onFinalResult(String hypothesis) {
                    Log.d(TAG, "🔊 onFinalResult called: " + hypothesis);
                    try {
                        JSONObject json = new JSONObject(hypothesis);
                        String text = "";
                        
                        // COMPREHENSIVE JSON EXTRACTION: Try multiple paths to get text
                        
                        // Method 1: Direct "text" field (most common)
                        if (json.has("text")) {
                            text = json.optString("text", "");
                            if (!text.isEmpty()) {
                                Log.d(TAG, "✅ Extracted text from json.text: '" + text + "'");
                            }
                        }
                        
                        // Method 2: Extract from result array (word-by-word)
                        if (text.isEmpty() && json.has("result")) {
                            JSONArray resultArray = json.getJSONArray("result");
                            StringBuilder extractedText = new StringBuilder();
                            
                            Log.d(TAG, String.format("Extracting from %d result items", resultArray.length()));
                            
                            for (int i = 0; i < resultArray.length(); i++) {
                                JSONObject wordObj = resultArray.getJSONObject(i);
                                String word = wordObj.optString("word", "");
                                if (!word.isEmpty()) {
                                    if (extractedText.length() > 0) {
                                        extractedText.append(" ");
                                    }
                                    extractedText.append(word);
                                }
                            }
                            
                            text = extractedText.toString();
                            if (!text.isEmpty()) {
                                Log.d(TAG, "✅ Extracted text from result array: '" + text + "'");
                            }
                        }
                        
                        // Method 3: Log full JSON if all methods failed
                        if (text.isEmpty()) {
                            Log.e(TAG, "❌ FAILED TO EXTRACT TEXT FROM FINAL RESULT");
                            Log.e(TAG, "Full JSON: " + hypothesis);
                            Log.e(TAG, "JSON keys: " + json.keys().toString());
                            
                            // Check if this is a "detected audio but no words" scenario
                            if (json.has("alternatives")) {
                                JSONArray alternatives = json.getJSONArray("alternatives");
                                if (alternatives.length() > 0) {
                                    JSONObject firstAlt = alternatives.getJSONObject(0);
                                    double confidence = firstAlt.optDouble("confidence", 0.0);
                                    String altText = firstAlt.optString("text", "");
                                    
                                    if (confidence > 0 && altText.isEmpty()) {
                                        Log.e(TAG, "⚠️⚠️⚠️ VOSK DETECTED AUDIO BUT RECOGNIZED NO WORDS");
                                        Log.e(TAG, "   Confidence: " + confidence);
                                        Log.e(TAG, "   This usually means:");
                                        Log.e(TAG, "   1. Speaker is too far from microphone");
                                        Log.e(TAG, "   2. Audio quality is too low");
                                        Log.e(TAG, "   3. Too much background noise");
                                        Log.e(TAG, "   4. Speaker not speaking clearly enough");
                                        Log.e(TAG, "   5. Microphone permission issues");
                                        Log.e(TAG, "   ");
                                        Log.e(TAG, "   SOLUTION: Speak louder and closer to the microphone");
                                    }
                                }
                            }
                        }
                        
                        // CONFIDENCE-BASED SAMPLING: Extract word timestamps if available
                        if (json.has("result")) {
                            try {
                                JSONArray wordsArray = json.getJSONArray("result");
                                Log.d(TAG, String.format("📊 Extracting timestamps for %d words", wordsArray.length()));
                                
                                for (int i = 0; i < wordsArray.length(); i++) {
                                    JSONObject wordObj = wordsArray.getJSONObject(i);
                                    
                                    WordTimestamp timestamp = new WordTimestamp();
                                    timestamp.word = wordObj.getString("word");
                                    timestamp.startTime = (float) wordObj.getDouble("start");
                                    timestamp.endTime = (float) wordObj.getDouble("end");
                                    timestamp.confidence = (float) wordObj.optDouble("conf", 1.0);
                                    timestamp.wordIndex = wordTimestamps.size();
                                    
                                    wordTimestamps.add(timestamp);
                                    
                                    if (i < 3 || i >= wordsArray.length() - 3) {
                                        Log.d(TAG, String.format("  Word %d '%s': %.2fs-%.2fs (conf: %.2f)", 
                                            timestamp.wordIndex, timestamp.word, timestamp.startTime, 
                                            timestamp.endTime, timestamp.confidence));
                                    }
                                }
                                
                                Log.d(TAG, String.format("✅ Extracted %d word timestamps", wordTimestamps.size()));
                            } catch (Exception e) {
                                Log.w(TAG, "Could not extract word timestamps: " + e.getMessage());
                            }
                        }
                        
                        // DON'T RESET if words already processed from intermediate results
                        // Only reset and process if this is the first/only result
                        if (maxWordIndexReached == 0 && !text.isEmpty()) {
                            Log.d(TAG, "No intermediate results - processing final text now");
                            currentWordIndex = 0;
                            pronunciationScores.clear();
                            processRecognizedText(text);
                        } else if (maxWordIndexReached > 0) {
                            Log.d(TAG, String.format("Already processed %d words from intermediate results", maxWordIndexReached));
                        } else if (text.isEmpty()) {
                            Log.e(TAG, "❌ No text extracted and no intermediate results - nothing to process!");
                            
                            // Notify user about the issue
                            if (callback != null) {
                                callback.onError("Speech detected but no words recognized. Please speak louder and closer to the microphone.");
                            }
                        }
                        
                        // Calculate final scores
                        calculateFinalScores();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing final result", e);
                        e.printStackTrace();
                    }
                }
                
                @Override
                public void onError(Exception exception) {
                    Log.e(TAG, "Vosk recognition error", exception);
                    if (VoskMFCCRecognizer.this.callback != null) {
                        VoskMFCCRecognizer.this.callback.onError("Recognition error: " + exception.getMessage());
                    }
                    isRecognizing = false;
                }
                
                @Override
                public void onTimeout() {
                    Log.d(TAG, "Recognition timeout");
                    calculateFinalScores();
                }
            });
            
            Log.d(TAG, "✅ Vosk recognition started");
            if (callback != null) {
                callback.onReady();
            }
            
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to start recognition", e);
            e.printStackTrace();
            
            // Provide detailed error message
            String errorMsg = "Failed to create recognizer";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Model")) {
                    errorMsg = "Model files not accessible. Try restarting the app.";
                } else if (e.getMessage().contains("grammar")) {
                    errorMsg = "Invalid grammar format. Please report this issue.";
                } else {
                    errorMsg = "Failed to start: " + e.getMessage();
                }
            }
            
            Log.e(TAG, "Error details: " + errorMsg);
            
            if (callback != null) {
                callback.onError(errorMsg);
            }
            isRecognizing = false;
        } catch (Exception e) {
            Log.e(TAG, "❌ Unexpected error starting recognition", e);
            e.printStackTrace();
            
            if (callback != null) {
                callback.onError("Unexpected error: " + e.getMessage());
            }
            isRecognizing = false;
        }
    }
    
    /**
     * Start recording audio for MFCC analysis
     */
    private void startAudioRecording() {
        try {
            Log.d(TAG, "Starting audio recording for MFCC analysis...");
            
            // Check for RECORD_AUDIO permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "RECORD_AUDIO permission not granted");
                return;
            }
            
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Optimized for speech recognition
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 4
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly");
                return;
            }
            
            audioRecord.startRecording();
            isRecordingAudio = true;
            
            // Start recording thread
            audioRecordingThread = new Thread(() -> {
                short[] buffer = new short[BUFFER_SIZE];
                int frameCount = 0;
                audioRecordingStartTime = System.currentTimeMillis(); // Track start time for timestamps
                
                while (isRecordingAudio) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    
                    if (read > 0) {
                        // Calculate RMS to monitor audio levels (only log occasionally)
                        if (frameCount % 100 == 0) {  // Reduced logging frequency even more
                            float rms = 0;
                            for (int i = 0; i < read; i++) {
                                float sample = buffer[i] / 32768.0f;
                                rms += sample * sample;
                            }
                            rms = (float) Math.sqrt(rms / read);
                            
                            String level = rms < 0.01f ? "SILENT" : 
                                          rms < 0.05f ? "QUIET" :
                                          rms < 0.3f ? "SPEECH" : "LOUD";
                            Log.d(TAG, String.format("Audio level: %.3f (%s)", rms, level));
                        }
                        
                        // Copy buffer for processing
                        short[] audioBuffer = new short[read];
                        System.arraycopy(buffer, 0, audioBuffer, 0, read);
                        
                        // CONFIDENCE-BASED SAMPLING: Write to circular buffer for later extraction
                        circularAudioBuffer.write(audioBuffer, read);
                        
                        // Build noise profile from first few frames only
                        if (frameCount < 10) {
                            audioDenoiser.updateNoiseProfile(audioBuffer);
                        }
                        
                        // Apply denoising to improve speech recognition in noisy environments
                        // Use lightweight denoising to minimize lag while improving accuracy
                        if (frameCount >= 10) { // Apply after noise profile is established
                            audioBuffer = audioDenoiser.applyLightweightDenoising(audioBuffer);
                            
                            // Apply AGC to normalize volume for consistent recognition
                            audioBuffer = audioDenoiser.applyAGC(audioBuffer);
                        }
                        
                        // Store denoised audio for MFCC analysis
                        synchronized (audioBuffers) {
                            // MEMORY OPTIMIZATION: Limit buffer count to prevent memory issues
                            // At 16kHz with BUFFER_SIZE ~4096, each buffer is ~0.25s
                            // 240 buffers = ~60 seconds of audio (Phil-IRI max)
                            if (audioBuffers.size() < 240) {
                                audioBuffers.add(audioBuffer);
                            } else if (frameCount % 100 == 0) {
                                Log.w(TAG, "Audio buffer limit reached, discarding old frames");
                            }
                        }
                        
                        frameCount++;
                    }
                }
                
                Log.d(TAG, "Audio recording thread stopped (processed " + frameCount + " frames)");
            });
            
            audioRecordingThread.setPriority(Thread.NORM_PRIORITY - 1); // Lower priority to reduce lag
            audioRecordingThread.start();
            Log.d(TAG, "✅ Audio recording started for MFCC");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start audio recording", e);
        }
    }
    
    /**
     * Stop recording audio
     */
    private void stopAudioRecording() {
        try {
            isRecordingAudio = false;
            
            if (audioRecordingThread != null) {
                audioRecordingThread.join(1000);
                audioRecordingThread = null;
            }
            
            if (audioRecord != null) {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            }
            
            Log.d(TAG, "✅ Audio recording stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio recording", e);
        }
    }
    
    /**
     * Process recognized text with improved alignment
     * Uses dynamic programming to find best word matches
     */
    private void processRecognizedText(String recognizedText) {
        if (currentWordIndex >= expectedWords.length) {
            return; // All words processed
        }
        
        // Build full recognized text for comprehension analysis
        if (recognizedTextBuilder.length() > 0) {
            recognizedTextBuilder.append(" ");
        }
        recognizedTextBuilder.append(recognizedText);
        
        Log.d(TAG, "Processing recognized text: '" + recognizedText + "'");
        
        String[] recognizedWords = recognizedText.toLowerCase().trim().split("\\s+");
        
        // Try to match each recognized word with remaining expected words
        for (String recognizedWord : recognizedWords) {
            if (currentWordIndex >= expectedWords.length) {
                break;
            }
            
            // REDUCED LOOK-AHEAD: Only check current word and next word (was 3)
            // This prevents scattered highlighting and skipping
            int bestMatchIndex = -1;
            float bestMatchScore = 0.0f;
            int lookAheadWindow = Math.min(2, expectedWords.length - currentWordIndex);
            
            for (int offset = 0; offset < lookAheadWindow; offset++) {
                int checkIndex = currentWordIndex + offset;
                String expectedWord = expectedWords[checkIndex].toLowerCase();
                
                // Calculate match score
                float matchScore = calculateMatchScore(recognizedWord, expectedWord);
                
                if (matchScore > bestMatchScore) {
                    bestMatchScore = matchScore;
                    bestMatchIndex = checkIndex;
                }
            }
            
            // STRICTER THRESHOLDS: Reduce false positives
            float acceptanceThreshold;
            if (bestMatchIndex >= 0) {
                String expectedWord = expectedWords[bestMatchIndex].toLowerCase();
                int wordLength = expectedWord.length();
                
                if (wordLength <= 2) {
                    acceptanceThreshold = 0.90f; // VERY strict for 1-2 letter words (was 0.85)
                } else if (wordLength == 3) {
                    acceptanceThreshold = 0.80f; // Strict for 3 letter words (was 0.75)
                } else if (wordLength <= 5) {
                    acceptanceThreshold = 0.72f; // Moderate for 4-5 letter words (was 0.68)
                } else {
                    acceptanceThreshold = 0.68f; // More lenient for longer words (was 0.65)
                }
            } else {
                acceptanceThreshold = 0.75f; // Default (was 0.70)
            }
            
            // If we found a good match above the threshold
            if (bestMatchIndex >= 0 && bestMatchScore >= acceptanceThreshold) {
                // ONLY skip if it's the very next word (offset = 1)
                // This prevents scattered highlighting
                if (bestMatchIndex == currentWordIndex + 1) {
                    String skippedWord = expectedWords[currentWordIndex];
                    Log.d(TAG, String.format("⏭️  Skipped word %d: '%s' (recognized next word first)", 
                        currentWordIndex, skippedWord));
                    
                    pronunciationScores.add(0.2f); // Very low score for skipped words
                    incorrectWordsCount++;  // Skipped words count as incorrect
                    matchBasedCorrectness.add(false); // Store match-based result
                    
                    if (callback != null) {
                        callback.onWordRecognized("", skippedWord, currentWordIndex, 0.2f, false);
                    }
                    
                    currentWordIndex++;
                } else if (bestMatchIndex > currentWordIndex + 1) {
                    // Don't skip multiple words - treat as unmatched instead
                    Log.d(TAG, String.format("⚠️  Unmatched word: '%s' (would skip too many words)", recognizedWord));
                    continue;
                }
                
                // Process the matched word
                String expectedWord = expectedWords[bestMatchIndex];
                
                // STRICTER SCORING: Reduce false positives
                float pronunciationScore = 0.5f;
                boolean isCorrect = false;
                
                // Text-based scoring with STRICTER thresholds
                if (bestMatchScore >= 0.98f) {
                    // Near-perfect match
                    pronunciationScore = 0.85f + (float)(Math.random() * 0.10f); // 85-95%
                    isCorrect = true;
                } else if (bestMatchScore >= 0.90f) {
                    // Excellent match
                    pronunciationScore = 0.75f + (float)(Math.random() * 0.10f); // 75-85%
                    isCorrect = true;
                } else if (bestMatchScore >= 0.80f) {
                    // Good match
                    pronunciationScore = 0.65f + (float)(Math.random() * 0.10f); // 65-75%
                    isCorrect = true;
                } else if (bestMatchScore >= 0.70f) {
                    // Acceptable match - borderline
                    pronunciationScore = 0.55f + (float)(Math.random() * 0.10f); // 55-65%
                    isCorrect = false; // Mark as incorrect to be safe
                } else {
                    // Weak match - likely mispronounced
                    pronunciationScore = 0.30f + (float)(Math.random() * 0.15f); // 30-45%
                    isCorrect = false;
                }
                
                pronunciationScores.add(pronunciationScore);
                
                // Store match-based correctness for this word (as fallback)
                matchBasedCorrectness.add(isCorrect);
                
                // Store word info for RF analysis
                allRecognizedWords.add(recognizedWord);
                allExpectedWords.add(expectedWord);
                
                // ═══════════════════════════════════════════════════════════════
                // 🎤 PRONUNCIATION LOG - What the user actually said
                // ═══════════════════════════════════════════════════════════════
                Log.d(TAG, "═══════════════════════════════════════════════════════════════");
                Log.d(TAG, String.format("🎤 WORD #%d PRONUNCIATION:", currentWordIndex + 1));
                Log.d(TAG, String.format("   👂 User said:     '%s'", recognizedWord));
                Log.d(TAG, String.format("   📖 Expected word: '%s'", expectedWord));
                Log.d(TAG, String.format("   🎯 Match quality: %.0f%% (%s)", bestMatchScore * 100, 
                    bestMatchScore >= 0.98f ? "perfect" :
                    bestMatchScore >= 0.90f ? "excellent" :
                    bestMatchScore >= 0.80f ? "good" : 
                    bestMatchScore >= 0.70f ? "acceptable" : "weak"));
                Log.d(TAG, String.format("   ✅ Result:        %s (%.0f%% score)", 
                    isCorrect ? "CORRECT ✅" : "INCORRECT ❌", pronunciationScore * 100));
                Log.d(TAG, "═══════════════════════════════════════════════════════════════");
                
                // Track correct/incorrect (will be updated by RF if available)
                if (isCorrect) {
                    correctWordsCount++;
                } else {
                    incorrectWordsCount++;
                }
                
                if (callback != null) {
                    callback.onWordRecognized(recognizedWord, expectedWord, currentWordIndex,
                        pronunciationScore, isCorrect);
                }
                
                currentWordIndex++;
                
                // Track maximum word index reached (for completion check)
                if (currentWordIndex > maxWordIndexReached) {
                    maxWordIndexReached = currentWordIndex;
                }
                
                // AUTO-COMPLETION: Check if all words have been read
                if (currentWordIndex >= expectedWords.length) {
                    Log.d(TAG, "🎉 All words recognized! Auto-completing...");
                    calculateFinalScores();
                    return;
                }
            } else {
                // No good match found - might be insertion or noise
                Log.d(TAG, String.format("⚠️  Unmatched word: '%s' (best match: %.0f%%, threshold: %.0f%%)", 
                    recognizedWord, bestMatchScore * 100, acceptanceThreshold * 100));
            }
        }
    }
    
    /**
     * Calculate match score between recognized and expected word
     * Returns 0.0 (no match) to 1.0 (perfect match)
     * STRICTER: Reduces false positives like "feather" vs "father"
     */
    private float calculateMatchScore(String recognized, String expected) {
        // Clean words
        recognized = recognized.replaceAll("[^a-z\\s]", "").trim();
        expected = expected.replaceAll("[^a-z]", "").trim();
        
        // Exact match
        if (recognized.equals(expected)) {
            return 1.0f;
        }
        
        // Handle multi-word recognition
        String recognizedNoSpaces = recognized.replaceAll("\\s+", "");
        if (recognizedNoSpaces.equals(expected)) {
            return 0.95f;
        }
        
        // Phonetic similarity (STRICT - only for speech recognition errors)
        if (soundsLike(recognizedNoSpaces, expected)) {
            return 0.85f;
        }
        
        // Levenshtein distance-based similarity
        int distance = levenshteinDistance(recognizedNoSpaces, expected);
        int maxLength = Math.max(recognizedNoSpaces.length(), expected.length());
        
        if (maxLength == 0) return 0.0f;
        
        float similarity = 1.0f - (float) distance / maxLength;
        
        // STRICTER: Penalize vowel differences more heavily
        // This helps reject "feather" vs "father", "carry" vs "cherry"
        int vowelDifferences = countVowelDifferences(recognizedNoSpaces, expected);
        if (vowelDifferences > 0) {
            similarity -= (vowelDifferences * 0.15f); // Heavy penalty for vowel mismatches
        }
        
        // Word-length-aware scoring adjustments
        // Small words (1-3 letters) need exact or near-exact matches
        if (expected.length() <= 3) {
            // For small words, penalize mismatches more heavily
            if (distance > 1) {
                similarity *= 0.5f; // HEAVY penalty for small word mismatches (was 0.6)
            }
            // Boost only if first letter matches AND length matches
            if (recognizedNoSpaces.length() > 0 && expected.length() > 0 &&
                recognizedNoSpaces.charAt(0) == expected.charAt(0) &&
                recognizedNoSpaces.length() == expected.length()) {
                similarity += 0.10f; // Reduced boost (was 0.15)
            }
        } else {
            // For longer words, be more lenient but still strict
            // Boost score if first letter matches
            if (recognizedNoSpaces.length() > 0 && expected.length() > 0 &&
                recognizedNoSpaces.charAt(0) == expected.charAt(0)) {
                similarity += 0.08f; // Reduced boost (was 0.10)
            }
            
            // Additional penalty for very different lengths
            int lengthDiff = Math.abs(recognizedNoSpaces.length() - expected.length());
            if (lengthDiff > 2) {
                similarity *= 0.80f; // Stronger penalty (was 0.85)
            } else if (lengthDiff > 1) {
                similarity *= 0.90f; // Penalty for moderate length difference
            }
        }
        
        return Math.min(1.0f, Math.max(0.0f, similarity));
    }
    
    /**
     * Count vowel differences between two words
     * Helps detect mispronunciations like "feather" vs "father"
     */
    private int countVowelDifferences(String word1, String word2) {
        String vowels1 = word1.replaceAll("[^aeiou]", "");
        String vowels2 = word2.replaceAll("[^aeiou]", "");
        
        // If vowel counts are very different, it's a different word
        if (Math.abs(vowels1.length() - vowels2.length()) > 1) {
            return 2; // Heavy penalty
        }
        
        // Count position-based vowel differences
        int differences = 0;
        int minLength = Math.min(vowels1.length(), vowels2.length());
        
        for (int i = 0; i < minLength; i++) {
            if (vowels1.charAt(i) != vowels2.charAt(i)) {
                differences++;
            }
        }
        
        // Add difference for length mismatch
        differences += Math.abs(vowels1.length() - vowels2.length());
        
        return differences;
    }
    
    /**
     * Match recognized word(s) with expected word
     * Handles phonetic similarities and multi-word recognition
     */
    private boolean matchWords(String recognized, String expected) {
        // Clean words
        recognized = recognized.replaceAll("[^a-z\\s]", "").trim();
        expected = expected.replaceAll("[^a-z]", "").trim();
        
        // Exact match
        if (recognized.equals(expected)) {
            return true;
        }
        
        // Handle multi-word recognition (e.g., "my man" for "marian")
        String recognizedNoSpaces = recognized.replaceAll("\\s+", "");
        
        // Check if recognized (without spaces) matches expected
        if (recognizedNoSpaces.equals(expected)) {
            return true;
        }
        
        // Phonetic similarity check
        if (soundsLike(recognizedNoSpaces, expected)) {
            return true;
        }
        
        // Check first letter match + length similarity (helps with misrecognitions)
        // STRICT: 75% threshold - rejects "singing" vs "sinking" (71.4% similarity)
        if (recognizedNoSpaces.length() > 0 && expected.length() > 0) {
            boolean firstLetterMatch = recognizedNoSpaces.charAt(0) == expected.charAt(0);
            int lengthDiff = Math.abs(recognizedNoSpaces.length() - expected.length());
            
            // If first letter matches and length is similar, be more lenient (but still strict)
            if (firstLetterMatch && lengthDiff <= 2) {
                int distance = levenshteinDistance(recognizedNoSpaces, expected);
                int maxLength = Math.max(recognizedNoSpaces.length(), expected.length());
                float similarity = 1.0f - (float) distance / maxLength;
                
                if (similarity >= 0.80f) { // VERY STRICT: 80% for same first letter (increased from 75%)
                    return true;
                }
            }
        }
        
        // Fuzzy match (allow small differences)
        // VERY STRICT: 85% threshold - rejects "singing" vs "sinking" (71.4%), "worked" vs "walked" (66.7%)
        if (recognizedNoSpaces.length() >= 3 && expected.length() >= 3) {
            // Check if one contains the other (only for very short words - 3 letters or less)
            if (recognizedNoSpaces.length() <= 3 && expected.length() <= 3) {
                if (recognizedNoSpaces.contains(expected) || expected.contains(recognizedNoSpaces)) {
                    return true;
                }
            }
            
            // Check similarity with very strict threshold
            int distance = levenshteinDistance(recognizedNoSpaces, expected);
            int maxLength = Math.max(recognizedNoSpaces.length(), expected.length());
            float similarity = 1.0f - (float) distance / maxLength;
            
            // VERY STRICT: 85% similarity threshold - rejects most mispronunciations
            return similarity >= 0.85f;
        }
        
        return false;
    }
    
    /**
     * Check if two words sound similar (phonetic matching)
     * STRICT: Only handles common speech recognition errors, NOT actual mispronunciations
     */
    private boolean soundsLike(String word1, String word2) {
        // Normalize both words
        word1 = word1.toLowerCase();
        word2 = word2.toLowerCase();
        
        // If words are very different in length, they don't sound alike
        int lengthDiff = Math.abs(word1.length() - word2.length());
        if (lengthDiff > 1) {
            return false; // Even 2-letter difference is too much (was 2, now 1)
        }
        
        // Common phonetic substitutions (ONLY for speech recognition errors)
        word1 = normalizePhonetics(word1);
        word2 = normalizePhonetics(word2);
        
        // Check if normalized versions match
        if (word1.equals(word2)) {
            return true;
        }
        
        // Check similarity after normalization
        int distance = levenshteinDistance(word1, word2);
        int maxLength = Math.max(word1.length(), word2.length());
        float similarity = 1.0f - (float) distance / maxLength;
        
        // VERY STRICT: 90% similarity required (was 85%)
        // This rejects "feather" vs "father" (after normalization)
        return similarity >= 0.90f;
    }
    
    /**
     * Normalize phonetic variations
     * CONSERVATIVE: Only normalize clear speech recognition errors, not pronunciation differences
     */
    private String normalizePhonetics(String word) {
        // Common phonetic substitutions in speech recognition ONLY
        word = word.replaceAll("ph", "f");           // phone → fone
        word = word.replaceAll("ck", "k");           // back → bak
        word = word.replaceAll("qu", "kw");          // queen → kween
        word = word.replaceAll("x", "ks");           // box → boks
        
        // DO NOT normalize vowels - "feather" vs "father" should NOT match
        // DO NOT normalize silent letters - these affect pronunciation
        
        return word;
    }
    
    /**
     * Calculate Levenshtein distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Calculate final scores - OPTIMIZED for no lag
     * Strategy: Return immediate results, then update with comprehension asynchronously
     */
    private void calculateFinalScores() {
        // Prevent multiple calls
        if (!isRecognizing) {
            Log.d(TAG, "calculateFinalScores called but not recognizing - ignoring");
            return;
        }
        
        // Stop audio recording
        stopAudioRecording();
        
        // Calculate reading time
        long readingTimeMs = System.currentTimeMillis() - recognitionStartTime;
        float readingTimeSec = readingTimeMs / 1000.0f;
        
        // Calculate accuracy based on correct vs total words read
        float accuracy = 0.0f;
        int totalWordsRead = correctWordsCount + incorrectWordsCount;
        if (totalWordsRead > 0) {
            accuracy = (float) correctWordsCount / totalWordsRead;
        }
        
        // Calculate average pronunciation score from match-based scores
        float avgPronunciation = 0.0f;
        if (!pronunciationScores.isEmpty()) {
            float sum = 0.0f;
            for (float score : pronunciationScores) {
                sum += score;
            }
            avgPronunciation = sum / pronunciationScores.size();
            Log.d(TAG, String.format("📊 Match-based pronunciation: %d scores, average: %.1f%%", 
                pronunciationScores.size(), avgPronunciation * 100));
        } else {
            Log.w(TAG, "⚠️ No pronunciation scores available - using default 50%");
            avgPronunciation = 0.5f; // Default if no scores
        }
        
        // Mark as not recognizing to prevent duplicate calls
        isRecognizing = false;
        
        // OPTIMIZATION: Return immediate results without waiting for comprehension
        final float finalAccuracy = accuracy;
        final float finalPronunciationScore = avgPronunciation;
        final float finalReadingTimeSec = readingTimeSec;
        final int finalTotalWordsRead = totalWordsRead;
        // Use array to allow RF analysis to update these values
        final int[] finalCorrectWords = {correctWordsCount};  // Will be updated by RF analysis
        final int[] finalIncorrectWords = {incorrectWordsCount};  // Will be updated by RF analysis
        
        // Calculate WPM immediately
        float wpm = 0.0f;
        if (finalReadingTimeSec > 0) {
            wpm = (finalTotalWordsRead * 60.0f) / finalReadingTimeSec;
        }
        final float finalWpm = wpm;
        
        // Calculate error rate immediately
        float errorRate = 0.0f;
        if (finalTotalWordsRead > 0) {
            errorRate = (float) finalIncorrectWords[0] / finalTotalWordsRead;
        }
        final float finalErrorRate = errorRate;
        
        // IMMEDIATE CALLBACK: Use estimated comprehension (0.5f) for instant results
        Log.d(TAG, "🚀 Providing immediate results (comprehension will be calculated async)");
        
        // Classify reading level with estimated comprehension
        ReadingLevelClassifier.ReadingLevelResult immediateLevel = null;
        if (levelClassifier != null && levelClassifier.isReady()) {
            immediateLevel = levelClassifier.classifyWithDetails(
                finalAccuracy, 
                finalPronunciationScore, 
                0.5f, // Estimated comprehension for immediate results
                finalWpm, 
                finalErrorRate
            );
        } else {
            immediateLevel = new ReadingLevelClassifier.ReadingLevelResult(
                ReadingLevelClassifier.INSTRUCTIONAL_LEVEL,
                "Instructional Level",
                "Can read with teacher guidance",
                (finalAccuracy * 0.5f) + (finalPronunciationScore * 0.5f),
                "Shows effort",
                "Continue practicing",
                "Keep reading daily"
            );
        }
        
        final ReadingLevelClassifier.ReadingLevelResult immediateReadingLevel = immediateLevel;
        
        // Call callback immediately with estimated comprehension
        if (callback != null) {
            callback.onComplete(finalAccuracy, finalPronunciationScore, 0.5f, immediateReadingLevel);
        }
        
        Log.d(TAG, String.format("✅ Immediate results provided - Accuracy: %.1f%%, Pronunciation: %.1f%%, Estimated Comprehension: 50%%",
            finalAccuracy * 100, finalPronunciationScore * 100));
        
        // NOW: Calculate actual comprehension in background (won't block UI)
        new Thread(() -> {
            try {
                Log.d(TAG, "🔄 Starting async comprehension analysis...");
                long bgStartTime = System.currentTimeMillis();
                
                // Analyze text comprehension using DistilBERT (async, won't block)
                float comprehensionScore = 0.5f; // Default
                if (textAnalyzer != null && textAnalyzer.isReady()) {
                    String recognizedText = recognizedTextBuilder.toString();
                    Log.d(TAG, "Analyzing comprehension in background...");
                    
                    comprehensionScore = textAnalyzer.analyzeComprehension(recognizedText, expectedPassageText);
                    
                    Log.d(TAG, String.format("Comprehension score: %.1f%%", comprehensionScore * 100));
                } else {
                    Log.w(TAG, "DistilBERT not ready, using estimated comprehension");
                }
                
                long bgTime = System.currentTimeMillis() - bgStartTime;
                Log.d(TAG, String.format("✅ Async comprehension complete in %dms", bgTime));
                
                // RANDOM FOREST + MFCC PRONUNCIATION ANALYSIS (async, won't block UI)
                float rfPronunciationScore = finalPronunciationScore; // Start with match-based score
                
                Log.d(TAG, String.format("📊 Pre-RF Analysis - Match-based pronunciation: %.1f%%", finalPronunciationScore * 100));
                
                boolean onnxReady = (onnxRandomForestScorer != null && onnxRandomForestScorer.isReady());
                
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                Log.d(TAG, "📊 AUDIO-ONLY PRONUNCIATION ANALYSIS");
                Log.d(TAG, String.format("  ONNX Scorer: %s", onnxRandomForestScorer != null ? "EXISTS" : "NULL"));
                Log.d(TAG, String.format("  ONNX Ready: %s", onnxReady ? "✅ YES" : "❌ NO"));
                Log.d(TAG, String.format("  Total audio buffers: %d", audioBuffers.size()));
                Log.d(TAG, String.format("  Words recognized: %d", allRecognizedWords.size()));
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                
                if (onnxReady && !audioBuffers.isEmpty() && !allRecognizedWords.isEmpty()) {
                    // Combine all audio buffers into one continuous stream
                    int totalSamples = 0;
                    synchronized (audioBuffers) {
                        for (short[] buffer : audioBuffers) {
                            totalSamples += buffer.length;
                        }
                    }
                    
                    if (totalSamples == 0) {
                        Log.e(TAG, "❌ Total audio samples is 0 - cannot analyze");
                        rfPronunciationScore = finalPronunciationScore;
                    } else {
                        short[] allAudio = new short[totalSamples];
                        int offset = 0;
                        synchronized (audioBuffers) {
                            for (short[] buffer : audioBuffers) {
                                System.arraycopy(buffer, 0, allAudio, offset, buffer.length);
                                offset += buffer.length;
                            }
                        }
                        
                        Log.d(TAG, String.format("✅ Combined audio: %d samples (%.1f seconds)", 
                            totalSamples, totalSamples / 16000.0f));
                        
                        // Use EXPECTED words count (not recognized) to ensure all words get analyzed
                        int wordsCount = expectedWords.length;
                        int samplesPerWord = totalSamples / wordsCount;
                        
                        Log.d(TAG, String.format("📊 Analyzing %d words (expected) with ~%d samples each", 
                            wordsCount, samplesPerWord));
                        Log.d(TAG, "═══════════════════════════════════════════════════════");
                        
                        long rfStartTime = System.currentTimeMillis();
                        List<Boolean> rfWordCorrectness = new ArrayList<>();
                        int rfCorrectCount = 0;
                        int rfIncorrectCount = 0;
                        
                        for (int i = 0; i < wordsCount; i++) {
                            try {
                                String expectedWord = expectedWords[i];
                                
                                // Extract audio segment for this word
                                int startSample = i * samplesPerWord;
                                int endSample = Math.min((i + 1) * samplesPerWord, totalSamples);
                                int segmentLength = endSample - startSample;
                                
                                if (segmentLength <= 0) {
                                    Log.w(TAG, String.format("Word %d: No audio segment - using match-based", i));
                                    boolean fallback = i < matchBasedCorrectness.size() ? matchBasedCorrectness.get(i) : false;
                                    rfWordCorrectness.add(fallback);
                                    if (fallback) rfCorrectCount++; else rfIncorrectCount++;
                                    continue;
                                }
                                
                                short[] wordAudio = new short[segmentLength];
                                System.arraycopy(allAudio, startSample, wordAudio, 0, segmentLength);
                                
                                // Run ONNX Random Forest analysis (if available)
                                boolean isCorrect;
                                float confidence;
                                
                                if (onnxRandomForestScorer != null && onnxRandomForestScorer.isReady()) {
                                    ONNXRandomForestScorer.PronunciationResult result = 
                                        onnxRandomForestScorer.scorePronunciation(wordAudio, expectedWord);
                                    
                                    isCorrect = result.isCorrect();
                                    confidence = result.getConfidence();
                                } else {
                                    // Fallback to match-based correctness if ONNX not available
                                    isCorrect = i < matchBasedCorrectness.size() ? matchBasedCorrectness.get(i) : false;
                                    confidence = 0.7f; // Default confidence for fallback
                                }
                                
                                rfWordCorrectness.add(isCorrect);
                                
                                if (isCorrect) {
                                    rfCorrectCount++;
                                } else {
                                    rfIncorrectCount++;
                                }
                                
                                if (i < 5 || i >= wordsCount - 5) {
                                    Log.d(TAG, String.format("  Word %d '%s': %s (%.0f%% confidence)",
                                        i, expectedWord, isCorrect ? "✅" : "❌", confidence * 100));
                                }
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error analyzing word " + i + ": " + e.getMessage());
                                // Use match-based fallback
                                boolean fallback = i < matchBasedCorrectness.size() ? matchBasedCorrectness.get(i) : false;
                                rfWordCorrectness.add(fallback);
                                if (fallback) rfCorrectCount++; else rfIncorrectCount++;
                            }
                        }
                        
                        long rfTime = System.currentTimeMillis() - rfStartTime;
                        
                        // Calculate RF pronunciation score
                        float rfScore = wordsCount > 0 ? (float) rfCorrectCount / wordsCount : 0.5f;
                        rfPronunciationScore = rfScore;
                        
                        Log.d(TAG, "═══════════════════════════════════════════════════════");
                        Log.d(TAG, "✅ AUDIO-ONLY ANALYSIS COMPLETE!");
                        Log.d(TAG, String.format("  Time taken: %dms (%.0fms per word)", rfTime, (float)rfTime / wordsCount));
                        Log.d(TAG, String.format("  Match-based: %.1f%% (%d/%d correct)", 
                            finalPronunciationScore * 100, finalCorrectWords[0], finalTotalWordsRead));
                        Log.d(TAG, String.format("  Audio-based: %.1f%% (%d/%d correct)", 
                            rfScore * 100, rfCorrectCount, wordsCount));
                        Log.d(TAG, String.format("  Difference: %+.1f%%", (rfScore - finalPronunciationScore) * 100));
                        Log.d(TAG, String.format("  RF results count: %d (expected: %d)", rfWordCorrectness.size(), wordsCount));
                        
                        // HYBRID APPROACH: Prioritize RF model over text matching
                        // RF model analyzes actual audio pronunciation, which is more accurate
                        // Text matching can have false negatives due to Vosk normalization
                        List<Boolean> hybridCorrectness = new ArrayList<>();
                        int hybridCorrectCount = 0;
                        int textOnlyCorrect = 0;
                        int audioOnlyCorrect = 0;
                        int bothCorrect = 0;
                        int overrideCount = 0;
                        
                        for (int i = 0; i < wordsCount; i++) {
                            boolean textCorrect = i < matchBasedCorrectness.size() ? matchBasedCorrectness.get(i) : false;
                            boolean audioCorrect = i < rfWordCorrectness.size() ? rfWordCorrectness.get(i) : false;
                            
                            // TRUST RF MODEL: Use RF result as primary decision
                            // RF analyzes actual pronunciation from audio, which is more reliable
                            boolean hybridCorrect = audioCorrect;
                            
                            // APPLY MISPRONUNCIATION OVERRIDE: Check for hardcoded Filipino mispronunciations
                            // This ensures known mispronunciations (f→p, v→b, th→d) are caught
                            if (i < allRecognizedWords.size() && i < allExpectedWords.size()) {
                                String recognizedWord = allRecognizedWords.get(i);
                                String expectedWord = allExpectedWords.get(i);
                                
                                // Apply MispronunciationOverride to RF result
                                boolean beforeOverride = hybridCorrect;
                                hybridCorrect = MispronunciationOverride.evaluate(recognizedWord, expectedWord, hybridCorrect);
                                
                                if (beforeOverride != hybridCorrect) {
                                    overrideCount++;
                                    Log.d(TAG, String.format("  Override applied to word %d '%s': %s → %s", 
                                        i, expectedWord, beforeOverride ? "CORRECT" : "INCORRECT", 
                                        hybridCorrect ? "CORRECT" : "INCORRECT"));
                                }
                            }
                            
                            hybridCorrectness.add(hybridCorrect);
                            
                            if (hybridCorrect) {
                                hybridCorrectCount++;
                            }
                            if (textCorrect && audioCorrect) bothCorrect++;
                            if (textCorrect && !audioCorrect) textOnlyCorrect++;
                            if (!textCorrect && audioCorrect) audioOnlyCorrect++;
                        }
                        
                        float hybridScore = wordsCount > 0 ? (float) hybridCorrectCount / wordsCount : 0.5f;
                        rfPronunciationScore = hybridScore;
                        
                        // UPDATE: Use RF-based counts for session saving
                        finalCorrectWords[0] = hybridCorrectCount;
                        finalIncorrectWords[0] = wordsCount - hybridCorrectCount;
                        
                        Log.d(TAG, "═══════════════════════════════════════════════════════");
                        Log.d(TAG, "🔀 HYBRID ANALYSIS (RF Model Primary + Mispronunciation Override):");
                        Log.d(TAG, String.format("  Final score: %.1f%% (%d/%d correct)", 
                            hybridScore * 100, hybridCorrectCount, wordsCount));
                        Log.d(TAG, String.format("  Both methods agree correct: %d", bothCorrect));
                        Log.d(TAG, String.format("  Text correct, RF incorrect: %d", textOnlyCorrect));
                        Log.d(TAG, String.format("  Text incorrect, RF correct: %d", audioOnlyCorrect));
                        Log.d(TAG, String.format("  Mispronunciation overrides applied: %d", overrideCount));
                        Log.d(TAG, "  Strategy: Trust RF model + apply Filipino mispronunciation rules");
                        Log.d(TAG, String.format("  📊 Updated session counts: %d correct, %d incorrect", 
                            finalCorrectWords[0], finalIncorrectWords[0]));
                        Log.d(TAG, "═══════════════════════════════════════════════════════");
                        
                        // Notify callback with HYBRID results
                        if (callback != null && hybridCorrectness.size() == wordsCount) {
                            Log.d(TAG, "📤 Sending hybrid results to UI (" + hybridCorrectness.size() + " words)");
                            
                            // Extract confidence scores from wordTimestamps
                            List<Float> confidenceScores = new ArrayList<>();
                            for (int i = 0; i < wordsCount; i++) {
                                float confidence = 1.0f; // Default high confidence
                                if (i < wordTimestamps.size()) {
                                    confidence = wordTimestamps.get(i).confidence;
                                }
                                confidenceScores.add(confidence);
                            }
                            
                            // Call both callbacks for backward compatibility
                            callback.onRFAnalysisComplete(hybridCorrectness);
                            callback.onRFAnalysisCompleteWithConfidence(hybridCorrectness, confidenceScores);
                        } else {
                            Log.e(TAG, String.format("❌ Hybrid results size mismatch: %d vs %d expected", 
                                hybridCorrectness.size(), wordsCount));
                            // Fall back to match-based
                            rfPronunciationScore = finalPronunciationScore;
                        }
                    }
                    
                } else {
                    // Fallback to match-based
                    Log.d(TAG, "⚠️ Audio-only analysis not available - using match-based");
                    if (!onnxReady) Log.w(TAG, "  Reason: ONNX model not ready");
                    if (audioBuffers.isEmpty()) Log.w(TAG, "  Reason: No audio captured");
                    if (allRecognizedWords.isEmpty()) Log.w(TAG, "  Reason: No words recognized");
                    
                    rfPronunciationScore = finalPronunciationScore;
                    
                    // Ensure matchBasedCorrectness has entries for ALL expected words
                    int originalSize = matchBasedCorrectness.size();
                    while (matchBasedCorrectness.size() < expectedWords.length) {
                        matchBasedCorrectness.add(false);
                    }
                    
                    if (matchBasedCorrectness.size() > originalSize) {
                        Log.w(TAG, String.format("⚠️ Filled %d missing words (from %d to %d)", 
                            matchBasedCorrectness.size() - originalSize, originalSize, matchBasedCorrectness.size()));
                    }
                    
                    // Notify callback with match-based fallback (MUST have all 47 words)
                    if (callback != null && matchBasedCorrectness.size() == expectedWords.length) {
                        Log.d(TAG, "📤 Sending match-based fallback to UI (" + matchBasedCorrectness.size() + " words)");
                        
                        // Extract confidence scores from wordTimestamps
                        List<Float> confidenceScores = new ArrayList<>();
                        for (int i = 0; i < expectedWords.length; i++) {
                            float confidence = 1.0f; // Default high confidence
                            if (i < wordTimestamps.size()) {
                                confidence = wordTimestamps.get(i).confidence;
                            }
                            confidenceScores.add(confidence);
                        }
                        
                        // Call both callbacks for backward compatibility
                        callback.onRFAnalysisComplete(matchBasedCorrectness);
                        callback.onRFAnalysisCompleteWithConfidence(matchBasedCorrectness, confidenceScores);
                    } else {
                        Log.e(TAG, String.format("❌ Cannot send fallback - size mismatch: %d vs %d expected", 
                            matchBasedCorrectness.size(), expectedWords.length));
                    }
                }
                
                // Use Random Forest score if available, otherwise use match-based
                final float finalRFPronunciation = rfPronunciationScore;
                
                // Notify callback of updated pronunciation from Random Forest
                if (callback != null) {
                    callback.onPronunciationUpdated(finalRFPronunciation);
                }
                
                // Update reading level with actual comprehension and RF pronunciation
                ReadingLevelClassifier.ReadingLevelResult updatedLevel = null;
                if (levelClassifier != null && levelClassifier.isReady()) {
                    updatedLevel = levelClassifier.classifyWithDetails(
                        finalAccuracy, 
                        finalRFPronunciation,  // Use Random Forest score
                        comprehensionScore, 
                        finalWpm, 
                        finalErrorRate
                    );
                } else {
                    updatedLevel = new ReadingLevelClassifier.ReadingLevelResult(
                        ReadingLevelClassifier.INSTRUCTIONAL_LEVEL,
                        "Instructional Level",
                        "Can read with teacher guidance",
                        (finalAccuracy * 0.5f) + (finalRFPronunciation * 0.5f),
                        "Shows effort",
                        "Continue practicing",
                        "Keep reading daily"
                    );
                }
                
                // Notify callback of updated comprehension (optional)
                final float finalComprehension = comprehensionScore;
                if (callback != null) {
                    callback.onComprehensionUpdated(finalComprehension);
                }
                
                Log.d(TAG, String.format("📊 Final scores - Accuracy: %.1f%%, Pronunciation: %.1f%% (RF), Comprehension: %.1f%%, WPM: %.0f",
                    finalAccuracy * 100, finalRFPronunciation * 100, finalComprehension * 100, finalWpm));
                
                // Check if passage was completed (all words read)
                // Use maxWordIndexReached instead of currentWordIndex because currentWordIndex
                // may have been reset in onFinalResult
                boolean passageCompleted = maxWordIndexReached >= expectedWords.length;
                float completionRate = expectedWords.length > 0 ? 
                    (float) maxWordIndexReached / expectedWords.length : 0.0f;
                
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                Log.d(TAG, "📊 PASSAGE COMPLETION CHECK:");
                Log.d(TAG, String.format("  Current word index: %d", currentWordIndex));
                Log.d(TAG, String.format("  Max word index reached: %d", maxWordIndexReached));
                Log.d(TAG, String.format("  Total words expected: %d", expectedWords.length));
                Log.d(TAG, String.format("  Completion rate: %.1f%%", completionRate * 100));
                Log.d(TAG, String.format("  Passage completed: %s", passageCompleted ? "YES ✅" : "NO ❌"));
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                
                if (!passageCompleted) {
                    Log.w(TAG, "⚠️⚠️⚠️ Passage not completed - session will NOT be saved to database");
                    Log.w(TAG, String.format("⚠️ Only %d out of %d words were recognized", 
                        maxWordIndexReached, expectedWords.length));
                    Log.w(TAG, "⚠️ User may have read all words, but speech recognition didn't detect them all");
                    
                    // Notify user that session was not saved
                    if (callback != null) {
                        callback.onError("Session not saved: Speech recognition detected only " + 
                            maxWordIndexReached + " out of " + expectedWords.length + " words. " +
                            "Please try reading more clearly.");
                    }
                    
                    return; // Don't save incomplete sessions
                }
                
                Log.d(TAG, "✅✅✅ Passage completed - proceeding to save session...");
                Log.d(TAG, "💾 Saving reading session to database with Random Forest pronunciation...");
                
                // Save reading session with actual comprehension and RF pronunciation
                ReadingSession session = new ReadingSession(currentStudentId, currentStudentName, currentPassageTitle, expectedPassageText);
                session.setAccuracy(finalAccuracy);
                session.setPronunciation(finalRFPronunciation);  // Use Random Forest score
                session.setComprehension(finalComprehension);
                session.setWpm(finalWpm);
                session.setCorrectWords(finalCorrectWords[0]);  // Use RF-based count
                session.setTotalWords(finalTotalWordsRead);
                session.setReadingLevel(updatedLevel.level);
                session.setReadingLevelName(updatedLevel.levelName);
                session.setReadingLevelDescription(updatedLevel.description);
                session.setStrengths(updatedLevel.strengths);
                session.setWeaknesses(updatedLevel.weaknesses);
                session.setRecommendations(updatedLevel.recommendations);
                
                Log.d(TAG, String.format("💾 Session data prepared - Pronunciation: %.1f%%, Correct: %d/%d, Level: %s", 
                    finalRFPronunciation * 100, finalCorrectWords[0], finalTotalWordsRead, updatedLevel.levelName));
                
                Log.d(TAG, "💾 Calling ReadingSessionRepository.saveSession()...");
                
                ReadingSessionRepository sessionRepo = new ReadingSessionRepository();
                sessionRepo.saveSession(session, new ReadingSessionRepository.OnSessionSavedListener() {
                    @Override
                    public void onSuccess(ReadingSession savedSession) {
                        Log.d(TAG, "═══════════════════════════════════════════════════════");
                        Log.d(TAG, "✅✅✅ SESSION SAVED SUCCESSFULLY!");
                        Log.d(TAG, String.format("✅ Session ID: %s", savedSession.getId()));
                        Log.d(TAG, String.format("✅ Student: %s", savedSession.getStudentName()));
                        Log.d(TAG, String.format("✅ Passage: %s", savedSession.getPassageTitle()));
                        Log.d(TAG, String.format("✅ Accuracy: %.1f%%", savedSession.getAccuracy() * 100));
                        Log.d(TAG, String.format("✅ Pronunciation: %.1f%%", savedSession.getPronunciation() * 100));
                        Log.d(TAG, String.format("✅ WPM: %.0f", savedSession.getWpm()));
                        Log.d(TAG, String.format("✅ Level: %s", savedSession.getReadingLevelName()));
                        Log.d(TAG, "═══════════════════════════════════════════════════════");
                        
                        // Notify callback with saved session data (for UI to display accurate values)
                        if (callback != null) {
                            callback.onSessionSaved(savedSession);
                        }
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "═══════════════════════════════════════════════════════");
                        Log.e(TAG, "❌❌❌ FAILED TO SAVE SESSION!");
                        Log.e(TAG, "❌ Error: " + error);
                        Log.e(TAG, "═══════════════════════════════════════════════════════");
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in async comprehension calculation", e);
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Stop recognition
     */
    public void stopRecognition() {
        // Calculate final scores if we were recognizing
        if (isRecognizing) {
            Log.d(TAG, "Stopping recognition and calculating final scores...");
            calculateFinalScores();
        }
        
        // Stop audio recording first
        stopAudioRecording();
        
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        isRecognizing = false;
        Log.d(TAG, "Recognition stopped");
    }
    
    /**
     * Check if currently recognizing
     */
    public boolean isRecognizing() {
        return isRecognizing;
    }
    
    /**
     * Re-analyze specific words using stored audio segments from circular buffer
     * This is used for confidence-based sampling to verify suspicious words
     * 
     * @param suspiciousIndices List of word indices to re-analyze
     * @return Map of word index to pronunciation correctness
     */
    public Map<Integer, Boolean> reanalyzeSuspiciousWords(List<Integer> suspiciousIndices) {
        Map<Integer, Boolean> results = new HashMap<>();
        
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        Log.d(TAG, String.format("🔍 CONFIDENCE-BASED SAMPLING: Re-analyzing %d suspicious words", suspiciousIndices.size()));
        
        int successCount = 0;
        int failCount = 0;
        
        for (int wordIndex : suspiciousIndices) {
            if (wordIndex >= wordTimestamps.size()) {
                Log.w(TAG, String.format("  Word index %d out of range (max: %d)", wordIndex, wordTimestamps.size() - 1));
                failCount++;
                continue;
            }
            
            WordTimestamp timestamp = wordTimestamps.get(wordIndex);
            
            // Extract audio segment from circular buffer
            long startSample = timestamp.getStartSample();
            long endSample = timestamp.getEndSample();
            
            short[] audioSegment = circularAudioBuffer.extractSegment(startSample, endSample);
            
            if (audioSegment == null || audioSegment.length == 0) {
                Log.w(TAG, String.format("  Word %d '%s': Could not extract audio segment", wordIndex, timestamp.word));
                failCount++;
                continue;
            }
            
            // Get expected word
            String expectedWord = wordIndex < expectedWords.length ? expectedWords[wordIndex] : timestamp.word;
            
            // Analyze with ONNX Random Forest (if available)
            boolean isCorrect;
            float confidence;
            
            if (onnxRandomForestScorer != null && onnxRandomForestScorer.isReady()) {
                ONNXRandomForestScorer.PronunciationResult result = 
                    onnxRandomForestScorer.scorePronunciation(audioSegment, expectedWord);
                
                isCorrect = result.isCorrect();
                confidence = result.getConfidence();
            } else {
                // Fallback: use confidence threshold (low confidence = likely incorrect)
                isCorrect = timestamp.confidence >= 0.7f;
                confidence = timestamp.confidence;
            }
            
            results.put(wordIndex, isCorrect);
            successCount++;
            
            Log.d(TAG, String.format("  Word %d '%s': %s (%.0f%% confidence, %.2fs-%.2fs)", 
                wordIndex, expectedWord, isCorrect ? "✅ CORRECT" : "❌ INCORRECT", 
                confidence * 100, timestamp.startTime, timestamp.endTime));
        }
        
        Log.d(TAG, String.format("✅ Re-analysis complete: %d succeeded, %d failed", successCount, failCount));
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        
        return results;
    }
    
    /**
     * Release resources
     */
    public void release() {
        stopRecognition();
        stopAudioRecording();
        
        if (onnxRandomForestScorer != null) {
            onnxRandomForestScorer.release();
        }
        
        if (textAnalyzer != null) {
            textAnalyzer.release();
        }
        
        if (levelClassifier != null) {
            levelClassifier.release();
        }
        
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
        
        audioBuffers.clear();
        
        Log.d(TAG, "Resources released");
    }
    
    /**
     * Model initialization callback
     */
    public interface ModelInitCallback {
        void onModelReady();
        void onModelError(String error);
    }
}
