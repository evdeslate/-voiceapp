package com.example.speak;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * MFCC-based Pronunciation Recognizer
 * 
 * Pipeline:
 * 1. Record audio from microphone
 * 2. Extract MFCC features using MFCCExtractor
 * 3. Run through ONNX Random Forest model
 * 4. Get pronunciation predictions (correct/incorrect)
 * 
 * NO SPEECH-TO-TEXT - User must read the expected words in order
 */
public class MFCCPronunciationRecognizer {
    private static final String TAG = "MFCCPronRecognizer";
    
    // Audio parameters
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Word timing parameters
    private static final long WORD_TIMEOUT_MS = 3000; // 3 seconds per word
    private static final long SILENCE_THRESHOLD_MS = 500; // 0.5 seconds of silence = word boundary
    private static final float SILENCE_AMPLITUDE_THRESHOLD = 0.08f; // RMS threshold (balanced)
    private static final int MIN_SPEECH_SAMPLES = 3200; // Minimum samples for valid speech (~0.2s at 16kHz)
    
    private Context context;
    private ONNXRandomForestScorer onnxScorer;
    private AudioDenoiser audioDenoiser;
    private AudioPreProcessor audioPreProcessor;
    private ReadingLevelClassifier levelClassifier;
    private DistilBERTTextAnalyzer textAnalyzer;
    
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;
    
    private String[] expectedWords;
    private int currentWordIndex = 0;
    private List<Float> pronunciationScores;
    private List<Boolean> wordCorrectness;
    private long recognitionStartTime = 0;
    
    private RecognitionCallback callback;
    
    /**
     * Callback interface for recognition events
     */
    public interface RecognitionCallback {
        void onReady();
        void onWordDetected(int wordIndex, String expectedWord);
        void onWordScored(int wordIndex, String expectedWord, float score, boolean isCorrect);
        void onComplete(float overallAccuracy, float averagePronunciation, float comprehensionScore, ReadingLevelClassifier.ReadingLevelResult readingLevel);
        void onError(String error);
    }
    
    public MFCCPronunciationRecognizer(Context context) {
        this.context = context;
        // Use TarsosDSP for MFCC extraction (more reliable)
        // mfccExtractor not needed here - ONNXRandomForestScorer handles it
        this.onnxScorer = new ONNXRandomForestScorer(context);
        this.audioDenoiser = new AudioDenoiser();
        this.audioPreProcessor = new AudioPreProcessor(SAMPLE_RATE);
        this.levelClassifier = new ReadingLevelClassifier(context);
        this.textAnalyzer = new DistilBERTTextAnalyzer(context);
        this.pronunciationScores = new ArrayList<>();
        this.wordCorrectness = new ArrayList<>();
        
        Log.d(TAG, "MFCC Pronunciation Recognizer initialized (using TarsosDSP)");
    }
    
    /**
     * Start recognition with expected words
     */
    public void startRecognition(String[] expectedWords, String passageText, 
                                 String studentId, String studentName, String passageTitle,
                                 RecognitionCallback callback) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }
        
        this.expectedWords = expectedWords;
        this.callback = callback;
        this.currentWordIndex = 0;
        this.pronunciationScores.clear();
        this.wordCorrectness.clear();
        this.recognitionStartTime = System.currentTimeMillis();
        
        // Check audio permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError("Microphone permission not granted");
            return;
        }
        
        // Reset audio processors
        audioDenoiser.reset();
        audioPreProcessor.reset();
        
        // Start recording
        startRecording();
        
        if (callback != null) {
            callback.onReady();
        }
    }
    
    /**
     * Start audio recording and word detection
     */
    private void startRecording() {
        try {
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 4
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                if (callback != null) {
                    callback.onError("Failed to initialize audio recording");
                }
                return;
            }
            
            audioRecord.startRecording();
            isRecording = true;
            
            recordingThread = new Thread(() -> {
                processAudioStream();
            });
            recordingThread.start();
            
            Log.d(TAG, "Audio recording started");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            if (callback != null) {
                callback.onError("Failed to start recording: " + e.getMessage());
            }
        }
    }
    
    /**
     * Process audio stream and detect words
     */
    private void processAudioStream() {
        short[] buffer = new short[BUFFER_SIZE];
        List<Short> currentWordAudio = new ArrayList<>();
        long lastSoundTime = System.currentTimeMillis();
        long wordStartTime = System.currentTimeMillis();
        boolean inWord = false;
        
        while (isRecording && currentWordIndex < expectedWords.length) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            
            if (read > 0) {
                // Calculate RMS (audio level)
                float rms = calculateRMS(buffer, read);
                
                // Detect speech vs silence
                boolean isSpeech = rms > SILENCE_AMPLITUDE_THRESHOLD;
                long currentTime = System.currentTimeMillis();
                
                if (isSpeech) {
                    // Speech detected
                    if (!inWord) {
                        // Start of new word
                        inWord = true;
                        wordStartTime = currentTime;
                        currentWordAudio.clear();
                        
                        if (callback != null && currentWordIndex < expectedWords.length) {
                            String expectedWord = expectedWords[currentWordIndex];
                            callback.onWordDetected(currentWordIndex, expectedWord);
                        }
                    }
                    
                    // Add audio to current word buffer
                    for (int i = 0; i < read; i++) {
                        currentWordAudio.add(buffer[i]);
                    }
                    
                    lastSoundTime = currentTime;
                    
                } else if (inWord) {
                    // Silence detected while in word
                    long silenceDuration = currentTime - lastSoundTime;
                    
                    if (silenceDuration >= SILENCE_THRESHOLD_MS) {
                        // End of word detected
                        processWord(currentWordAudio);
                        inWord = false;
                        currentWordAudio.clear();
                    } else {
                        // Still in word, add silence
                        for (int i = 0; i < read; i++) {
                            currentWordAudio.add(buffer[i]);
                        }
                    }
                }
                
                // Check for word timeout
                if (inWord && (currentTime - wordStartTime) > WORD_TIMEOUT_MS) {
                    Log.w(TAG, "Word timeout - processing anyway");
                    processWord(currentWordAudio);
                    inWord = false;
                    currentWordAudio.clear();
                }
            }
        }
        
        // Process any remaining audio
        if (inWord && !currentWordAudio.isEmpty()) {
            processWord(currentWordAudio);
        }
        
        // Calculate final scores
        calculateFinalScores();
    }
    
    /**
     * Process a single word's audio
     */
    private void processWord(List<Short> audioData) {
        if (currentWordIndex >= expectedWords.length) {
            return;
        }
        
        String expectedWord = expectedWords[currentWordIndex];
        
        // Convert List<Short> to short[]
        short[] audioArray = new short[audioData.size()];
        for (int i = 0; i < audioData.size(); i++) {
            audioArray[i] = audioData.get(i);
        }
        
        // Check if audio is too short (likely just noise)
        if (audioArray.length < MIN_SPEECH_SAMPLES) {
            Log.w(TAG, String.format("⚠️  Audio too short for '%s': %d samples (min %d) - skipping", 
                expectedWord, audioArray.length, MIN_SPEECH_SAMPLES));
            return;
        }
        
        // Log raw audio statistics
        logAudioStatistics(audioArray, expectedWord);
        
        // Check if audio is mostly silence (reject background noise)
        float rms = calculateRMS(audioArray, audioArray.length);
        if (rms < SILENCE_AMPLITUDE_THRESHOLD) {
            Log.w(TAG, String.format("⚠️  Audio too quiet for '%s': RMS=%.3f (threshold=%.3f) - likely silence/noise", 
                expectedWord, rms, SILENCE_AMPLITUDE_THRESHOLD));
            return;
        }
        
        // Apply audio preprocessing
        audioArray = audioDenoiser.applyLightweightDenoising(audioArray);
        audioArray = audioDenoiser.applyAGC(audioArray);
        
        // Apply RMS normalization (CRITICAL for matching training data)
        // Training data was RMS-normalized, so production audio must be too
        audioArray = audioPreProcessor.rmsNormalize(audioArray);
        
        // Log processed audio statistics
        Log.d(TAG, "After preprocessing + RMS normalization:");
        logAudioStatistics(audioArray, expectedWord);
        
        // Score pronunciation using ONNX Random Forest
        ONNXRandomForestScorer.PronunciationResult result = 
            onnxScorer.scorePronunciation(audioArray, expectedWord);
        
        float score = result.getScore();
        boolean isCorrect = result.isCorrect();
        
        pronunciationScores.add(score);
        wordCorrectness.add(isCorrect);
        
        Log.d(TAG, String.format("Word %d '%s': %.0f%% (%s)", 
            currentWordIndex, expectedWord, score * 100, isCorrect ? "✓" : "✗"));
        
        if (callback != null) {
            callback.onWordScored(currentWordIndex, expectedWord, score, isCorrect);
        }
        
        currentWordIndex++;
    }
    
    /**
     * Log audio statistics for debugging
     */
    private void logAudioStatistics(short[] audio, String word) {
        if (audio == null || audio.length == 0) {
            Log.w(TAG, "Empty audio for word: " + word);
            return;
        }
        
        // Calculate statistics
        long sum = 0;
        short min = Short.MAX_VALUE;
        short max = Short.MIN_VALUE;
        int silentSamples = 0;
        int loudSamples = 0;
        
        for (short sample : audio) {
            sum += Math.abs(sample);
            min = (short) Math.min(min, sample);
            max = (short) Math.max(max, sample);
            
            if (Math.abs(sample) < 500) silentSamples++;
            if (Math.abs(sample) > 10000) loudSamples++;
        }
        
        float avgAmplitude = (float) sum / audio.length;
        float duration = audio.length / 16000.0f; // 16kHz sample rate
        float silentPercent = (silentSamples * 100.0f) / audio.length;
        float loudPercent = (loudSamples * 100.0f) / audio.length;
        
        // Calculate RMS (Root Mean Square) for energy
        long sumSquares = 0;
        for (short sample : audio) {
            sumSquares += (long) sample * sample;
        }
        float rms = (float) Math.sqrt((double) sumSquares / audio.length);
        
        Log.d(TAG, String.format("🎤 Audio for '%s':", word));
        Log.d(TAG, String.format("   Samples: %d (%.2fs)", audio.length, duration));
        Log.d(TAG, String.format("   Range: [%d, %d]", min, max));
        Log.d(TAG, String.format("   Avg amplitude: %.1f", avgAmplitude));
        Log.d(TAG, String.format("   RMS energy: %.1f", rms));
        Log.d(TAG, String.format("   Silent: %.1f%%, Loud: %.1f%%", silentPercent, loudPercent));
        
        // Detect potential issues
        if (avgAmplitude < 100) {
            Log.w(TAG, "   ⚠️  Very quiet audio - might be silence or too soft");
        }
        if (silentPercent > 80) {
            Log.w(TAG, "   ⚠️  Mostly silent - user might not have spoken");
        }
        if (max < 1000) {
            Log.w(TAG, "   ⚠️  Low maximum amplitude - check microphone");
        }
        if (rms < 500) {
            Log.w(TAG, "   ⚠️  Low energy - audio might be too quiet");
        }
    }
    
    /**
     * Calculate RMS (Root Mean Square) for audio level detection
     */
    private float calculateRMS(short[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        double mean = (double) sum / length;
        return (float) Math.sqrt(mean) / 32768.0f; // Normalize to 0-1
    }
    
    /**
     * Calculate final scores and complete recognition
     */
    private void calculateFinalScores() {
        if (pronunciationScores.isEmpty()) {
            if (callback != null) {
                callback.onError("No words were recognized");
            }
            return;
        }
        
        // Calculate overall accuracy
        int correctCount = 0;
        for (boolean correct : wordCorrectness) {
            if (correct) correctCount++;
        }
        float overallAccuracy = (float) correctCount / wordCorrectness.size();
        
        // Calculate average pronunciation
        float sumPronunciation = 0;
        for (float score : pronunciationScores) {
            sumPronunciation += score;
        }
        float averagePronunciation = sumPronunciation / pronunciationScores.size();
        
        // Calculate reading time and WPM
        long readingTimeMs = System.currentTimeMillis() - recognitionStartTime;
        float readingTimeMin = readingTimeMs / 60000.0f;
        float wpm = currentWordIndex / readingTimeMin;
        
        // Calculate error rate
        float errorRate = 1.0f - overallAccuracy;
        
        // Get reading level classification with details
        ReadingLevelClassifier.ReadingLevelResult readingLevel = 
            levelClassifier.classifyWithDetails(
                overallAccuracy, averagePronunciation, 0.0f, wpm, errorRate);
        
        // Comprehension score (placeholder - would need actual comprehension questions)
        float comprehensionScore = 0.0f;
        
        Log.d(TAG, String.format("Recognition complete: %.0f%% accuracy, %.0f%% pronunciation",
            overallAccuracy * 100, averagePronunciation * 100));
        
        if (callback != null) {
            callback.onComplete(overallAccuracy, averagePronunciation, comprehensionScore, readingLevel);
        }
    }
    
    /**
     * Stop recognition
     */
    public void stopRecognition() {
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio record", e);
            }
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error joining recording thread", e);
            }
        }
        
        Log.d(TAG, "Recognition stopped");
    }
    
    /**
     * Release resources
     */
    public void release() {
        stopRecognition();
        
        if (onnxScorer != null) {
            onnxScorer.release();
        }
    }
}
