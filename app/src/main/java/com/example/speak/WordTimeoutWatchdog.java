package com.example.speak;

import android.os.Handler;
import android.os.Looper;

/**
 * WordTimeoutWatchdog
 *
 * Solves the stall problem:
 *   When a child mumbles a complex word, Vosk may never fire onWordRecognized.
 *   This watchdog monitors how long the current word has been waiting.
 *   If it exceeds the timeout, it auto-advances the word as SKIPPED/MISPRONOUNCED.
 *
 * How it works:
 *   - Call expectWord(index, expectedText) when you start waiting for a word
 *   - Call wordConfirmed() when Vosk fires onWordRecognized (cancels the timeout)
 *   - If timeout fires before wordConfirmed(), onTimeout callback is called
 *
 * Timeout values (tunable):
 *   NORMAL_WORD_TIMEOUT_MS  = 3000  (3 sec for common short words)
 *   COMPLEX_WORD_TIMEOUT_MS = 5000  (5 sec for long/complex words)
 *   A word is "complex" if length > 8 characters
 */
public class WordTimeoutWatchdog {

    public interface TimeoutCallback {
        /**
         * Called when a word times out (child mumbled/skipped it).
         * @param wordIndex   index of the word that timed out
         * @param wordText    the expected word text
         */
        void onTimeout(int wordIndex, String wordText);
    }

    private static final int NORMAL_WORD_TIMEOUT_MS  = 3000;
    private static final int COMPLEX_WORD_TIMEOUT_MS = 5000;
    private static final int COMPLEX_WORD_LENGTH      = 8;

    private final Handler  handler   = new Handler(Looper.getMainLooper());
    private final TimeoutCallback callback;

    private Runnable  pendingTimeout = null;
    private int       currentIndex   = -1;
    private String    currentWord    = "";
    private boolean   isRunning      = false;

    public WordTimeoutWatchdog(TimeoutCallback callback) {
        this.callback = callback;
    }

    /**
     * Start watching for word at wordIndex.
     * Cancels any existing timeout first.
     */
    public void expectWord(int wordIndex, String wordText) {
        cancel(); // clear previous timeout

        currentIndex = wordIndex;
        currentWord  = wordText;
        isRunning    = true;

        int timeoutMs = (wordText != null && wordText.length() > COMPLEX_WORD_LENGTH)
            ? COMPLEX_WORD_TIMEOUT_MS
            : NORMAL_WORD_TIMEOUT_MS;

        pendingTimeout = () -> {
            if (isRunning) {
                android.util.Log.w("WordTimeoutWatchdog",
                    String.format("⏰ Timeout: word %d '%s' (waited %dms)",
                        wordIndex, wordText, timeoutMs));
                isRunning = false;
                callback.onTimeout(wordIndex, wordText);
            }
        };

        handler.postDelayed(pendingTimeout, timeoutMs);

        android.util.Log.d("WordTimeoutWatchdog",
            String.format("⏱ Watching word %d '%s' (timeout: %dms)",
                wordIndex, wordText, timeoutMs));
    }

    /**
     * Call this when Vosk confirms the word — cancels the timeout.
     */
    public void wordConfirmed() {
        cancel();
    }

    /**
     * Cancel the current timeout without firing the callback.
     */
    public void cancel() {
        if (pendingTimeout != null) {
            handler.removeCallbacks(pendingTimeout);
            pendingTimeout = null;
        }
        isRunning = false;
    }

    /** Stop watchdog completely (call on recording stop) */
    public void stop() {
        cancel();
        currentIndex = -1;
        currentWord  = "";
    }
}
