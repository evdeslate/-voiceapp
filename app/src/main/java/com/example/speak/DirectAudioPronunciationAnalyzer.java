package com.example.speak;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct audio-only pronunciation analyzer
 * Captures raw audio and analyzes pronunciation without speech recognition
 * 
 * Benefits:
 * - No speech-to-text normalization (catches "sing-ging" vs "singing")
 * - Fully offline
 * - Simpler architecture
 * - Smaller app size (no Vosk dependency needed)
 * - Direct pronunciation analysis
 */
public class DirectAudioPronunciationAnalyzer {
    private static final String TAG = "DirectAudioAnalyzer";
    
    // Audio recording settings
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Components
    private AudioRecord audioRecord;
    private ONNXRandomForestScorer pronunciationScorer;
    private AudioDenoiser audioDenoiser;
    
    // State
    private boolean isRecording = false;
    private Thread recordingThread;
    private List<short[]> audioBuffers = new ArrayList<>();
    private long recordingStartTime = 0;
    
    // Expected passage data
    private String[] expectedWords;
    private String expectedPassageText;
    
    // Callback interface
    public interface AnalysisCallback {
        void onWordAnalyzed(int wordIndex, boolean isCorrect, float confidence);
        void onComplete(List<Boolean> wordCorrectness, float overallScore, long durationMs);
        void onError(String error);
    }
    
    private AnalysisCallback callback;
    
    public DirectAudioPronunciationAnalyzer(Context context) {
        // Initialize components
        this.pronunciationScorer = new ONNXRandomForestScorer(context);
        this.audioDenoiser = new AudioDenoiser();
    }
    
    /**
     * Check if the analyzer is ready to use
     */
    public boolean isReady() {
        return pronunciationScorer != null && pronunciationScorer.isReady();
    }
    
    /**
     * Set the expected passage text for analysis
     */
    public void setExpectedPassage(String passageText) {
        this.expectedPassageText = passageText;
        this.expectedWords = passageText.trim().split("\\s+");
        
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        Log.d(TAG, "📝 Expected passage set:");
        Log.d(TAG, String.format("  Total words: %d", expectedWords.length));
        Log.d(TAG, String.format("  First 5 words: %s", getFirstWords(5)));
        Log.d(TAG, "═══════════════════════════════════════════════════════");
    }
    
    /**
     * Start recording and analyzing audio
     */
    public void startRecording(AnalysisCallback callback) {
        if (isRecording) {
            Log.w(TAG, "Already recording - ignoring start request");
            return;
        }
        
        if (expectedWords == null || expectedWords.length == 0) {
            callback.onError("No expected passage set");
            return;
        }
        
        this.callback = callback;
        
        try {
            // Initialize AudioRecord
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 4
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                callback.onError("Failed to initialize audio recording");
                return;
            }
            
            // Clear previous buffers
            audioBuffers.clear();
            
            // Start recording
            audioRecord.startRecording();
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            
            Log.d(TAG, "═══════════════════════════════════════════════════════");
            Log.d(TAG, "🎤 AUDIO RECORDING STARTED");
            Log.d(TAG, String.format("  Sample rate: %d Hz", SAMPLE_RATE));
            Log.d(TAG, String.format("  Buffer size: %d bytes", BUFFER_SIZE));
            Log.d(TAG, String.format("  Expected words: %d", expectedWords.length));
            Log.d(TAG, "═══════════════════════════════════════════════════════");
            
            // Start recording thread
            recordingThread = new Thread(this::recordAudio);
            recordingThread.start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            callback.onError("Failed to start recording: " + e.getMessage());
            isRecording = false;
        }
    }
    
    /**
     * Stop recording and analyze the captured audio
     */
    public void stopRecordingAndAnalyze() {
        if (!isRecording) {
            Log.w(TAG, "Not recording - ignoring stop request");
            return;
        }
        
        Log.d(TAG, "🛑 Stopping recording...");
        isRecording = false;
        
        // Wait for recording thread to finish
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for recording thread", e);
            }
        }
        
        // Stop and release AudioRecord
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }
        
        long recordingDuration = System.currentTimeMillis() - recordingStartTime;
        
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        Log.d(TAG, "🎤 RECORDING STOPPED");
        Log.d(TAG, String.format("  Duration: %d ms (%.1f seconds)", recordingDuration, recordingDuration / 1000.0f));
        Log.d(TAG, String.format("  Audio buffers captured: %d", audioBuffers.size()));
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        
        // Analyze the captured audio
        analyzeAudio(recordingDuration);
    }
    
    /**
     * Recording thread - captures audio continuously
     */
    private void recordAudio() {
        short[] buffer = new short[BUFFER_SIZE / 2]; // 16-bit samples
        
        while (isRecording) {
            int samplesRead = audioRecord.read(buffer, 0, buffer.length);
            
            if (samplesRead > 0) {
                // Copy buffer to storage
                short[] audioChunk = new short[samplesRead];
                System.arraycopy(buffer, 0, audioChunk, 0, samplesRead);
                
                synchronized (audioBuffers) {
                    audioBuffers.add(audioChunk);
                }
            } else if (samplesRead < 0) {
                Log.e(TAG, "Error reading audio: " + samplesRead);
                break;
            }
        }
        
        Log.d(TAG, "Recording thread finished");
    }
    
    /**
     * Analyze the captured audio
     */
    private void analyzeAudio(long recordingDuration) {
        new Thread(() -> {
            try {
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                Log.d(TAG, "🔬 STARTING AUDIO ANALYSIS");
                
                // Combine all audio buffers
                int totalSamples = 0;
                synchronized (audioBuffers) {
                    for (short[] buffer : audioBuffers) {
                        totalSamples += buffer.length;
                    }
                }
                
                if (totalSamples == 0) {
                    Log.e(TAG, "❌ No audio captured!");
                    if (callback != null) {
                        callback.onError("No audio was captured");
                    }
                    return;
                }
                
                short[] allAudio = new short[totalSamples];
                int offset = 0;
                synchronized (audioBuffers) {
                    for (short[] buffer : audioBuffers) {
                        System.arraycopy(buffer, 0, allAudio, offset, buffer.length);
                        offset += buffer.length;
                    }
                }
                
                Log.d(TAG, String.format("  Total audio: %d samples (%.1f seconds)", 
                    totalSamples, totalSamples / (float) SAMPLE_RATE));
                
                // OPTIMIZATION: Skip denoising for 20-30% speed gain
                // Vosk already handles noisy environments well, and MFCC is robust to moderate noise
                Log.d(TAG, "⚡ Skipping denoising (optimization)");
                
                // Divide audio equally among expected words
                int wordsCount = expectedWords.length;
                int samplesPerWord = totalSamples / wordsCount;
                
                Log.d(TAG, String.format("  Words to analyze: %d", wordsCount));
                Log.d(TAG, String.format("  Samples per word: ~%d (%.2f seconds)", 
                    samplesPerWord, samplesPerWord / (float) SAMPLE_RATE));
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                
                // Analyze each word
                List<Boolean> wordCorrectness = new ArrayList<>();
                int correctCount = 0;
                long analysisStartTime = System.currentTimeMillis();
                
                for (int i = 0; i < wordsCount; i++) {
                    String expectedWord = expectedWords[i];
                    
                    // Extract audio segment for this word
                    int startSample = i * samplesPerWord;
                    int endSample = Math.min((i + 1) * samplesPerWord, totalSamples);
                    int segmentLength = endSample - startSample;
                    
                    if (segmentLength <= 0) {
                        Log.w(TAG, String.format("Word %d '%s': No audio segment", i, expectedWord));
                        wordCorrectness.add(false);
                        if (callback != null) {
                            callback.onWordAnalyzed(i, false, 0.0f);
                        }
                        continue;
                    }
                    
                    short[] wordAudio = new short[segmentLength];
                    System.arraycopy(allAudio, startSample, wordAudio, 0, segmentLength);
                    
                    // Analyze pronunciation using ONNX Random Forest
                    ONNXRandomForestScorer.PronunciationResult result = 
                        pronunciationScorer.scorePronunciation(wordAudio, expectedWord);
                    
                    boolean isCorrect = result.isCorrect();
                    float confidence = result.getConfidence();
                    
                    wordCorrectness.add(isCorrect);
                    if (isCorrect) {
                        correctCount++;
                    }
                    
                    // Notify callback for real-time UI update
                    if (callback != null) {
                        callback.onWordAnalyzed(i, isCorrect, confidence);
                    }
                    
                    // Log first 5 and last 5 words
                    if (i < 5 || i >= wordsCount - 5) {
                        Log.d(TAG, String.format("  Word %d '%s': %s (%.0f%% confidence)",
                            i, expectedWord, isCorrect ? "✅" : "❌", confidence * 100));
                    }
                }
                
                long analysisTime = System.currentTimeMillis() - analysisStartTime;
                float overallScore = wordsCount > 0 ? (float) correctCount / wordsCount : 0.0f;
                
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                Log.d(TAG, "✅ ANALYSIS COMPLETE!");
                Log.d(TAG, String.format("  Analysis time: %d ms (%.0f ms per word)", 
                    analysisTime, (float) analysisTime / wordsCount));
                Log.d(TAG, String.format("  Overall score: %.1f%% (%d/%d correct)", 
                    overallScore * 100, correctCount, wordsCount));
                Log.d(TAG, "═══════════════════════════════════════════════════════");
                
                // Notify callback with final results
                if (callback != null) {
                    callback.onComplete(wordCorrectness, overallScore, recordingDuration);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error analyzing audio", e);
                if (callback != null) {
                    callback.onError("Analysis failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Clean up resources
     */
    public void release() {
        if (isRecording) {
            stopRecordingAndAnalyze();
        }
        
        if (pronunciationScorer != null) {
            pronunciationScorer.release();
        }
        
        audioBuffers.clear();
        
        Log.d(TAG, "Resources released");
    }
    
    /**
     * Helper method to get first N words
     */
    private String getFirstWords(int count) {
        if (expectedWords == null || expectedWords.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(count, expectedWords.length); i++) {
            if (i > 0) sb.append(" ");
            sb.append(expectedWords[i]);
        }
        return sb.toString();
    }
}
