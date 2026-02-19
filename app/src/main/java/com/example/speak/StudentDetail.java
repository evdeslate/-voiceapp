package com.example.speak;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StudentDetail extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;

    // UI Components
    private TextView studentNameText;
    private TextView gradeText;
    private ImageView studentProfileImage;

    private ImageView speakLogo;
    
    // Select Passage Section
    private ConstraintLayout selectPassageLayout;
    private TextView selectPassageText;
    private ImageView dropdownIcon;
    
    // Progress Section
    private ConstraintLayout progressCircle;
    private TextView progressPercentage;
    private TextView fastReaderText;
    private TextView currentProgressText;
    
    // Reading Activity Section
    private ImageButton readingCharacterButton;
    private TextView clickToReadText;
    
    // Student Data
    private String studentId;  // Changed to String for Firebase compatibility
    private String studentName;
    private String studentGrade;
    private int studentProgress;
    private int studentAvatar;
    private String teacherName;
    
    // Speech Recognition - Using Vosk + MFCC (offline, combined system)
    private VoskMFCCRecognizer voskRecognizer; // Vosk for word detection + MFCC for pronunciation
    
    // MFCC Pronunciation Scoring (integrated with Vosk)
    private MFCCExtractor mfccExtractor;
    private MFCCPronunciationScorer mfccScorer;
    private float lastMFCCScore = 0.0f;
    
    // Vosk model initialization state
    private boolean isVoskModelReady = false;
    
    // Reading session tracking
    private int currentTotalWords = 0;
    private int currentWordsRead = 0;
    private float currentAccuracy = 0.0f;
    private int currentCorrectWords = 0;
    private int currentIncorrectWords = 0;
    
    private String currentPassageText = "";
    private List<WordSpan> wordSpans = new ArrayList<>(); // Precomputed word positions
    
    // Real-time speech-driven highlighting state
    private boolean[] wordCorrect;      // Correctness per word (true = correct, false = incorrect)
    private boolean[] wordScored;       // Whether word has been scored by onWordRecognized
    private boolean[] wordFinished;     // Finished spoken flags (true = word has been spoken)
    private String lastPartial = "";    // Last partial result for comparison
    private int lastFinishedIndex = -1; // Last word marked as finished
    private int currentWordIndex = -1; // Current word being processed
    
    private boolean isCurrentlyRecording = false;
    private boolean useWordTracking = true; // Enable word-by-word highlighting with automatic speech recognition
    private boolean useEnhancedMode = true; // Use enhanced recognition system
    private boolean useRealTimeMode = true; // Use real-time optimized tracking
    private boolean useContinuousFlow = true; // Always use continuous reading flow mode for fluency
    private boolean useModelEnhanced = true; // NEW: Use AI model for intelligent word matching
    private boolean useContinuousReading = true; // NEW: Use continuous flow reading (no word-by-word stopping)
    private boolean autoStartReading = true; // NEW: Automatically start reading when passage opens
    private boolean isProcessingWord = false; // NEW: Flag to prevent highlighting during word processing

    // Phil-IRI Compliant Timer - 60 second maximum
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private TextView currentTimerDisplay;
    private long timerStartTime = 0;
    private boolean timerRunning = false;
    private static final long PHIL_IRI_MAX_TIME_MS = 60000; // 60 seconds maximum
    private boolean philIriTimeExpired = false;
    
    // Store final reading results for accurate display
    private float finalReadingAccuracy = 0.0f;
    private float finalPronunciation = 0.0f;
    private String finalReadingLevelName = "";
    private boolean hasFinalResults = false;
    private long finalReadingTimeMs = 0; // Store actual reading time from speech recognition
    private int finalWordsRead = 0; // Store actual words read
    private float finalWpm = 0.0f; // Store WPM from database (don't recalculate)
    
    // Store results modal reference to update it when session is saved
    private Dialog currentResultsModal = null;
    
    // Toast management to prevent spam
    private Toast currentToast;
    private long lastToastTime = 0;
    private static final long TOAST_INTERVAL = 1500; // 1.5 seconds between toasts
    
    // ── NEW: Robust word detection components ───────────────────────────────── 
    private PhoneticMatcher phoneticMatcher;
    private WordTimeoutWatchdog wordWatchdog;
    
    // Index of the word we are currently WAITING for Vosk to confirm
    private int awaitingWordIndex = 0;
    
    // Words that timed out (mumbled/skipped) — marked incorrect automatically
    private final java.util.Set<Integer> timedOutWords = new java.util.HashSet<>();

    /**
     * Show toast with rate limiting to prevent spam
     */
    private void showManagedToast(String message) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToastTime > TOAST_INTERVAL) {
            if (currentToast != null) {
                currentToast.cancel();
            }
            currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            currentToast.show();
            lastToastTime = currentTime;
        }
    }
    
    /**
     * Show important toast immediately (for critical messages)
     */
    private void showImportantToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
        lastToastTime = System.currentTimeMillis();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_detail);

        // Get student data from intent
        getStudentData();
        
        // Initialize views
        initializeViews();
        
        // Setup click listeners
        setupClickListeners();
        
        // Update UI with student data
        updateUI();
        
        // Initialize speech recognition
        initializeSpeechRecognition();
        
        // Check and request audio permission
        checkAudioPermission();
    }

    private void initializeSpeechRecognition() {
        try {
            android.util.Log.d("StudentDetail", "=== INITIALIZING VOSK + MFCC SYSTEM ===");
            
            // Initialize MFCC components
            try {
                mfccExtractor = new MFCCExtractor(16000);
                mfccScorer = new MFCCPronunciationScorer(this);
                if (mfccScorer.isReady()) {
                    android.util.Log.d("StudentDetail", "✅ MFCC scorer initialized and ready");
                } else {
                    android.util.Log.w("StudentDetail", "⚠️ MFCC scorer initialized but model not ready");
                }
            } catch (Exception e) {
                android.util.Log.e("StudentDetail", "❌ Failed to initialize MFCC scorer: " + e.getMessage(), e);
                mfccScorer = null;
            }
            
            // Initialize Vosk recognizer
            try {
                voskRecognizer = new VoskMFCCRecognizer(this);
                android.util.Log.d("StudentDetail", "✅ VoskMFCCRecognizer created");
                
                // Initialize Vosk model (async)
                voskRecognizer.initializeModel(new VoskMFCCRecognizer.ModelInitCallback() {
                    @Override
                    public void onModelReady() {
                        isVoskModelReady = true;
                        android.util.Log.d("StudentDetail", "✅ Vosk model loaded and ready");
                        runOnUiThread(() -> {
                            Toast.makeText(StudentDetail.this, "✅ Speech recognition ready", Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    @Override
                    public void onModelError(String error) {
                        isVoskModelReady = false;
                        android.util.Log.e("StudentDetail", "❌ Vosk model error: " + error);
                        runOnUiThread(() -> {
                            Toast.makeText(StudentDetail.this, "❌ Speech model error: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
                
            } catch (Exception e) {
                android.util.Log.e("StudentDetail", "❌ Failed to initialize VoskMFCCRecognizer: " + e.getMessage(), e);
                voskRecognizer = null;
            }
            
            // Using Vosk + MFCC for speech recognition
            android.util.Log.d("StudentDetail", "ℹ️ Using Vosk + MFCC as the primary speech system");
            
            // Test basic speech recognition availability
            try {
                if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
                    android.util.Log.d("StudentDetail", "✅ Android Speech Recognition is available");
                } else {
                    android.util.Log.w("StudentDetail", "⚠️ Android Speech Recognition is NOT available");
                    Toast.makeText(this, "⚠️ Speech recognition not available on this device", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                android.util.Log.e("StudentDetail", "❌ Error checking speech recognition availability: " + e.getMessage());
            }
            
            android.util.Log.d("StudentDetail", "=== SPEECH RECOGNITION INITIALIZATION COMPLETE ===");
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "❌ CRITICAL ERROR in initializeSpeechRecognition: " + e.getMessage());
            e.printStackTrace();
            
            // Show error to user but don't crash
            Toast.makeText(this, "Warning: Some speech features may not work properly", Toast.LENGTH_SHORT).show();
        }
    }
    
    /* REMOVED: Old test methods - no longer needed with Vosk implementation */
    
    /**
     * Comprehensive speech recognition diagnostic
     */
    private void runSpeechRecognitionDiagnostic() {
        android.util.Log.d("StudentDetail", "=== STARTING SPEECH RECOGNITION DIAGNOSTIC ===");
        
        // Create diagnostic dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔍 Speech Recognition Diagnostic");
        
        // Create scrollable text view for diagnostic output
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView diagnosticText = new android.widget.TextView(this);
        diagnosticText.setPadding(20, 20, 20, 20);
        diagnosticText.setTextSize(12);
        diagnosticText.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollView.addView(diagnosticText);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Start 10-Second Test", null);
        builder.setNegativeButton("Close", null);
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Initial diagnostic info
        StringBuilder initialInfo = new StringBuilder();
        initialInfo.append("🔍 SPEECH RECOGNITION DIAGNOSTIC\n");
        initialInfo.append("================================\n\n");
        
        // Check basic availability
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            initialInfo.append("✅ Speech Recognition: Available\n");
        } else {
            initialInfo.append("❌ Speech Recognition: NOT Available\n");
        }
        
        // Check audio permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            initialInfo.append("✅ Audio Permission: Granted\n");
        } else {
            initialInfo.append("❌ Audio Permission: NOT Granted\n");
        }
        
        // Check components
        initialInfo.append("\n📱 COMPONENT STATUS:\n");
        initialInfo.append("Vosk Recognizer: " + (voskRecognizer != null ? "✅ Available" : "❌ NULL") + "\n");
        initialInfo.append("Vosk Model Ready: " + (isVoskModelReady ? "✅ Yes" : "❌ No") + "\n");
        initialInfo.append("MFCC Scorer: " + (mfccScorer != null ? "✅ Available" : "❌ NULL") + "\n");
        initialInfo.append("\nℹ️ Note: Using Vosk + MFCC for speech recognition\n");
        
        initialInfo.append("\n🎤 READY FOR SPEECH TEST\n");
        initialInfo.append("Click 'Start 10-Second Test' and speak clearly.\n");
        initialInfo.append("Try saying: 'once upon a time there was'\n\n");
        
        diagnosticText.setText(initialInfo.toString());
        
        // Set up the start test button
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            diagnosticText.append("🎙️ Starting 10-second speech test...\n");
            diagnosticText.append("Speak clearly: 'once upon a time there was'\n\n");
            
            // Create test speech recognizer
            android.speech.SpeechRecognizer testRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            
            testRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    runOnUiThread(() -> {
                        diagnosticText.append("🎤 Ready for speech - start speaking now!\n");
                    });
                }

                @Override
                public void onBeginningOfSpeech() {
                    runOnUiThread(() -> {
                        diagnosticText.append("👂 Speech detected - listening...\n");
                    });
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Show audio level
                    if (rmsdB > -20) {
                        runOnUiThread(() -> {
                            diagnosticText.append("🔊 Audio level: " + String.format("%.1f", rmsdB) + " dB\n");
                        });
                    }
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    runOnUiThread(() -> {
                        diagnosticText.append("📊 Audio buffer: " + buffer.length + " bytes\n");
                    });
                }

                @Override
                public void onEndOfSpeech() {
                    runOnUiThread(() -> {
                        diagnosticText.append("🔄 Speech ended - processing results...\n");
                    });
                }

                @Override
                public void onError(int error) {
                    String errorMsg = getErrorText(error);
                    runOnUiThread(() -> {
                        diagnosticText.append("❌ ERROR: " + errorMsg + " (code: " + error + ")\n");
                        
                        // Provide specific guidance based on error
                        switch (error) {
                            case android.speech.SpeechRecognizer.ERROR_AUDIO:
                                diagnosticText.append("💡 Try: Check microphone, speak louder\n");
                                break;
                            case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
                                diagnosticText.append("💡 Try: Speak more clearly, check background noise\n");
                                break;
                            case android.speech.SpeechRecognizer.ERROR_NETWORK:
                                diagnosticText.append("💡 Try: Check internet connection\n");
                                break;
                            case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                diagnosticText.append("💡 Try: Grant microphone permission in settings\n");
                                break;
                        }
                    });
                    testRecognizer.destroy();
                }

                @Override
                public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    runOnUiThread(() -> {
                        if (matches != null && !matches.isEmpty()) {
                            diagnosticText.append("✅ SUCCESS! Recognition results:\n");
                            for (int i = 0; i < Math.min(matches.size(), 5); i++) {
                                diagnosticText.append("  " + (i + 1) + ". \"" + matches.get(i) + "\"\n");
                            }
                            
                            // Test word matching with the results
                            diagnosticText.append("\n🔍 WORD MATCHING TEST:\n");
                            String[] expectedWords = {"once", "upon", "a", "time", "there", "was"};
                            String recognizedText = matches.get(0).toLowerCase();
                            String[] recognizedWords = recognizedText.split("\\s+");
                            
                            int maxWords = Math.min(expectedWords.length, recognizedWords.length);
                            for (int i = 0; i < maxWords; i++) {
                                boolean match = testWordMatch(recognizedWords[i], expectedWords[i]);
                                String result = match ? "✅" : "❌";
                                diagnosticText.append("  " + result + " '" + recognizedWords[i] + "' vs '" + expectedWords[i] + "'\n");
                            }
                            
                            diagnosticText.append("\n🎯 DIAGNOSTIC COMPLETE!\n");
                            if (matches.size() > 0) {
                                diagnosticText.append("Speech recognition is working properly.\n");
                            }
                        } else {
                            diagnosticText.append("❌ No speech recognized\n");
                            diagnosticText.append("💡 Try speaking louder and more clearly\n");
                        }
                    });
                    testRecognizer.destroy();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    java.util.ArrayList<String> matches = partialResults.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        runOnUiThread(() -> {
                            diagnosticText.append("🔄 Partial: \"" + matches.get(0) + "\"\n");
                        });
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    runOnUiThread(() -> {
                        diagnosticText.append("📡 Event: " + eventType + "\n");
                    });
                }
            });

            // Start listening with enhanced settings
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);

            try {
                testRecognizer.startListening(intent);
                diagnosticText.append("🚀 Test started successfully!\n");
            } catch (Exception e) {
                diagnosticText.append("❌ Failed to start test: " + e.getMessage() + "\n");
                testRecognizer.destroy();
            }

            // Auto-stop after 10 seconds
            new android.os.Handler().postDelayed(() -> {
                try {
                    testRecognizer.stopListening();
                    diagnosticText.append("\n⏰ 10-second test completed!\n");
                } catch (Exception e) {
                    // Ignore
                }
            }, 10000);
        });
        
        // Clean up when dialog closes
        dialog.setOnDismissListener(dialogInterface -> {
            // Any cleanup if needed
        });
    }
    
    /**
     * Test word matching using the same logic as InstantWordHighlighter
     */
    private boolean testWordMatch(String recognized, String expected) {
        // Clean both words for comparison
        String cleanRecognized = recognized.toLowerCase().replaceAll("[^a-z]", "");
        String cleanExpected = expected.toLowerCase().replaceAll("[^a-z]", "");
        
        if (cleanRecognized.equals(cleanExpected)) {
            return true;
        }
        
        // Balanced matching - not too strict, not too lenient
        if (cleanRecognized.length() >= 2 && cleanExpected.length() >= 2) {
            
            // Check if one word contains the other (common with speech recognition)
            if (cleanRecognized.contains(cleanExpected) || cleanExpected.contains(cleanRecognized)) {
                return true;
            }
            
            // For short words (2-4 letters), allow 1-2 character differences
            if (cleanExpected.length() <= 4) {
                int distance = calculateLevenshteinDistance(cleanRecognized, cleanExpected);
                if (distance <= 2) {
                    return true;
                }
            }
            
            // For longer words, use 60% similarity (more lenient threshold)
            else {
                int distance = calculateLevenshteinDistance(cleanRecognized, cleanExpected);
                int maxLength = Math.max(cleanRecognized.length(), cleanExpected.length());
                float similarity = 1.0f - (float) distance / maxLength;
                
                if (similarity >= 0.60f) { // 60% similarity - more lenient for speech recognition
                    return true;
                }
            }
            
            // Check if words start with the same 2-3 letters (common speech recognition pattern)
            if (cleanRecognized.length() >= 3 && cleanExpected.length() >= 3) {
                String recognizedStart = cleanRecognized.substring(0, 3);
                String expectedStart = cleanExpected.substring(0, 3);
                if (recognizedStart.equals(expectedStart)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Show troubleshooting guide for common speech recognition issues
     */
    private void showTroubleshootingGuide() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🆘 Speech Recognition Troubleshooting");
        
        StringBuilder guide = new StringBuilder();
        guide.append("COMMON ISSUES & SOLUTIONS:\n\n");
        
        guide.append("🔴 PROBLEM: App shows 'timeout' constantly\n");
        guide.append("✅ SOLUTIONS:\n");
        guide.append("• Speak louder and closer to microphone\n");
        guide.append("• Check microphone permission in Settings\n");
        guide.append("• Reduce background noise\n");
        guide.append("• Try restarting the app\n\n");
        
        guide.append("🔴 PROBLEM: Words highlighted red when correct\n");
        guide.append("✅ SOLUTIONS:\n");
        guide.append("• Speak more clearly and slowly\n");
        guide.append("• Pronounce each word distinctly\n");
        guide.append("• Check if you're saying the exact word shown\n");
        guide.append("• Try the 'Test Word Matching' to see sensitivity\n\n");
        
        guide.append("🔴 PROBLEM: No speech detected at all\n");
        guide.append("✅ SOLUTIONS:\n");
        guide.append("• Grant microphone permission in app settings\n");
        guide.append("• Check if other apps can use microphone\n");
        guide.append("• Restart the app completely\n");
        guide.append("• Try 'Test Basic Speech Recognition'\n\n");
        
        guide.append("🔴 PROBLEM: Speech recognition very slow\n");
        guide.append("✅ SOLUTIONS:\n");
        guide.append("• Check internet connection (uses online recognition)\n");
        guide.append("• Close other apps using microphone\n");
        guide.append("• Restart device if persistent\n\n");
        
        guide.append("💡 TIPS FOR BEST RESULTS:\n");
        guide.append("• Speak at normal conversational volume\n");
        guide.append("• Hold device 6-12 inches from mouth\n");
        guide.append("• Speak in a quiet environment\n");
        guide.append("• Pronounce words clearly but naturally\n");
        guide.append("• Wait for the word to highlight gold before speaking\n");
        guide.append("• If a word doesn't work, try speaking it slightly differently\n\n");
        
        guide.append("🔧 STILL HAVING ISSUES?\n");
        guide.append("1. Try 'Full Speech Diagnostic' test\n");
        guide.append("2. Check Android speech recognition works in other apps\n");
        guide.append("3. Restart the app completely\n");
        guide.append("4. Check device microphone hardware\n");
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(guide.toString());
        textView.setPadding(20, 20, 20, 20);
        textView.setTextSize(14);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Got it!", null);
        builder.setNegativeButton("Test Speech Now", (dialog, which) -> {
            testBasicSpeechRecognition();
        });
        builder.show();
    }
    private int calculateLevenshteinDistance(String s1, String s2) {
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
    private void testBasicSpeechRecognition() {
        android.util.Log.d("StudentDetail", "=== TESTING BASIC SPEECH RECOGNITION ===");
        
        // Check audio permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "❌ Audio permission required for speech recognition", Toast.LENGTH_LONG).show();
            android.util.Log.e("StudentDetail", "Audio permission not granted");
            checkAudioPermission();
            return;
        }
        
        android.util.Log.d("StudentDetail", "✅ Audio permission granted");

        // Check if speech recognition is available
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "❌ Speech recognition not available on this device", Toast.LENGTH_LONG).show();
            android.util.Log.e("StudentDetail", "Speech recognition not available");
            return;
        }
        
        android.util.Log.d("StudentDetail", "✅ Speech recognition is available");
        Toast.makeText(this, "� Starting speech test - say anything!", Toast.LENGTH_LONG).show();

        // Create a simple speech recognizer for testing
        android.speech.SpeechRecognizer testRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
        
        testRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                android.util.Log.d("StudentDetail", "✅ Ready for speech");
                Toast.makeText(StudentDetail.this, "🎤 Ready - Say something now!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBeginningOfSpeech() {
                android.util.Log.d("StudentDetail", "✅ Speech detected");
                Toast.makeText(StudentDetail.this, "👂 Listening... Keep talking!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Audio level - could show this as feedback
                android.util.Log.v("StudentDetail", "Audio level: " + rmsdB);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                android.util.Log.v("StudentDetail", "Audio buffer received: " + buffer.length + " bytes");
            }

            @Override
            public void onEndOfSpeech() {
                android.util.Log.d("StudentDetail", "✅ Speech ended, processing...");
                Toast.makeText(StudentDetail.this, "🔄 Processing what you said...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int error) {
                String errorMsg = getErrorText(error);
                android.util.Log.e("StudentDetail", "❌ Speech recognition error: " + errorMsg + " (code: " + error + ")");
                Toast.makeText(StudentDetail.this, "❌ Error: " + errorMsg, Toast.LENGTH_LONG).show();
                testRecognizer.destroy();
            }

            @Override
            public void onResults(Bundle results) {
                java.util.ArrayList<String> matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    android.util.Log.d("StudentDetail", "✅ SUCCESS! Recognized: '" + recognizedText + "'");
                    Toast.makeText(StudentDetail.this, "✅ SUCCESS! You said: \"" + recognizedText + "\"", Toast.LENGTH_LONG).show();
                    
                    // Show all possible matches
                    for (int i = 0; i < Math.min(matches.size(), 3); i++) {
                        android.util.Log.d("StudentDetail", "Match " + i + ": " + matches.get(i));
                    }
                } else {
                    android.util.Log.e("StudentDetail", "❌ No speech recognized");
                    Toast.makeText(StudentDetail.this, "❌ No speech recognized - try speaking louder", Toast.LENGTH_LONG).show();
                }
                testRecognizer.destroy();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                java.util.ArrayList<String> matches = partialResults.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String partialText = matches.get(0);
                    android.util.Log.d("StudentDetail", "Partial result: '" + partialText + "'");
                    Toast.makeText(StudentDetail.this, "Hearing: \"" + partialText + "\"...", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                android.util.Log.d("StudentDetail", "Speech event: " + eventType);
            }
        });

        // Start listening with basic settings
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        try {
            testRecognizer.startListening(intent);
            android.util.Log.d("StudentDetail", "✅ Speech recognition started successfully");
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "❌ Failed to start speech recognition: " + e.getMessage());
            Toast.makeText(this, "❌ Failed to start speech recognition: " + e.getMessage(), Toast.LENGTH_LONG).show();
            testRecognizer.destroy();
        }
    }
    
    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case android.speech.SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case android.speech.SpeechRecognizer.ERROR_CLIENT: return "Client side error";
            case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case android.speech.SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case android.speech.SpeechRecognizer.ERROR_NO_MATCH: return "No speech input";
            case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognition service busy";
            case android.speech.SpeechRecognizer.ERROR_SERVER: return "Server error";
            case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown error (" + errorCode + ")";
        }
    }
    
    /**
     * Run comprehensive diagnostics to identify speech recognition issues
     */
    private void runComprehensiveDiagnostics() {
        android.util.Log.d("StudentDetail", "=== STARTING COMPREHENSIVE DIAGNOSTICS ===");
        
        // Check if Vosk is available
        if (voskRecognizer == null) {
            Toast.makeText(this, "❌ Vosk recognizer not initialized for diagnostics", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Create diagnostic dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔍 Speech Recognition Diagnostics");
        
        // Create scrollable text view for diagnostic output
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView diagnosticText = new android.widget.TextView(this);
        diagnosticText.setPadding(20, 20, 20, 20);
        diagnosticText.setTextSize(12);
        diagnosticText.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollView.addView(diagnosticText);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Start Test", null);
        builder.setNegativeButton("Close", null);
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Initial diagnostic info
        StringBuilder initialInfo = new StringBuilder();
        initialInfo.append("🔍 SPEECH RECOGNITION DIAGNOSTIC\n");
        initialInfo.append("================================\n\n");
        
        // Check basic availability
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            initialInfo.append("✅ Speech Recognition: Available\n");
        } else {
            initialInfo.append("❌ Speech Recognition: NOT Available\n");
        }
        
        // Check audio permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            initialInfo.append("✅ Audio Permission: Granted\n");
        } else {
            initialInfo.append("❌ Audio Permission: NOT Granted\n");
        }
        
        // Check components
        initialInfo.append("\n📱 COMPONENT STATUS:\n");
        initialInfo.append("Vosk Recognizer: " + (voskRecognizer != null ? "✅ Available" : "❌ NULL") + "\n");
        initialInfo.append("Vosk Model Ready: " + (isVoskModelReady ? "✅ Yes" : "❌ No") + "\n");
        initialInfo.append("\nℹ️ Note: Using Vosk + MFCC for testing\n");
        
        initialInfo.append("\n🎤 READY FOR SPEECH TEST\n");
        initialInfo.append("Click 'Start Test' and speak clearly.\n");
        initialInfo.append("Try saying: 'once upon a time there was'\n\n");
        
        diagnosticText.setText(initialInfo.toString());
        
        // Set up the start test button
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            diagnosticText.append("🎙️ Starting speech test using Vosk...\n");
            diagnosticText.append("Speak clearly: 'once upon a time there was'\n\n");
            
            // Use basic speech recognition test
            testBasicSpeechRecognition();
            dialog.dismiss();
        });
        
        // Clean up when dialog closes
        dialog.setOnDismissListener(dialogInterface -> {
            // No cleanup needed for basic test
        });
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Audio permission is required for speech recognition", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void getStudentData() {
        Intent intent = getIntent();
        studentId = intent.getStringExtra("student_id");
        if (studentId == null) {
            // Backward compatibility: try to get as int and convert to String
            int legacyId = intent.getIntExtra("student_id", 0);
            studentId = String.valueOf(legacyId);
        }
        studentName = intent.getStringExtra("student_name");
        studentGrade = intent.getStringExtra("student_grade");
        studentProgress = intent.getIntExtra("student_progress", 0);
        studentAvatar = intent.getIntExtra("student_avatar", 0);
        teacherName = intent.getStringExtra("teacher_name");
        
        // Default values if not provided
        if (studentName == null || studentName.isEmpty()) {
            studentName = "Student";
        }
        if (studentGrade == null || studentGrade.isEmpty()) {
            studentGrade = "Grade 5-A";
        }
    }

    private void initializeViews() {
        // Header components
        studentNameText = findViewById(R.id.studentNameText);
        gradeText = findViewById(R.id.gradeText);
        studentProfileImage = findViewById(R.id.studentProfileImage);
        speakLogo = findViewById(R.id.speakLogo);
        
        // Select passage section
        selectPassageLayout = findViewById(R.id.selectPassageLayout);
        selectPassageText = findViewById(R.id.selectPassageText);
        dropdownIcon = findViewById(R.id.dropdownIcon);
        
        // Progress section
        progressCircle = findViewById(R.id.progressCircle);
        progressPercentage = findViewById(R.id.progressPercentage);
        fastReaderText = findViewById(R.id.fastReaderText);
        currentProgressText = findViewById(R.id.currentProgressText);
        
        // Reading activity section
        readingCharacterButton = findViewById(R.id.readingCharacterButton);
        clickToReadText = findViewById(R.id.clickToReadText);
    }

    private void setupClickListeners() {
        // Select passage click listener
        selectPassageLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPassageSelectionModal();
            }
        });

        // Reading character button click listener
        readingCharacterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPassageSelectionModal();
            }
        });
    }

    private void updateUI() {
        // Update student information
        studentNameText.setText(studentName);
        gradeText.setText(studentGrade);
        progressPercentage.setText(studentProgress + "%");
        
        // Update reading level based on progress
        String readingLevel = getReadingLevel(studentProgress);
        fastReaderText.setText(readingLevel);
        
        // Set progress circle color
        setProgressCircleColor(studentProgress);
        
        // Set student profile image if avatar resource is provided
        if (studentAvatar != 0) {
            studentProfileImage.setImageResource(studentAvatar);
        }
    }

    private String getReadingLevel(int progress) {
        if (progress >= 90) {
            return "Independent Level";
        } else if (progress >= 75) {
            return "Instructional Level";
        } else {
            return "Frustration Level";
        }
    }

    private void setProgressCircleColor(int progress) {
        int color;
        if (progress >= 90) {
            color = 0xFF4CAF50; // Green
        } else if (progress >= 70) {
            color = 0xFF2196F3; // Blue
        } else if (progress >= 50) {
            color = 0xFFFFC107; // Amber/Orange
        } else {
            color = 0xFFF44336; // Red
        }
        progressCircle.setBackgroundColor(color);
    }

    private void showPassageSelectionModal() {
        // Check if Vosk model is ready before allowing reading
        if (!isVoskModelReady) {
            String message;
            if (SpeakApplication.isVoskModelLoading) {
                message = "⏳ Speech model is still loading, please wait...";
            } else if (SpeakApplication.voskModelError != null) {
                message = "❌ Speech model failed to load: " + SpeakApplication.voskModelError + "\n\nPlease restart the app.";
            } else {
                message = "❌ Speech model not ready. Please restart the app.";
            }
            
            android.util.Log.w("StudentDetail", "Cannot start reading: " + message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        
        // First show passage selection, then open reading modal
        showSimplePassageSelection();
    }

    private void showSimplePassageSelection() {
        // Simple and reliable AlertDialog for passage selection
        String[] passages = {
            "Test",
            "Cat and Mouse",
            "Marian's Experiment",
            "The Snail with the Biggest House",
            "The Tricycle Man",
            "Anansi's Web"
        };
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select a Reading Passage");
        builder.setItems(passages, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String selectedPassage = passages[which];
                showReadingModal(selectedPassage);
            }
        });
        builder.setNegativeButton("Cancel", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        try {
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error opening passage selection", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReadingModal(String passageTitle) {
        try {
            // Create reading modal dialog
            Dialog readingModal = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            readingModal.setContentView(R.layout.modal_reading_passage);
            readingModal.setCancelable(true);

            // Setup modal components
            setupReadingModalComponents(readingModal, passageTitle);
            
            // Show modal
            readingModal.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error opening reading modal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showReadingResultsModal(String passageTitle) {
        try {
            android.util.Log.d("StudentDetail", "📊 showReadingResultsModal called with passageTitle: '" + passageTitle + "'");
            
            // Always load the correct passage text for this specific modal
            // Don't rely on currentPassageText as it might be from a different passage
            String modalPassageText = getPassageContent(passageTitle);
            android.util.Log.d("StudentDetail", "📊 Loaded modalPassageText for '" + passageTitle + "', length: " + (modalPassageText != null ? modalPassageText.length() : "null"));
            
            // Create results modal dialog
            Dialog resultsModal = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            resultsModal.setContentView(R.layout.modal_reading_results);
            resultsModal.setCancelable(true);
            
            // Store modal reference for later updates
            currentResultsModal = resultsModal;

            // Setup results modal components with the specific passage text
            setupReadingResultsModalComponents(resultsModal, passageTitle, modalPassageText);
            
            // Show modal
            resultsModal.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error opening results modal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupReadingModalComponents(Dialog readingModal, String passageTitle) {
        try {
            // Get modal components
            TextView titleView = readingModal.findViewById(R.id.passageTitle);
            TextView contentView = readingModal.findViewById(R.id.passageContent);
            TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
            FloatingActionButton closeButton = readingModal.findViewById(R.id.closeButton);
            Button microphoneButton = readingModal.findViewById(R.id.microphoneButton);
            Button startReadingButton = readingModal.findViewById(R.id.startReadingButton);
            Button listenButton = readingModal.findViewById(R.id.listenButton);
            Button showResultsButton = readingModal.findViewById(R.id.showResultsButton);
            Button testSimplifiedButton = readingModal.findViewById(R.id.testSimplifiedButton);
            Button testBasicButton = readingModal.findViewById(R.id.testBasicButton);
            ProgressBar progressBar = readingModal.findViewById(R.id.readingProgressBar);
            TextView progressText = readingModal.findViewById(R.id.progressPercentage);

            // Set passage title
            if (titleView != null) {
                titleView.setText(passageTitle);
            }

            // Set passage content
            if (contentView != null) {
                String passageContent = getPassageContent(passageTitle);
                contentView.setText(passageContent);
            }

            // Initialize timer display
            if (timerDisplay != null) {
                timerDisplay.setText("00:00");
                android.util.Log.d("StudentDetail", "⏱️ Timer display found and initialized to 00:00");
                android.util.Log.d("StudentDetail", "⏱️ Timer display ID: " + timerDisplay.getId());
                android.util.Log.d("StudentDetail", "⏱️ Timer display class: " + timerDisplay.getClass().getSimpleName());
            } else {
                android.util.Log.e("StudentDetail", "⏱️ ERROR: Timer display is NULL! ID R.id.timerDisplay not found in modal.");
            }

            // Microphone button - main reading control
            if (microphoneButton != null) {
                microphoneButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isCurrentlyRecording) {
                            // Stop reading
                            stopSpeechRecognition();
                            stopTimerUpdates(); // Stop timer
                            microphoneButton.setText("Press to begin reading");
                            microphoneButton.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_dark));
                            Toast.makeText(StudentDetail.this, "🛑 Reading stopped", Toast.LENGTH_SHORT).show();
                        } else {
                            // Reset highlights before starting
                            if (contentView != null) {
                                contentView.setText(getPassageContent(passageTitle));
                            }
                            
                            // Reset timer
                            resetTimer();
                            
                            // Reset tracking state
                            if (wordCorrect != null && wordFinished != null) {
                                Arrays.fill(wordCorrect, false);
                                Arrays.fill(wordFinished, false);
                                lastPartial = "";
                                lastFinishedIndex = -1;
                            }
                            
                            // Reset session tracking
                            currentWordsRead = 0;
                            currentCorrectWords = 0;
                            currentIncorrectWords = 0;
                            currentAccuracy = 0.0f;
                            hasFinalResults = false;
                            
                            // Start reading
                            microphoneButton.setText("Reading... (tap to stop)");
                            microphoneButton.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
                            startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
                        }
                    }
                });
            }

            // Show Results button - displays reading results modal
            if (showResultsButton != null) {
                showResultsButton.setVisibility(View.VISIBLE);
                showResultsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showReadingResultsModal(passageTitle);
                    }
                });
            }

            // Close button
            if (closeButton != null) {
                closeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Stop any ongoing recognition before closing
                        if (isCurrentlyRecording) {
                            stopSpeechRecognition();
                        }
                        readingModal.dismiss();
                    }
                });
            }

            // Legacy buttons (hidden by default, can be shown for testing)
            if (startReadingButton != null) {
                startReadingButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isCurrentlyRecording) {
                            // Stop reading
                            stopSpeechRecognition();
                            startReadingButton.setText("Start Reading");
                            startReadingButton.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
                            Toast.makeText(StudentDetail.this, "🛑 Reading stopped", Toast.LENGTH_SHORT).show();
                        } else {
                            // Start reading
                            startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
                            startReadingButton.setText("Stop Reading");
                            startReadingButton.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
                        }
                    }
                });
            }

            // Listen button - now tests basic speech recognition
            if (listenButton != null) {
                listenButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Show options: Test Speech, Test Word Matching, Full Diagnostic, or Troubleshooting
                        String[] options = {
                            "🎤 Test Basic Speech Recognition", 
                            "🔍 Test Word Matching Logic",
                            "🔧 Full Speech Diagnostic",
                            "🆘 Troubleshooting Guide"
                        };
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(StudentDetail.this);
                        builder.setTitle("Choose Diagnostic Test");
                        builder.setItems(options, (dialog, which) -> {
                            switch (which) {
                                case 0:
                                    testBasicSpeechRecognition();
                                    break;
                                case 1:
                                    // Test word matching - use basic speech test
                                    testBasicSpeechRecognition();
                                    break;
                                case 2:
                                    runSpeechRecognitionDiagnostic();
                                    break;
                                case 3:
                                    showTroubleshootingGuide();
                                    break;
                            }
                        });
                        builder.show();
                    }
                });
            }

            // Test Simplified Speech Button - tests the new simplified system
            if (testSimplifiedButton != null) {
                testSimplifiedButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        testSimplifiedSpeechRecognition();
                    }
                });
            }

            // Test Basic Speech Button - tests absolute basic speech recognition
            if (testBasicButton != null) {
                testBasicButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        testAbsoluteBasicSpeechRecognition();
                    }
                });
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error setting up reading modal", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupReadingResultsModalComponents(Dialog resultsModal, String passageTitle, String passageText) {
        try {
            // Get modal components
            TextView activityTitle = resultsModal.findViewById(R.id.activityTitle);
            TextView studentName = resultsModal.findViewById(R.id.studentName);
            TextView readingLevel = resultsModal.findViewById(R.id.readingLevel);
            TextView accuracyScoreBox = resultsModal.findViewById(R.id.accuracyScoreBox);
            ImageView studentAvatar = resultsModal.findViewById(R.id.studentAvatar);
            
            // Result values
            TextView totalWordsValue = resultsModal.findViewById(R.id.totalWordsValue);
            TextView timeValue = resultsModal.findViewById(R.id.timeValue);
            TextView wpmValue = resultsModal.findViewById(R.id.wpmValue);
            TextView correctWordsValue = resultsModal.findViewById(R.id.correctWordsValue);
            TextView incorrectWordsValue = resultsModal.findViewById(R.id.incorrectWordsValue);
            
            // Back button
            com.google.android.material.floatingactionbutton.FloatingActionButton backButton = 
                resultsModal.findViewById(R.id.backButton);

            // Set passage title
            if (activityTitle != null) {
                activityTitle.setText(passageTitle.toUpperCase());
            }

            // Set student information
            if (studentName != null) {
                studentName.setText(this.studentName);
            }
            
            // Use reading level from the actual session, not student's overall progress
            if (readingLevel != null) {
                String level = hasFinalResults && !finalReadingLevelName.isEmpty() 
                    ? finalReadingLevelName 
                    : getReadingLevel(studentProgress);
                readingLevel.setText(level);
                android.util.Log.d("StudentDetail", "📊 Results modal reading level: " + level);
            }
            
            // Set student avatar
            if (studentAvatar != null && this.studentAvatar != 0) {
                studentAvatar.setImageResource(this.studentAvatar);
            }

            // Calculate and set reading results
            // These would normally come from the actual reading session data
            // For now, using sample data - you can integrate with actual reading results
            
            if (currentTotalWords > 0) {
                // Get actual results from the speech recognition system
                int totalWords = currentTotalWords;
                int wordsRead = currentWordsRead;
                
                // Use stored final results if available (from onReadingComplete callback)
                long elapsedTime;
                if (hasFinalResults && finalReadingTimeMs > 0) {
                    elapsedTime = finalReadingTimeMs; // Use actual reading time from speech recognition
                    wordsRead = finalWordsRead; // Use actual words read from callback
                    android.util.Log.d("StudentDetail", "📊 Using stored final results: time=" + (elapsedTime/1000) + "s, words=" + wordsRead);
                } else {
                    // Fallback to UI timer if final results not available
                    elapsedTime = getActualElapsedTime();
                    android.util.Log.d("StudentDetail", "📊 Using fallback UI timer: time=" + (elapsedTime/1000) + "s");
                }
                
                // Use stored final accuracy if available, otherwise get current accuracy
                float accuracy;
                if (hasFinalResults) {
                    accuracy = finalReadingAccuracy;
                    android.util.Log.d("StudentDetail", "📊 Using stored final accuracy: " + String.format("%.1f%%", accuracy * 100));
                } else {
                    accuracy = currentAccuracy;
                    android.util.Log.d("StudentDetail", "📊 Using current accuracy: " + String.format("%.1f%%", accuracy * 100));
                }
                
                // Calculate results using the correct formula with actual reading time
                int timeInSeconds = (int) (elapsedTime / 1000);
                float timeInSecondsFloat = elapsedTime / 1000.0f;
                
                // WPM: Use stored value from database if available, otherwise calculate
                int wpm;
                if (hasFinalResults && finalWpm > 0) {
                    wpm = (int) finalWpm; // Use WPM from database (matches progress report exactly)
                    android.util.Log.d("StudentDetail", "📊 Using WPM from database: " + wpm);
                } else {
                    // Fallback calculation (offline mode - calculate from captured time)
                    // Use float division for accuracy, matching VoskMFCCRecognizer calculation
                    wpm = (timeInSecondsFloat > 0 && wordsRead > 0) ? 
                        (int) ((wordsRead * 60.0f) / timeInSecondsFloat) : 0;
                    android.util.Log.d("StudentDetail", "📊 Calculated WPM from captured time (offline): " + wpm);
                }
                
                // Get actual counts from speech recognition system instead of estimates
                int actualCorrectWords = currentCorrectWords;
                int actualIncorrectWords = currentIncorrectWords;
                
                // Use actual counts if available, otherwise fall back to estimates
                int correctWords, incorrectWords;
                if (actualCorrectWords >= 0 && actualIncorrectWords >= 0) {
                    correctWords = actualCorrectWords;
                    incorrectWords = actualIncorrectWords;
                    android.util.Log.d("StudentDetail", "📊 Using ACTUAL counts from speech recognition");
                } else {
                    // Fallback to estimated calculation
                    correctWords = (int) (wordsRead * accuracy);
                    incorrectWords = wordsRead - correctWords;
                    android.util.Log.d("StudentDetail", "📊 Using ESTIMATED counts (fallback)");
                }
                
                // Calculate accuracy percentage for display
                float accuracyPercentage = accuracy * 100;
                
                // Calculate combined score (50% accuracy + 50% pronunciation) for display
                float pronunciation = hasFinalResults ? finalPronunciation : lastMFCCScore;
                float combinedScore = (accuracy * 0.5f) + (pronunciation * 0.5f);
                float combinedPercentage = combinedScore * 100;
                
                android.util.Log.d("StudentDetail", "📊 Results Modal Scores:");
                android.util.Log.d("StudentDetail", "📊   Accuracy: " + String.format("%.1f%%", accuracyPercentage));
                android.util.Log.d("StudentDetail", "📊   Pronunciation: " + String.format("%.1f%%", pronunciation * 100));
                android.util.Log.d("StudentDetail", "📊   Combined: " + String.format("%.1f%%", combinedPercentage));
                
                // Log the calculation for debugging
                android.util.Log.d("StudentDetail", "📊 WPM Calculation (using actual reading time):");
                android.util.Log.d("StudentDetail", "📊   Words Read: " + wordsRead);
                android.util.Log.d("StudentDetail", "📊   Time (seconds): " + timeInSeconds + " (from " + (hasFinalResults && finalReadingTimeMs > 0 ? "speech recognition" : "UI timer") + ")");
                android.util.Log.d("StudentDetail", "📊   Formula: (" + wordsRead + " × 60) ÷ " + timeInSeconds + " = " + wpm + " WPM");
                android.util.Log.d("StudentDetail", "📊   Accuracy: " + String.format("%.1f%%", accuracyPercentage));
                android.util.Log.d("StudentDetail", "📊   Correct Words: " + correctWords + (actualCorrectWords >= 0 ? " (actual)" : " (estimated)"));
                android.util.Log.d("StudentDetail", "📊   Incorrect Words: " + incorrectWords + (actualIncorrectWords >= 0 ? " (actual)" : " (estimated)"));
                
                // Show comparison if using actual counts
                if (actualCorrectWords >= 0 && actualIncorrectWords >= 0) {
                    int estimatedCorrect = (int) (wordsRead * accuracy);
                    int estimatedIncorrect = wordsRead - estimatedCorrect;
                    android.util.Log.d("StudentDetail", "📊 COMPARISON:");
                    android.util.Log.d("StudentDetail", "📊   Estimated: " + estimatedCorrect + " correct, " + estimatedIncorrect + " incorrect");
                    android.util.Log.d("StudentDetail", "📊   Actual: " + actualCorrectWords + " correct, " + actualIncorrectWords + " incorrect");
                    android.util.Log.d("StudentDetail", "📊   Difference: " + (actualCorrectWords - estimatedCorrect) + " correct, " + (actualIncorrectWords - estimatedIncorrect) + " incorrect");
                }
                
                // Always show actual passage word count (even if no reading session started)
                int actualTotalWords = totalWords > 0 ? totalWords : getWordCount(passageText);
                
                // Set accuracy score in yellow box (matching progress report)
                if (accuracyScoreBox != null) {
                    accuracyScoreBox.setText(String.format("%.1f%%", accuracyPercentage));
                }
                
                // Debug logging
                android.util.Log.d("StudentDetail", "📊 TOTAL WORDS DEBUG:");
                android.util.Log.d("StudentDetail", "📊   Passage Title: '" + passageTitle + "'");
                android.util.Log.d("StudentDetail", "📊   Speech Recognition totalWords: " + totalWords);
                android.util.Log.d("StudentDetail", "📊   passageText length: " + (passageText != null ? passageText.length() : "null"));
                if (passageText != null && passageText.length() > 50) {
                    android.util.Log.d("StudentDetail", "📊   passageText preview: '" + passageText.substring(0, 50) + "...'");
                }
                android.util.Log.d("StudentDetail", "📊   getWordCount result: " + getWordCount(passageText));
                android.util.Log.d("StudentDetail", "📊   Final actualTotalWords: " + actualTotalWords);
                
                if (totalWordsValue != null) {
                    totalWordsValue.setText(String.valueOf(actualTotalWords));
                }
                if (timeValue != null) {
                    timeValue.setText(String.valueOf(timeInSeconds > 0 ? timeInSeconds : 0));
                }
                if (wpmValue != null) {
                    wpmValue.setText(wpm > 0 ? wpm + " WPM" : "0 WPM");
                }
                if (correctWordsValue != null) {
                    correctWordsValue.setText(String.valueOf(correctWords));
                }
                if (incorrectWordsValue != null) {
                    incorrectWordsValue.setText(String.valueOf(incorrectWords));
                }
            } else {
                // Use actual passage word count if no reading session data available
                int actualTotalWords = getWordCount(passageText);
                
                // Debug logging
                android.util.Log.d("StudentDetail", "📊 ELSE BLOCK - TOTAL WORDS DEBUG:");
                android.util.Log.d("StudentDetail", "📊   Passage Title: '" + passageTitle + "'");
                android.util.Log.d("StudentDetail", "📊   passageText length: " + (passageText != null ? passageText.length() : "null"));
                if (passageText != null && passageText.length() > 50) {
                    android.util.Log.d("StudentDetail", "📊   passageText preview: '" + passageText.substring(0, 50) + "...'");
                }
                android.util.Log.d("StudentDetail", "📊   getWordCount result: " + actualTotalWords);
                
                if (totalWordsValue != null) totalWordsValue.setText(String.valueOf(actualTotalWords));
                if (timeValue != null) timeValue.setText("0");
                if (wpmValue != null) wpmValue.setText("0 WPM");
                if (accuracyScoreBox != null) accuracyScoreBox.setText("0.0%");
                if (correctWordsValue != null) correctWordsValue.setText("0");
                if (incorrectWordsValue != null) incorrectWordsValue.setText("0");
            }

            // Back button functionality
            if (backButton != null) {
                backButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        resultsModal.dismiss();
                    }
                });
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error setting up results modal", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Start continuous reading with timer integration using Vosk + MFCC
     */
    private void startContinuousReadingWithTimer(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText, TextView timerDisplay) {
        android.util.Log.d("StudentDetail", "=== STARTING VOSK + MFCC READING ===");
        android.util.Log.d("StudentDetail", "Passage: " + passageTitle);
        
        // Check if Vosk model is ready
        if (!isVoskModelReady || voskRecognizer == null) {
            Toast.makeText(this, "⏳ Speech recognition is loading... Please wait.", Toast.LENGTH_LONG).show();
            resetReadingButton(readingModal);
            return;
        }
        
        // Get the passage content TextView for highlighting
        final TextView passageContentView = readingModal.findViewById(R.id.passageContent);
        if (passageContentView == null) {
            android.util.Log.e("StudentDetail", "PassageContentView is NULL!");
            Toast.makeText(this, "❌ Cannot find passage text view", Toast.LENGTH_LONG).show();
            resetReadingButton(readingModal);
            return;
        }
        
        // Update UI to show selected passage
        selectPassageText.setText(passageTitle);
        currentPassageText = getPassageContent(passageTitle);
        
        // Precompute word positions for accurate highlighting
        computeWordSpans(currentPassageText);
        
        // Split passage into words for recognition (needed by watchdog)
        final String[] expectedWords = currentPassageText
            .toLowerCase()
            .replaceAll("[^a-z\\s]", "")
            .split("\\s+");
        
        // Log first 10 expected words for debugging
        StringBuilder firstWords = new StringBuilder();
        for (int i = 0; i < Math.min(10, expectedWords.length); i++) {
            if (i > 0) firstWords.append(", ");
            firstWords.append(expectedWords[i]);
        }
        android.util.Log.d("StudentDetail", String.format("📖 Expected words (first 10): %s", firstWords.toString()));
        android.util.Log.d("StudentDetail", String.format("📖 Total words in passage: %d", expectedWords.length));
        
        // ── Initialize robust detection components ────────────────────────────────
        phoneticMatcher = new PhoneticMatcher();
        awaitingWordIndex = 0;
        timedOutWords.clear();
        
        // Watchdog: auto-advance when child mumbles or skips a word
        wordWatchdog = new WordTimeoutWatchdog((timedOutIndex, timedOutWordText) -> {
            // Runs on main thread (Handler)
            android.util.Log.w("StudentDetail",
                String.format("⏰ Word %d '%s' timed out — marking as mispronounced",
                    timedOutIndex, timedOutWordText));
            
            timedOutWords.add(timedOutIndex);
            
            if (timedOutIndex >= 0 && timedOutIndex < wordCorrect.length) {
                wordFinished[timedOutIndex] = true;
                wordScored[timedOutIndex]   = true;  // scored immediately as wrong
                wordCorrect[timedOutIndex]  = false; // timed-out = mispronounced
            }
            
            // Highlight this word red immediately
            if (passageContentView != null) {
                redrawHighlights(passageContentView);
            }
            
            // Advance to next word — reading continues
            awaitingWordIndex = timedOutIndex + 1;
            if (awaitingWordIndex < expectedWords.length) {
                android.util.Log.d("StudentDetail", String.format("⏭️ Moving to next word %d: '%s'", 
                    awaitingWordIndex, expectedWords[awaitingWordIndex]));
                wordWatchdog.expectWord(awaitingWordIndex, expectedWords[awaitingWordIndex]);
            } else {
                android.util.Log.d("StudentDetail", "✅ All words processed (via timeout)");
            }
        });
        
        // Start watching for the first word
        wordWatchdog.expectWord(0, expectedWords[0]);
        
        // Set recording state
        isCurrentlyRecording = true;
        
        // Start timer
        if (timerDisplay != null) {
            startTimerUpdates(timerDisplay);
        }
        
        // Start Vosk recognition with async Random Forest + MFCC + DistilBERT analysis
        voskRecognizer.startRecognition(expectedWords, currentPassageText, studentId, studentName, passageTitle, new VoskMFCCRecognizer.RecognitionCallback() {
            @Override
            public void onReady() {
                android.util.Log.d("StudentDetail", "✅ Vosk recognition ready");
                runOnUiThread(() -> {
                    Toast.makeText(StudentDetail.this, "🎤 Start reading now!", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onWordRecognized(String recognizedWord, String expectedWord, int wordIndex, 
                                        float pronunciationScore, boolean isCorrect) {
                // ── Phonetic validation: Vosk said it matched, but did it REALLY? ──────
                // Vosk normalizes slang ("singin" → "singing") and marks it correct.
                // We re-check using phonetic similarity to catch those false positives.
                PhoneticMatcher.Result phonetic = phoneticMatcher.match(recognizedWord, expectedWord);
                
                boolean voskSaysCorrect    = isCorrect;
                boolean phoneticSaysClose  = phonetic.isCloseEnough;
                boolean phoneticExactMatch = phonetic.editDistance == 0;
                
                // Decision logic:
                //  - Exact match              → CORRECT
                //  - Vosk correct + phonetic close → CORRECT (Vosk normalized minor variation, OK)
                //  - Vosk correct + phonetic FAR  → WRONG   (Vosk hallucinated / over-normalized)
                //  - Vosk wrong  + phonetic close → WRONG   (child attempted but mispronounced)
                //  - Vosk wrong  + phonetic far   → WRONG   (completely different sound)
                boolean finalCorrect;
                if (phoneticExactMatch) {
                    finalCorrect = true;
                } else if (voskSaysCorrect && phonetic.similarity >= 0.75f) {
                    // Vosk normalized a minor variation (e.g. "walkin" → "walking") — accept
                    finalCorrect = true;
                } else if (voskSaysCorrect && phonetic.similarity < 0.75f) {
                    // Vosk over-normalized a real mispronunciation — reject
                    android.util.Log.w("StudentDetail",
                        String.format("🚫 Vosk false-positive rejected: heard '%s' for '%s' (sim=%.2f)",
                            recognizedWord, expectedWord, phonetic.similarity));
                    finalCorrect = false;
                } else {
                    finalCorrect = false;
                }
                
                android.util.Log.d("StudentDetail", String.format(
                    "📝 Word %d '%s' → heard '%s' | vosk=%b | phonetic=%s | final=%b",
                    wordIndex, expectedWord, recognizedWord,
                    voskSaysCorrect, phonetic.toString(), finalCorrect));
                
                // ── Confirm with watchdog (prevents timeout firing for this word) ──────
                wordWatchdog.wordConfirmed();
                
                currentWordsRead    = wordIndex + 1;
                currentTotalWords   = expectedWords.length;
                awaitingWordIndex   = wordIndex + 1;
                
                // ── Update state and highlight ─────────────────────────────────────────
                final boolean result = finalCorrect;
                runOnUiThread(() -> {
                    if (wordIndex >= 0 && wordIndex < wordCorrect.length) {
                        wordFinished[wordIndex] = true;
                        wordScored[wordIndex]   = true;    // scored immediately — no waiting for RF
                        wordCorrect[wordIndex]  = result;
                        isProcessingWord        = false;
                        
                        if (passageContentView != null) {
                            redrawHighlights(passageContentView);
                        }
                    }
                    
                    if (progressBar != null && progressText != null && wordIndex % 3 == 0) {
                        int pct = (int)((float)(wordIndex + 1) / expectedWords.length * 100);
                        progressBar.setProgress(pct);
                        progressText.setText(pct + "%");
                    }
                });
                
                // ── Start watchdog for the NEXT word ──────────────────────────────────
                if (awaitingWordIndex < expectedWords.length) {
                    wordWatchdog.expectWord(awaitingWordIndex, expectedWords[awaitingWordIndex]);
                }
                
                // ── Check if reading is complete ──────────────────────────────────────
                if (wordIndex >= expectedWords.length - 1) {
                    android.util.Log.d("StudentDetail", "🎉 Last word reached!");
                    wordWatchdog.stop();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (voskRecognizer != null && voskRecognizer.isRecognizing()) {
                            voskRecognizer.stopRecognition();
                        }
                    }, 1000);
                }
            }
            
            @Override
            public void onPartialResult(String partial) {
                // Partial results are for logging ONLY.
                // They no longer drive highlighting — onWordRecognized is the sole driver.
                // This eliminates the highlight-jumping-ahead bug entirely.
                if (partial != null && !partial.isEmpty()) {
                    android.util.Log.v("StudentDetail", "Partial (display-only): " + partial);
                }
            }
            
            @Override
            public void onComplete(float overallAccuracy, float averagePronunciation, float comprehensionScore, ReadingLevelClassifier.ReadingLevelResult readingLevel) {
                android.util.Log.d("StudentDetail", String.format("✅ Reading complete! Waiting for RF analysis..."));
                
                // Store preliminary results (will be updated by RF)
                finalReadingAccuracy = overallAccuracy;
                finalPronunciation = averagePronunciation;
                finalReadingLevelName = readingLevel.levelName;
                lastMFCCScore = averagePronunciation;
                finalReadingTimeMs = getActualElapsedTime();
                finalWordsRead = currentWordsRead;
                hasFinalResults = true;
                
                runOnUiThread(() -> {
                    // Stop timer immediately
                    stopTimerUpdates();
                    
                    // Update progress to 100%
                    if (progressBar != null && progressText != null) {
                        progressBar.setProgress(100);
                        progressText.setText("100%");
                    }
                    
                    // Reset recording state
                    isCurrentlyRecording = false;
                    resetReadingButton(readingModal);
                    
                    // Show "Analyzing pronunciation..." indicator
                    Button showResultsButton = readingModal.findViewById(R.id.showResultsButton);
                    if (showResultsButton != null) {
                        showResultsButton.setText("Analyzing pronunciation...");
                        showResultsButton.setEnabled(false);
                        android.util.Log.d("StudentDetail", "⏳ Waiting for RF analysis to complete...");
                    }
                });
            }
            
            @Override
            public void onComprehensionUpdated(float comprehensionScore) {
                android.util.Log.d("StudentDetail", String.format("📊 Comprehension updated: %.0f%%", comprehensionScore * 100));
                // Comprehension calculated async - already saved to database
                // UI already showed estimated score, no need to update
            }
            
            @Override
            public void onPronunciationUpdated(float pronunciationScore) {
                android.util.Log.d("StudentDetail", String.format("🎤 Pronunciation updated from Random Forest: %.0f%%", pronunciationScore * 100));
                // Update stored pronunciation score with accurate Random Forest result
                lastMFCCScore = pronunciationScore;
                finalPronunciation = pronunciationScore;
                
                // Recalculate combined score with updated pronunciation
                runOnUiThread(() -> {
                    if (hasFinalResults) {
                        float combinedScore = (finalReadingAccuracy * 0.5f) + (pronunciationScore * 0.5f);
                        
                        android.util.Log.d("StudentDetail", String.format("📊 Updated scores - Combined: %.0f%%, Accuracy: %.0f%%, Pronunciation: %.0f%%",
                            combinedScore * 100, finalReadingAccuracy * 100, pronunciationScore * 100));
                    }
                });
            }
            
            @Override
            public void onRFAnalysisComplete(List<Boolean> wordCorrectness) {
                android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
                android.util.Log.d("StudentDetail", "✅ RF ANALYSIS COMPLETE - Updating word colors");
                android.util.Log.d("StudentDetail", String.format("   Received %d word results", wordCorrectness.size()));
                
                runOnUiThread(() -> {
                    // Update word correctness based on RF results
                    int correctCount = 0;
                    int incorrectCount = 0;
                    
                    android.util.Log.d("StudentDetail", String.format("🎨 Updating %d words with RF results", wordCorrectness.size()));
                    
                    for (int i = 0; i < wordCorrectness.size() && i < wordCorrect.length; i++) {
                        boolean isCorrect = wordCorrectness.get(i);
                        wordCorrect[i] = isCorrect;
                        wordScored[i] = true; // Now scored by RF
                        wordFinished[i] = true; // Ensure word is marked as finished
                        
                        if (isCorrect) {
                            correctCount++;
                        } else {
                            incorrectCount++;
                        }
                        
                        // Log first 10 words for debugging
                        if (i < 10) {
                            android.util.Log.d("StudentDetail", String.format("   Word %d: %s (finished=%b, scored=%b, correct=%b)", 
                                i, isCorrect ? "✅ CORRECT" : "❌ INCORRECT", wordFinished[i], wordScored[i], wordCorrect[i]));
                        }
                    }
                    
                    // Update session data with RF results
                    currentCorrectWords = correctCount;
                    currentIncorrectWords = incorrectCount;
                    currentAccuracy = currentTotalWords > 0 ? (float)correctCount / currentTotalWords : 0.0f;
                    
                    android.util.Log.d("StudentDetail", String.format("   RF Results: %d correct, %d incorrect (%.1f%% accuracy)",
                        correctCount, incorrectCount, currentAccuracy * 100));
                    
                    // Redraw passage with accurate red/green colors
                    android.util.Log.d("StudentDetail", "🎨 Calling redrawHighlights with passageContentView...");
                    if (passageContentView != null) {
                        redrawHighlights(passageContentView);
                        passageContentView.invalidate(); // Force UI refresh
                        android.util.Log.d("StudentDetail", "✅ redrawHighlights completed and view invalidated");
                    } else {
                        android.util.Log.e("StudentDetail", "❌ passageContentView is NULL - cannot redraw!");
                    }
                    
                    android.util.Log.d("StudentDetail", "✅ Passage updated with accurate RF colors");
                    android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
                    
                    // Update button to show "Saving results..."
                    Button showResultsButton = readingModal.findViewById(R.id.showResultsButton);
                    if (showResultsButton != null) {
                        showResultsButton.setText("Saving results...");
                        showResultsButton.setEnabled(false);
                        android.util.Log.d("StudentDetail", "⏳ Waiting for session to be saved...");
                        
                        // OFFLINE FALLBACK: If onSessionSaved doesn't fire within 2 seconds,
                        // show results anyway with the data we have
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (!showResultsButton.isEnabled()) {
                                android.util.Log.w("StudentDetail", "⚠️ Session save timeout - showing results with local data");
                                showResultsButton.setText("View Results");
                                showResultsButton.setEnabled(true);
                                showResultsButton.performClick();
                            }
                        }, 2000);
                    }
                });
            }
            
            @Override
            public void onRFAnalysisCompleteWithConfidence(List<Boolean> wordCorrectness, List<Float> wordConfidences) {
                // Use the simpler callback above - confidence scores not needed for UI
                android.util.Log.d("StudentDetail", "📊 RF analysis with confidence scores received");
            }
            
            @Override
            public void onSessionSaved(ReadingSession savedSession) {
                android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
                android.util.Log.d("StudentDetail", "💾 SESSION SAVED CALLBACK RECEIVED");
                android.util.Log.d("StudentDetail", String.format("💾 Saved session data - Accuracy: %.1f%%, Pronunciation: %.1f%%, WPM: %.0f, Level: %s",
                    savedSession.getAccuracy() * 100, savedSession.getPronunciation() * 100, 
                    savedSession.getWpm(), savedSession.getReadingLevelName()));
                android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
                
                // Update stored values with database session data FIRST
                runOnUiThread(() -> {
                    finalReadingAccuracy = savedSession.getAccuracy();
                    finalPronunciation = savedSession.getPronunciation();
                    finalReadingLevelName = savedSession.getReadingLevelName();
                    finalWordsRead = savedSession.getTotalWords();
                    finalWpm = savedSession.getWpm(); // ← Store WPM from database, don't recalculate
                    // ❌ Don't reverse-engineer time from WPM — use what was captured in onComplete
                    // finalReadingTimeMs already set correctly in onComplete()
                    
                    // Also update current values so setupReadingResultsModalComponents uses DB data
                    currentCorrectWords = savedSession.getCorrectWords();
                    currentIncorrectWords = savedSession.getTotalWords() - savedSession.getCorrectWords();
                    currentAccuracy = savedSession.getAccuracy();
                    
                    android.util.Log.d("StudentDetail", "✅ UI variables updated with saved session data");
                    android.util.Log.d("StudentDetail", String.format("   finalReadingAccuracy: %.1f%%", finalReadingAccuracy * 100));
                    android.util.Log.d("StudentDetail", String.format("   finalPronunciation: %.1f%%", finalPronunciation * 100));
                    android.util.Log.d("StudentDetail", String.format("   finalWpm: %.0f", finalWpm));
                    android.util.Log.d("StudentDetail", String.format("   finalReadingTimeMs: %d ms", finalReadingTimeMs));
                    android.util.Log.d("StudentDetail", String.format("   finalWordsRead: %d", finalWordsRead));
                    
                    // Re-enable the button and auto-show results with accurate DB data
                    Button showResultsButton = readingModal.findViewById(R.id.showResultsButton);
                    if (showResultsButton != null) {
                        showResultsButton.setText("View Results");
                        showResultsButton.setEnabled(true);
                        android.util.Log.d("StudentDetail", "📊 Auto-showing results modal with accurate database data");
                        // Auto-click now that we have accurate DB data
                        // The modal will use the updated finalReadingAccuracy, finalWpm, etc.
                        showResultsButton.performClick();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("StudentDetail", "❌ Vosk error: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(StudentDetail.this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
                    resetReadingButton(readingModal);
                    resetTimer();
                });
            }
        });
        
        android.util.Log.d("StudentDetail", "✅ Vosk + MFCC recognition started");
    }
    
    /**
     * Update results modal display with saved session data from database
     * This ensures the modal matches what's shown in progress reports
     */
    private void updateResultsModalWithSessionData(Dialog resultsModal, ReadingSession session) {
        try {
            android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
            android.util.Log.d("StudentDetail", "🔄 UPDATING RESULTS MODAL WITH DATABASE SESSION DATA");
            android.util.Log.d("StudentDetail", String.format("  Accuracy: %.1f%%", session.getAccuracy() * 100));
            android.util.Log.d("StudentDetail", String.format("  Pronunciation: %.1f%%", session.getPronunciation() * 100));
            android.util.Log.d("StudentDetail", String.format("  WPM: %.0f", session.getWpm()));
            android.util.Log.d("StudentDetail", String.format("  Level: %s", session.getReadingLevelName()));
            android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
            
            // Get modal components
            TextView readingLevel = resultsModal.findViewById(R.id.readingLevel);
            TextView accuracyScoreBox = resultsModal.findViewById(R.id.accuracyScoreBox);
            TextView wpmValue = resultsModal.findViewById(R.id.wpmValue);
            TextView correctWordsValue = resultsModal.findViewById(R.id.correctWordsValue);
            TextView incorrectWordsValue = resultsModal.findViewById(R.id.incorrectWordsValue);
            TextView totalWordsValue = resultsModal.findViewById(R.id.totalWordsValue);
            TextView timeValue = resultsModal.findViewById(R.id.timeValue);
            
            // Update reading level
            if (readingLevel != null) {
                readingLevel.setText(session.getReadingLevelName());
                android.util.Log.d("StudentDetail", "  ✅ Updated reading level");
            }
            
            // Update accuracy score in yellow box (matching progress report)
            if (accuracyScoreBox != null) {
                accuracyScoreBox.setText(String.format("%.1f%%", session.getAccuracy() * 100));
                android.util.Log.d("StudentDetail", String.format("  ✅ Updated accuracy score: %.1f%%", session.getAccuracy() * 100));
            }
            
            // Update WPM
            if (wpmValue != null) {
                String wpmText = String.format("%.0f WPM", session.getWpm());
                wpmValue.setText(wpmText);
                android.util.Log.d("StudentDetail", String.format("  ✅ Updated WPM: %s (was showing different value)", wpmText));
            }
            
            // Update word counts
            if (correctWordsValue != null) {
                correctWordsValue.setText(String.valueOf(session.getCorrectWords()));
                android.util.Log.d("StudentDetail", "  ✅ Updated correct words");
            }
            if (incorrectWordsValue != null) {
                int incorrectWords = session.getTotalWords() - session.getCorrectWords();
                incorrectWordsValue.setText(String.valueOf(incorrectWords));
                android.util.Log.d("StudentDetail", "  ✅ Updated incorrect words");
            }
            if (totalWordsValue != null) {
                totalWordsValue.setText(String.valueOf(session.getTotalWords()));
                android.util.Log.d("StudentDetail", "  ✅ Updated total words");
            }
            
            // Update time (use captured elapsed time, not reverse-engineered from WPM)
            if (timeValue != null && finalReadingTimeMs > 0) {
                int timeInSeconds = (int) (finalReadingTimeMs / 1000);
                timeValue.setText(String.valueOf(timeInSeconds));
                android.util.Log.d("StudentDetail", String.format("  ✅ Updated time: %d seconds (from captured elapsed time)", timeInSeconds));
            }
            
            android.util.Log.d("StudentDetail", "✅✅✅ Results modal successfully updated with database session data");
            android.util.Log.d("StudentDetail", "═══════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "❌ Error updating results modal: " + e.getMessage(), e);
        }
    }
    
    /**
     * Precompute word positions in passage text
     * Call this when passage is loaded
     */
    private void computeWordSpans(String passageText) {
        wordSpans.clear();
        
        // Use regex to find all words (alphanumeric sequences)
        Pattern pattern = Pattern.compile("\\b\\w+\\b");
        Matcher matcher = pattern.matcher(passageText);
        
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String word = passageText.substring(start, end);
            
            wordSpans.add(new WordSpan(word, start, end));
        }
        
        // Initialize tracking arrays for real-time highlighting
        int n = wordSpans.size();
        wordCorrect = new boolean[n];
        wordScored = new boolean[n];
        wordFinished = new boolean[n];
        Arrays.fill(wordCorrect, false);
        Arrays.fill(wordScored, false);
        Arrays.fill(wordFinished, false);
        lastPartial = "";
        lastFinishedIndex = -1;
        
        android.util.Log.d("StudentDetail", "✅ Computed " + wordSpans.size() + " word spans with tracking arrays");
    }
    
    /**
     * Highlight word at specific index using precomputed positions
     * This is accurate and doesn't break with repeated words or punctuation
     */
    /**
     * Redraw all word highlights based on current state
     * Subtle Yellow = processing, Green = correct, Red = incorrect, No color = not yet spoken
     * Optimized to reduce UI lag
     */
    private void redrawHighlights(TextView textView) {
        try {
            android.util.Log.d("StudentDetail", "🎨 redrawHighlights called");
            
            // Get or create spannable
            CharSequence currentText = textView.getText();
            android.text.Spannable spannable;
            
            if (currentText instanceof android.text.Spannable) {
                spannable = (android.text.Spannable) currentText;
            } else {
                spannable = new android.text.SpannableString(currentText);
            }
            
            // Clear old spans
            android.text.style.BackgroundColorSpan[] old = spannable.getSpans(0, spannable.length(), android.text.style.BackgroundColorSpan.class);
            for (android.text.style.BackgroundColorSpan span : old) {
                spannable.removeSpan(span);
            }
            
            int greenCount = 0;
            int redCount = 0;
            int yellowCount = 0;
            int skippedCount = 0;
            
            // Apply highlights for finished words only
            for (int i = 0; i < wordSpans.size(); i++) {
                if (!wordFinished[i]) {
                    skippedCount++;
                    continue; // Skip unfinished words
                }
                
                WordSpan ws = wordSpans.get(i);
                
                int color;
                // If word is being processed, show subtle yellow instead of final color
                if (isProcessingWord && i == currentWordIndex) {
                    color = android.graphics.Color.parseColor("#FFF9C4"); // SUBTLE YELLOW = processing
                    yellowCount++;
                } else if (wordScored[i]) {
                    // Only show final color if word has been scored by Random Forest
                    if (wordCorrect[i]) {
                        color = android.graphics.Color.parseColor("#66BB6A"); // GREEN = correct
                        greenCount++;
                    } else {
                        color = android.graphics.Color.parseColor("#EF5350"); // RED = incorrect
                        redCount++;
                    }
                } else {
                    // Word is finished but not yet scored by Random Forest - show YELLOW (needs review)
                    color = android.graphics.Color.parseColor("#FFF59D"); // YELLOW = needs review
                    yellowCount++;
                }
                
                spannable.setSpan(
                    new android.text.style.BackgroundColorSpan(color),
                    ws.start, ws.end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            
            android.util.Log.d("StudentDetail", String.format("🎨 Highlighting summary: %d GREEN, %d RED, %d YELLOW, %d skipped (not finished)",
                greenCount, redCount, yellowCount, skippedCount));
            
            // Only update TextView if spannable was newly created
            if (!(currentText instanceof android.text.Spannable)) {
                textView.setText(spannable);
            }
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "Error redrawing highlights", e);
        }
    }
    
    /**
     * Mark finished words from partial results (real-time)
     * Called from onPartialResult callback
     * Optimized to reduce lag during continuous reading
     */
    private void onPartialUpdate(String partial, TextView textView) {
        partial = partial.toLowerCase().trim();
        if (partial.isEmpty()) return;
        
        String cleanCurrent = partial.replaceAll("[^a-z\\s]", "").trim();
        String cleanLast = lastPartial.replaceAll("[^a-z\\s]", "").trim();
        
        String[] currentWords = cleanCurrent.isEmpty() ? new String[0] : cleanCurrent.split("\\s+");
        String[] lastWords = cleanLast.isEmpty() ? new String[0] : cleanLast.split("\\s+");
        
        // If we have more words now, word matching/scoring is about to happen
        if (currentWords.length > lastWords.length) {
            int finishedIndex = currentWords.length - 2; // Last word still in progress
            
            if (finishedIndex >= 0 && finishedIndex > lastFinishedIndex && finishedIndex < wordSpans.size()) {
                lastFinishedIndex = finishedIndex;
                currentWordIndex = finishedIndex; // Track which word is being processed
                isProcessingWord = true; // Set processing flag BEFORE marking as finished
                
                // Mark as finished but don't set final color yet - will show yellow
                wordFinished[finishedIndex] = true;
                
                // ALWAYS redraw when marking a word as processing to show subtle yellow
                // This ensures users see the yellow "processing" state before final color
                redrawHighlights(textView);
                
                // Reduced logging
                if (finishedIndex % 10 == 0) {
                    android.util.Log.d("StudentDetail", "Marked word " + finishedIndex + " as processing (yellow)");
                }
            }
        }
        
        // OPTIMIZATION: Update current word highlight less frequently
        // Only update when word changes, not on every partial result
        // AND only if not currently processing a word
        if (currentWords.length > 0 && !cleanCurrent.equals(cleanLast) && !isProcessingWord) {
            int currentIndex = currentWords.length - 1;
            if (currentIndex < wordSpans.size() && !wordFinished[currentIndex]) {
                // Show current word with temporary highlight (will be updated by onWordRecognized)
                highlightCurrentWord(textView, currentIndex);
            }
        }
        
        lastPartial = cleanCurrent;
    }
    
    /**
     * Highlight the current word being spoken (temporary gold highlight)
     * This provides instant feedback before Vosk confirms the word
     */
    private void highlightCurrentWord(TextView textView, int wordIndex) {
        try {
            CharSequence currentText = textView.getText();
            android.text.Spannable spannable;
            
            if (currentText instanceof android.text.Spannable) {
                spannable = (android.text.Spannable) currentText;
            } else {
                spannable = new android.text.SpannableString(currentText);
            }
            
            // Remove old "current word" highlights (gold)
            android.text.style.BackgroundColorSpan[] old = spannable.getSpans(0, spannable.length(), android.text.style.BackgroundColorSpan.class);
            for (android.text.style.BackgroundColorSpan span : old) {
                int color = span.getBackgroundColor();
                // Remove only gold highlights (current word), keep green/red (finished words)
                if (color == android.graphics.Color.parseColor("#FFD54F")) {
                    spannable.removeSpan(span);
                }
            }
            
            // Add gold highlight for current word
            if (wordIndex >= 0 && wordIndex < wordSpans.size()) {
                WordSpan ws = wordSpans.get(wordIndex);
                spannable.setSpan(
                    new android.text.style.BackgroundColorSpan(android.graphics.Color.parseColor("#FFD54F")), // GOLD = in progress
                    ws.start, ws.end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            
            // Update TextView
            if (!(currentText instanceof android.text.Spannable)) {
                textView.setText(spannable);
            }
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "Error highlighting current word", e);
        }
    }
    
    /**
     * Automatically start continuous reading when passage opens
     */
    private void autoStartContinuousReading(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        // Check audio permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "🎤 Audio permission required for reading. Please grant permission.", Toast.LENGTH_LONG).show();
            checkAudioPermission();
            return;
        }

        // Update UI to show selected passage
        selectPassageText.setText(passageTitle);
        currentPassageText = getPassageContent(passageTitle);
        
        // Precompute word positions for accurate highlighting
        computeWordSpans(currentPassageText);
        
        // Set recording state
        isCurrentlyRecording = true;
        
        // Start continuous reading immediately
        android.util.Log.d("StudentDetail", "🌊 Auto-starting Vosk reading for: " + passageTitle);
        
        // Check if Vosk is ready
        if (voskRecognizer != null && isVoskModelReady) {
            startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, null);
        } else {
            android.util.Log.e("StudentDetail", "❌ Vosk recognizer not available");
            Toast.makeText(this, "⏳ Speech recognition is loading... Please wait.", Toast.LENGTH_LONG).show();
            isCurrentlyRecording = false;
        }
    }

    private String getPassageContent(String passageTitle) {
        // Return actual passage content based on title (case-insensitive)
        String normalizedTitle = passageTitle.toLowerCase();
        
        switch (normalizedTitle) {
            case "test":
                return "Maria woke up early and looked outside the window. The sun was bright, and the birds were singing in the trees. She washed her face, ate breakfast, and packed her bag for school. On the way, she met her friend and they walked together with happy smiles.";

            case "cat and mouse":
                return "A mouse and a cat lived in an old house. The mouse stayed in a hole while the cat slept under the table. One night, the mouse got out of its hole. \"Mmm, Cheese!\" it thought, as it went up the table. As it started nibbling the cheese, a fork fell. It woke the cat up so it ran up the table. But the mouse was too fast for the cat. It quickly dashed to its hole. Safe at last!";

            case "marian's experiment":
                return "Marian came home from school. She went to the kitchen and saw her mother cooking. \"Mama, do we have mongo seeds?\" asked Marian. \"I will do an experiment.\" \"Yes, we have some in the cabinet,\" answered Mama. Marian got some seeds and planted them in a wooden box. She watered the seeds every day. She made sure they got enough sun. After three days, Marian was happy to see stems and leaves sprouting. Her mongo seeds grew into young plants.";

            case "the snail with the biggest house":
                return "A little snail told his father, \"I want to have the biggest house.\" \"Keep your house light and easy to carry,\" said his father. But, the snail ate a lot until his house grew enormous. \"You now have the biggest house,\" said the snails. After a while, the snails have eaten all the grass in the farm. They decided to move to another place. \"Help! I cannot move,\" said the snail with the biggest house. The snails tried to help but the house was too heavy. So the snail with the biggest house was left behind.";

            case "the tricycle man":
                return "Nick is a tricycle man. He waits for riders every morning. \"Please take me to the bus station,\" says Mr. Perez. \"Please take me to the market,\" says Mrs. Pardo. \"Please take us to school,\" say Mike and Kris. \"But I can take only one of you,\" says Nick to the children. \"Oh, I can sit behind you Nick,\" says Mr. Perez. \"Kris or Mike can take my seat.\" \"Thank you, Mr. Perez,\" say Mike and Kris.";

            case "anansi's web":
                return "Anansi was tired of her web. So one day, she said \"I will go live with the ant.\" Now, the ant lived in a small hill. Once in the hill Anansi cried, \"This place is too dark! I will go live with the bees.\" When she got to the beehive, Anansi cried, \"This place is too hot and sticky! I will go live with the beetle.\" But on her way to beetle's home she saw her web. \"Maybe a web is the best place after all.\"";

            default:
                return "This is a sample passage for '" + passageTitle + "'. The full story content would be " +
                       "displayed here for the student to read. This passage would contain age-appropriate content " +
                       "that helps students practice their reading skills while enjoying an engaging story.\n\n" +
                       "The passage would be formatted with proper paragraphs and spacing to make it easy for " +
                       "young readers to follow along. Teachers can use this to assess reading comprehension and " +
                       "pronunciation skills.";
        }
    }

    /**
     * Count the number of words in a passage text
     */
    private int getWordCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        // Clean the text and split into words (same logic as ContinuousFlowSpeechRecognition)
        String cleanText = text.replaceAll("[^a-zA-Z0-9\\s']", " ");
        String[] words = cleanText.trim().split("\\s+");
        
        // Count non-empty words
        int count = 0;
        for (String word : words) {
            if (!word.trim().isEmpty()) {
                count++;
            }
        }
        
        return count;
    }

    private void startReadingActivity(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        // Check audio permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio permission required for speech recognition", Toast.LENGTH_SHORT).show();
            checkAudioPermission();
            return;
        }

        // Update UI to show selected passage
        selectPassageText.setText(passageTitle);
        currentPassageText = getPassageContent(passageTitle);
        
        // Precompute word positions for accurate highlighting
        computeWordSpans(currentPassageText);
        
        // Get the button to check its current state
        Button startButton = readingModal.findViewById(R.id.startReadingButton);
        
        if (!isCurrentlyRecording) {
            // Start speech recognition
            android.util.Log.d("StudentDetail", "🎤 Starting speech recognition...");
            
            // Update button to show "Stop" state immediately
            if (startButton != null) {
                startButton.setText("Stop Reading");
                startButton.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
            }
            
            isCurrentlyRecording = true;
            startSpeechRecognition(passageTitle, readingModal, progressBar, progressText);
        } else {
            // Stop current recording
            android.util.Log.d("StudentDetail", "🛑 Stopping speech recognition...");
            
            // Update button to show "Start" state immediately
            if (startButton != null) {
                startButton.setText("Start Fluency Reading");
                startButton.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
            }
            
            isCurrentlyRecording = false;
            stopSpeechRecognition();
            
            // Show feedback
            Toast.makeText(this, "🛑 Reading stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSpeechRecognition(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        // Use Vosk + MFCC for speech recognition
        if (voskRecognizer != null && isVoskModelReady) {
            TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
            startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
        } else {
            android.util.Log.e("StudentDetail", "❌ Vosk recognizer not ready!");
            Toast.makeText(this, "⏳ Speech recognition is loading... Please wait.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void startContinuousFlowReading(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
            android.util.Log.d("StudentDetail", "=== REDIRECTING TO VOSK + MFCC ===");
            android.util.Log.d("StudentDetail", "Passage: " + passageTitle);

            // Redirect to Vosk implementation
            TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
            startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
        }

    
    private void startSpeechDrivenWordHighlighting(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "=== USING VOSK + MFCC ===");
        android.util.Log.d("StudentDetail", "Passage: " + passageTitle);
        
        // Use Vosk implementation
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
    }
    
    private void startModelEnhancedWordHighlighting(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "=== USING VOSK + MFCC ===");
        android.util.Log.d("StudentDetail", "Passage: " + passageTitle);
        
        // Use Vosk implementation
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
    }
    
    // Fallback methods - all redirect to Vosk + MFCC implementation
    private void startRealTimeWordTracking(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "Using Vosk + MFCC");
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
    }
    
    private void startEnhancedWordTrackingRecognition(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "Using Vosk + MFCC");
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
    }
    
    private void startEnhancedRegularRecognition(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "Using Vosk + MFCC");
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
    }
    
    private void startWordTrackingRecognition(String passageTitle, Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "Using Vosk + MFCC");
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer(passageTitle, readingModal, progressBar, progressText, timerDisplay);
    }

    private void stopSpeechRecognition() {
        android.util.Log.d("StudentDetail", "🛑 Stopping Vosk recognition...");
        
        try {
            // Stop watchdog
            if (wordWatchdog != null) {
                wordWatchdog.stop();
                android.util.Log.d("StudentDetail", "✅ Stopped word timeout watchdog");
            }
            
            // Stop Vosk recognizer
            if (voskRecognizer != null && voskRecognizer.isRecognizing()) {
                voskRecognizer.stopRecognition();
                android.util.Log.d("StudentDetail", "✅ Stopped Vosk recognition");
            }
            
            // Reset recording state
            isCurrentlyRecording = false;
            
            android.util.Log.d("StudentDetail", "✅ Recognition stopped successfully");
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "❌ Error stopping recognition: " + e.getMessage());
            e.printStackTrace();
            // Still reset the state even if there was an error
            isCurrentlyRecording = false;
        }
    }

    private void resetReadingButton(Dialog readingModal) {
        android.util.Log.d("StudentDetail", "🔄 Resetting reading buttons to start state...");
        
        try {
            // Reset microphone button (primary control)
            Button microphoneButton = readingModal.findViewById(R.id.microphoneButton);
            if (microphoneButton != null) {
                microphoneButton.setText("Press to begin reading");
                microphoneButton.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_dark));
                android.util.Log.d("StudentDetail", "✅ Microphone button reset to 'Press to begin reading'");
            }
            
            // Reset legacy start button (if visible)
            Button startButton = readingModal.findViewById(R.id.startReadingButton);
            if (startButton != null) {
                startButton.setText("Start Fluency Reading");
                startButton.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
                android.util.Log.d("StudentDetail", "✅ Start button reset to 'Start Fluency Reading'");
            }
            
            // Reset timer display
            TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
            if (timerDisplay != null) {
                timerDisplay.setText("00:00");
                android.util.Log.d("StudentDetail", "✅ Timer display reset to 00:00");
            }
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "❌ Error resetting buttons: " + e.getMessage());
        }
        
        // Always reset recording state
        isCurrentlyRecording = false;
        android.util.Log.d("StudentDetail", "✅ Recording state reset to false");
    }

    private void updateReadingProgress(float confidence, boolean isCorrect, ProgressBar progressBar, TextView progressText) {
        if (progressBar != null && progressText != null) {
            // Calculate progress based on confidence and correctness
            int currentProgress = progressBar.getProgress();
            int increment = isCorrect ? (int)(confidence * 20) : 5; // Larger increment for correct reading
            int newProgress = Math.min(100, currentProgress + increment);
            
            progressBar.setProgress(newProgress);
            progressText.setText(newProgress + "%");
            
            // Update student's overall progress
            if (newProgress > studentProgress) {
                studentProgress = newProgress;
                updateStudentProgressInUI();
            }
        }
    }

    private void updateStudentProgressInUI() {
        // Update the main progress display
        progressPercentage.setText(studentProgress + "%");
        
        // Update reading level
        String readingLevel = getReadingLevel(studentProgress);
        fastReaderText.setText(readingLevel);
        
        // Update progress circle color
        setProgressCircleColor(studentProgress);
    }
    
    /**
     * Calculate combined score from text accuracy and MFCC pronunciation score
     * Weight: 60% text accuracy, 40% pronunciation quality
     */
    private float getCombinedScore(float textScore, float mfccScore) {
        return (textScore * 0.6f) + (mfccScore * 0.4f);
    }
    
    /**
     * Get combined feedback message with both text and pronunciation scores
     */
    private String getCombinedFeedback(float textScore, float mfccScore, float combinedScore) {
        StringBuilder feedback = new StringBuilder();
        
        feedback.append("📊 Reading Score: ").append((int)(combinedScore * 100)).append("%\n\n");
        feedback.append("📝 Text Accuracy: ").append((int)(textScore * 100)).append("%\n");
        feedback.append("🎤 Pronunciation: ").append((int)(mfccScore * 100)).append("%\n\n");
        
        if (combinedScore >= 0.9f) {
            feedback.append("🌟 Excellent! Perfect reading!");
        } else if (combinedScore >= 0.8f) {
            feedback.append("👍 Very good! Keep it up!");
        } else if (combinedScore >= 0.7f) {
            feedback.append("😊 Good job! Practice more.");
        } else {
            feedback.append("💪 Keep practicing!");
        }
        
        return feedback.toString();
    }

    private void startListeningActivity(String passageTitle, Dialog readingModal) {
        Toast.makeText(this, "Playing audio for: " + passageTitle, Toast.LENGTH_SHORT).show();
        
        // TODO: Implement text-to-speech functionality
        // This is where you would use TextToSpeech to read the passage aloud
    }

    private void simulateReadingProgress(ProgressBar progressBar, TextView progressText) {
        // Simple progress simulation (replace with actual reading assessment)
        if (progressBar != null && progressText != null) {
            android.os.Handler handler = new android.os.Handler();
            final int[] progress = {0};
            
            Runnable updateProgress = new Runnable() {
                @Override
                public void run() {
                    if (progress[0] <= 100) {
                        progressBar.setProgress(progress[0]);
                        progressText.setText(progress[0] + "%");
                        progress[0] += 10;
                        handler.postDelayed(this, 1000); // Update every second
                    }
                }
            };
            
            handler.post(updateProgress);
        }
    }

    private void handlePassageSelection(String passageTitle) {
        // This method is now replaced by showReadingModal
        showReadingModal(passageTitle);
    }

    private void navigateToHome() {
        Intent intent = new Intent(StudentDetail.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void navigateBack() {
        // Go back to teacher dashboard
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        // Just go back normally since we're using AlertDialog
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        // Clean up Vosk recognizer
        if (voskRecognizer != null) {
            voskRecognizer.release();
            voskRecognizer = null;
            android.util.Log.d("StudentDetail", "✅ Vosk recognizer released");
        }
        
        // Clean up MFCC resources
        if (mfccScorer != null) {
            mfccScorer.close();
            mfccScorer = null;
            android.util.Log.d("StudentDetail", "✅ MFCC scorer closed");
        }
        
        // Clean up timer
        resetTimer();
        
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop recording when activity is paused
        if (isCurrentlyRecording) {
            stopSpeechRecognition();
        }
    }
    
    /**
     * Start highlighting demonstration - not available in this build
     */
    private void startHighlightingDemo(Dialog readingModal, ProgressBar progressBar, TextView progressText) {
        android.util.Log.d("StudentDetail", "=== STARTING VOSK + MFCC DEMO ===");
        Toast.makeText(this, "🎤 Starting Vosk + MFCC demo", Toast.LENGTH_SHORT).show();
        
        // Use Vosk implementation
        TextView timerDisplay = readingModal.findViewById(R.id.timerDisplay);
        startContinuousReadingWithTimer("Demo Passage", readingModal, progressBar, progressText, timerDisplay);
    }
    
    /**
     * Test simplified speech recognition - not available in this build
     */
    private void testSimplifiedSpeechRecognition() {
        android.util.Log.d("StudentDetail", "=== SIMPLIFIED SPEECH RECOGNITION NOT AVAILABLE ===");
        Toast.makeText(this, "ℹ️ SimplifiedSpeechRecognition not implemented in this build.\nUsing basic speech test instead.", Toast.LENGTH_LONG).show();
        
        // Fallback to basic speech recognition test
        testBasicSpeechRecognition();
    }
    
    /**
     * Test absolute basic speech recognition - not available in this build
     */
    private void testAbsoluteBasicSpeechRecognition() {
        android.util.Log.d("StudentDetail", "=== BASIC SPEECH TEST NOT AVAILABLE ===");
        Toast.makeText(this, "ℹ️ BasicSpeechTest not implemented in this build.\nUsing basic speech test instead.", Toast.LENGTH_LONG).show();
        
        // Fallback to basic speech recognition test
        testBasicSpeechRecognition();
    }

    /**
     * Start timer display updates - Phil-IRI Compliant
     */
    private void startTimerUpdates(TextView timerDisplay) {
        android.util.Log.d("StudentDetail", "⏱️ Starting Phil-IRI compliant timer (60-second maximum)");
        
        if (timerDisplay == null) {
            android.util.Log.e("StudentDetail", "⏱️ ERROR: timerDisplay is NULL!");
            return;
        }
        
        // Stop any existing timer
        stopTimerUpdates();
        
        // Set up Phil-IRI timer
        currentTimerDisplay = timerDisplay;
        timerStartTime = System.currentTimeMillis();
        timerRunning = true;
        philIriTimeExpired = false;
        
        android.util.Log.d("StudentDetail", "⏱️ Phil-IRI timer started at: " + timerStartTime + " (max duration: 60 seconds)");
        
        // Start the timer loop
        updateTimerDisplay();
    }
    
    /**
     * Update timer display and schedule next update - Phil-IRI Compliant
     */
    private void updateTimerDisplay() {
        if (!timerRunning || currentTimerDisplay == null || timerStartTime == 0) {
            android.util.Log.w("StudentDetail", "⏱️ Timer update skipped - not running or invalid state");
            return;
        }
        
        try {
            long elapsedTime = System.currentTimeMillis() - timerStartTime;
            
            // Phil-IRI Rule: Continue reading beyond 60 seconds with scoring penalty
            if (elapsedTime >= PHIL_IRI_MAX_TIME_MS && !philIriTimeExpired) {
                philIriTimeExpired = true;
                
                android.util.Log.d("StudentDetail", "⏱️ Phil-IRI 60-second limit reached - continuing reading with scoring penalty");
                
                // DO NOT stop reading - let it continue until completion
                // The scoring penalty will be applied in the final results
                
                // Show notification that penalty will apply
                runOnUiThread(() -> {
                    Toast.makeText(StudentDetail.this, "⏰ 60 seconds reached - continuing with time penalty", Toast.LENGTH_LONG).show();
                });
                
                // Timer continues running to track excess time
            }
            
            // Calculate display time (don't cap at 60 seconds - show actual elapsed time)
            int totalSeconds = (int) (elapsedTime / 1000);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            
            String timeText = String.format("%02d:%02d", minutes, seconds);
            
            // Add visual indicator if time exceeded 60 seconds
            if (philIriTimeExpired) {
                timeText += " ⚠️"; // Warning indicator for excess time
            }
            
            android.util.Log.d("StudentDetail", "⏱️ Phil-IRI Timer: " + timeText + " (elapsed: " + elapsedTime + "ms" + 
                              (philIriTimeExpired ? ", EXCESS TIME - penalty will apply" : "") + ")");
            
            // Update the display
            currentTimerDisplay.setText(timeText);
            
            // Schedule next update if still running (continue indefinitely)
            if (timerRunning) {
                timerHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateTimerDisplay();
                    }
                }, 1000);
                android.util.Log.v("StudentDetail", "⏱️ Next Phil-IRI timer update scheduled");
            } else {
                android.util.Log.d("StudentDetail", "⏱️ Phil-IRI timer stopped - time expired or manually stopped");
            }
            
        } catch (Exception e) {
            android.util.Log.e("StudentDetail", "⏱️ Phil-IRI timer update error: " + e.getMessage(), e);
            // Try again if still running and under time limit
            if (timerRunning && !philIriTimeExpired) {
                timerHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateTimerDisplay();
                    }
                }, 1000);
            }
        }
    }
    
    /**
     * Stop timer display updates
     */
    private void stopTimerUpdates() {
        android.util.Log.d("StudentDetail", "⏱️ stopTimerUpdates called");
        
        timerRunning = false;
        
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
            android.util.Log.d("StudentDetail", "⏱️ Timer runnable removed from handler");
        }
        
        android.util.Log.d("StudentDetail", "⏱️ Timer updates stopped");
    }
    
    /**
     * Completely reset timer - Phil-IRI Compliant
     */
    private void resetTimer() {
        stopTimerUpdates();
        currentTimerDisplay = null;
        timerStartTime = 0;
        philIriTimeExpired = false;
        android.util.Log.d("StudentDetail", "⏱️ Phil-IRI timer completely reset");
    }
    
    /**
     * Get actual elapsed time for Phil-IRI CWPM calculation
     * UPDATED: No longer caps at 60 seconds - returns actual elapsed time
     */
    private long getActualElapsedTime() {
        if (timerStartTime == 0) {
            return 0;
        }
        
        long elapsedTime = System.currentTimeMillis() - timerStartTime;
        
        // Phil-IRI Rule: Return actual elapsed time (no capping)
        // Scoring penalties will be applied separately for time beyond 60 seconds
        
        android.util.Log.d("StudentDetail", "⏱️ Phil-IRI actual elapsed time: " + elapsedTime + "ms (" + (elapsedTime/1000) + " seconds)" + 
                          (elapsedTime > PHIL_IRI_MAX_TIME_MS ? " - EXCESS TIME" : ""));
        return elapsedTime;
    }

}