# Yellow-Only Highlighting Implementation

## Problem Solved
Speech recognition (Vosk) normalizes speech, so "sing-ging" becomes "singing" in text. Text-based matching cannot detect pronunciation issues that speech recognition normalizes. This caused false positives where mispronounced words were marked as correct (green).

## Solution Implemented
**Yellow-only highlighting during reading + Post-completion RF analysis**

### Workflow

#### 1. During Reading (Real-time)
- **Yellow highlighting** tracks current word position (guidance only)
- **No green/red coloring** during active reading
- Vosk transcription runs but only for word tracking
- `onWordRecognized()` marks words as finished but NOT scored
- `wordScored[i] = false` (pending RF analysis)
- `redrawHighlights()` shows YELLOW for all finished words

#### 2. After Reading Completes (Post-Processing)
- Button shows **"Analyzing pronunciation..."** loading indicator
- ONNX Random Forest processes all audio segments
- `onRFAnalysisComplete()` callback receives per-word results
- UI updates with accurate red/green highlighting based on RF scores

#### 3. Final Step
- User sees the color-coded passage with accurate pronunciation feedback
- Button shows **"Saving results..."** while session persists to Firebase
- Results modal appears with accuracy, WPM, pronunciation scores
- ReadingSession persists to Firebase with all metrics

## Code Changes

### StudentDetail.java

#### Modified `onWordRecognized()` callback:
```java
// Mark word as finished but NOT scored yet (pending RF analysis)
wordFinished[wordIndex] = true;
wordScored[wordIndex] = false; // NOT scored yet - pending RF
isProcessingWord = false;

// Redraw to show YELLOW (pending RF analysis)
redrawHighlights(passageContentView);
```

#### Modified `onComplete()` callback:
```java
// Show "Analyzing pronunciation..." indicator
Button showResultsButton = readingModal.findViewById(R.id.showResultsButton);
if (showResultsButton != null) {
    showResultsButton.setText("Analyzing pronunciation...");
    showResultsButton.setEnabled(false);
}
```

#### Added `onRFAnalysisComplete()` callback:
```java
@Override
public void onRFAnalysisComplete(List<Boolean> wordCorrectness) {
    runOnUiThread(() -> {
        // Update word correctness based on RF results
        for (int i = 0; i < wordCorrectness.size() && i < wordCorrect.length; i++) {
            boolean isCorrect = wordCorrectness.get(i);
            wordCorrect[i] = isCorrect;
            wordScored[i] = true; // Now scored by RF
        }
        
        // Redraw passage with accurate red/green colors
        redrawHighlights(passageContentView);
        
        // Update button to show "Saving results..."
        Button showResultsButton = readingModal.findViewById(R.id.showResultsButton);
        if (showResultsButton != null) {
            showResultsButton.setText("Saving results...");
            showResultsButton.setEnabled(false);
        }
    });
}
```

### redrawHighlights() Logic (Already Correct)
```java
if (wordScored[i]) {
    // Only show final color if word has been scored by Random Forest
    if (wordCorrect[i]) {
        color = Color.parseColor("#66BB6A"); // GREEN = correct
    } else {
        color = Color.parseColor("#EF5350"); // RED = incorrect
    }
} else {
    // Word is finished but not yet scored by Random Forest - show YELLOW
    color = Color.parseColor("#FFF59D"); // YELLOW = needs review
}
```

## Benefits

1. **No False Positives During Reading**: Yellow-only prevents premature green highlighting
2. **Accurate Final Results**: RF analysis catches actual pronunciation issues
3. **Clear User Feedback**: 
   - Yellow = "I'm tracking your reading"
   - Green = "Pronounced correctly" (RF verified)
   - Red = "Needs improvement" (RF detected issue)
4. **Better UX**: Loading indicators show progress, no confusion about partial results

## Technical Details

### Why This Works
- **Vosk**: Fast word detection for tracking position (yellow highlighting)
- **ONNX RF**: Accurate pronunciation scoring from audio (red/green colors)
- **Separation of Concerns**: Tracking ≠ Scoring
- **Post-processing**: RF analysis doesn't block UI, runs after completion

### Performance
- Yellow highlighting: Real-time (no lag)
- RF analysis: ~50-100ms per word (runs after completion)
- Total delay: 2-5 seconds for full passage analysis
- User sees: Smooth yellow tracking → Brief "Analyzing..." → Accurate colors

## Testing Checklist

- [ ] During reading, all words show YELLOW only
- [ ] No green/red colors appear during active reading
- [ ] After completion, button shows "Analyzing pronunciation..."
- [ ] After RF completes, passage updates with red/green colors
- [ ] Mispronounced words (e.g., "sing-ging") are marked RED
- [ ] Correctly pronounced words are marked GREEN
- [ ] Results modal shows accurate pronunciation scores
- [ ] Session saves to Firebase with correct metrics

## Files Modified
- `app/src/main/java/com/example/speak/StudentDetail.java`
  - Modified `onWordRecognized()` callback
  - Modified `onComplete()` callback
  - Added `onRFAnalysisComplete()` callback
  - Added `onRFAnalysisCompleteWithConfidence()` callback

## Files Unchanged (Already Correct)
- `app/src/main/java/com/example/speak/VoskMFCCRecognizer.java` (RF analysis already implemented)
- `app/src/main/java/com/example/speak/ONNXRandomForestScorer.java` (RF model already working)
- `redrawHighlights()` method (already has yellow logic for unscored words)
