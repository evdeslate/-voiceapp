# Hybrid Mode UI Highlighting Fix

## Problem
Results modal showed 11 mispronounced words, but UI only highlighted 5 red words.

## Root Cause
In hybrid mode, TWO systems were updating the `wordCorrect[]` array:
1. **DirectAudio** - Updates via `onWordAnalyzed()` with accurate ONNX Random Forest results
2. **Vosk RF Analysis** - Updates via `onRFAnalysisComplete()` with Vosk-based MFCC results

The problem: **Vosk's `onRFAnalysisComplete()` was OVERWRITING DirectAudio's accurate results!**

This caused a race condition where:
- DirectAudio correctly identified 11 mispronounced words
- Vosk RF then overwrote those results with its own analysis (only 5 incorrect)
- UI showed Vosk's results (5 red words)
- Database saved DirectAudio's results (11 incorrect)
- Results modal showed database data (11 incorrect) ❌ MISMATCH!

## Solution
Modified `onRFAnalysisComplete()` to SKIP processing when in hybrid mode, since DirectAudio provides the accurate pronunciation assessment.

## Changes Made

### File: `StudentDetail.java`

#### 1. Added hybrid mode check in `onRFAnalysisComplete()` (line ~2040)
```java
@Override
public void onRFAnalysisComplete(List<Boolean> wordCorrectness) {
    Log.d("StudentDetail", "🎨 RF ANALYSIS COMPLETE - UPDATING UI COLORS");
    Log.d("StudentDetail", String.format("  useHybridMode: %b", useHybridMode));
    
    // HYBRID MODE: Skip Vosk RF results - DirectAudio provides accurate pronunciation
    if (useHybridMode) {
        Log.d("StudentDetail", "⚠️ HYBRID MODE: Skipping Vosk RF results (using DirectAudio instead)");
        return; // Don't overwrite DirectAudio's accurate results!
    }
    
    // ... rest of Vosk RF processing (only runs in Vosk-only mode)
}
```

#### 2. Added redraw in `onWordAnalyzed` callback (line ~1560)
```java
@Override
public void onWordAnalyzed(int wordIndex, boolean isCorrect, float confidence) {
    runOnUiThread(() -> {
        if (wordIndex >= 0 && wordIndex < wordCorrect.length) {
            wordCorrect[wordIndex] = isCorrect;
            wordScored[wordIndex] = true;
            wordFinished[wordIndex] = true;
            
            // Redraw highlights to show this word's result immediately
            if (passageContentView != null) {
                redrawHighlights(passageContentView);
            }
        }
    });
}
```

#### 3. Added final redraw in `onComplete` callback (line ~1570)
```java
@Override
public void onComplete(List<Boolean> wordCorrectness, float overallScore, long durationMs) {
    // Force final UI redraw to ensure all words are highlighted
    runOnUiThread(() -> {
        if (passageContentView != null) {
            redrawHighlights(passageContentView);
            passageContentView.invalidate();
            passageContentView.requestLayout();
            
            // Verify UI state with logging
            int uiCorrectCount = 0;
            int uiIncorrectCount = 0;
            int uiScoredCount = 0;
            for (int i = 0; i < wordCorrect.length; i++) {
                if (wordScored[i]) {
                    uiScoredCount++;
                    if (wordCorrect[i]) {
                        uiCorrectCount++;
                    } else {
                        uiIncorrectCount++;
                    }
                }
            }
            Log.d("StudentDetail", "🔍 DIRECTAUDIO UI STATE VERIFICATION:");
            Log.d("StudentDetail", String.format("  Words scored in UI: %d / %d", uiScoredCount, wordCorrect.length));
            Log.d("StudentDetail", String.format("  UI Correct: %d, UI Incorrect: %d", uiCorrectCount, uiIncorrectCount));
        }
    });
}
```

#### 4. Added passageContentView capture in `startHybridReading()` (line ~1545)
```java
private void startHybridReading(...) {
    // Get the passage content TextView for highlighting (must be final for callback)
    final TextView passageContentView = readingModal.findViewById(R.id.passageContent);
    if (passageContentView == null) {
        Log.e("StudentDetail", "PassageContentView is NULL!");
    }
    
    // Start DirectAudio with callbacks that can access passageContentView
    directAudioAnalyzer.startRecording(new DirectAudioPronunciationAnalyzer.AnalysisCallback() {
        // ... callbacks can now access passageContentView
    });
}
```

## Expected Behavior
- DirectAudio analyzes pronunciation with ONNX Random Forest (accurate)
- Vosk provides real-time yellow highlighting (word tracking guidance)
- Vosk RF analysis is SKIPPED in hybrid mode (no overwriting)
- UI updates progressively as DirectAudio analyzes each word
- Final UI refresh ensures all words are highlighted correctly
- Results modal and UI highlighting now show the SAME number of mispronounced words ✅

## Testing
1. Start a reading session in hybrid mode
2. Complete the reading
3. Check logs for "⚠️ HYBRID MODE: Skipping Vosk RF results" - confirms Vosk isn't overwriting
4. Check logs for "🔍 DIRECTAUDIO UI STATE VERIFICATION" - should show all 47 words scored
5. Verify UI highlighting matches the results modal exactly (e.g., if modal shows 11 incorrect, UI should highlight 11 red words)
6. Scroll through the passage to ensure all red words are visible

## Notes
- The fix prevents Vosk RF from overwriting DirectAudio's accurate results
- DirectAudio uses ONNX Random Forest (more accurate than Vosk's MFCC-based scoring)
- Vosk still provides valuable real-time yellow highlighting for word tracking
- In Vosk-only mode (useHybridMode = false), Vosk RF analysis still works normally
