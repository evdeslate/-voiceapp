# Comprehensive Speech Highlighting Fix

## Problem Analysis

Current issues:
1. ❌ Highlighting jumps around the passage (confusing)
2. ❌ Words highlight too far ahead (inaccurate)
3. ❌ Partial results cause false matches
4. ❌ Free-form recognition makes matching harder
5. ❌ User doesn't know what's happening during reading

## Solution: Two-Phase Approach

### Phase 1: During Reading (Simple Guidance)
- Show **BLUE** highlight for current reading position
- Move highlight forward based on **onWordRecognized** (not partial results)
- No green/red colors yet (avoid confusion)
- Clear visual feedback: "I'm listening to this word"

### Phase 2: After Reading (Accurate Scoring)
- Vosk RF + DirectAudio analyze all words
- Show final **GREEN** (correct) / **RED** (incorrect) colors
- Accurate mispronunciation detection
- User sees final results

## Implementation Strategy

### 1. Disable Partial Result Highlighting
**Why:** Partial results are unreliable and cause scattered highlighting

```java
@Override
public void onPartialResult(String partial) {
    // Log only, no highlighting
    android.util.Log.d("StudentDetail", "📝 Partial: '" + partial + "'");
    // NO UI updates from partial results
}
```

### 2. Use onWordRecognized for Sequential Highlighting
**Why:** onWordRecognized is called after Vosk confirms a word match

```java
@Override
public void onWordRecognized(String recognizedWord, String expectedWord, 
                             int wordIndex, float pronunciationScore, boolean isCorrect) {
    runOnUiThread(() -> {
        // Mark word as being read (BLUE highlight)
        if (wordIndex >= 0 && wordIndex < wordFinished.length) {
            wordFinished[wordIndex] = true;
            wordScored[wordIndex] = false; // Not scored yet
            
            // Redraw with BLUE color for current position
            redrawHighlights(passageContentView);
        }
    });
}
```

### 3. Update Highlight Colors
**Current:** Yellow during reading  
**New:** Blue during reading (clearer "current position" indicator)

```java
private void redrawHighlights(TextView passageView) {
    for (int i = 0; i < wordSpans.size(); i++) {
        WordSpan span = wordSpans.get(i);
        
        if (wordScored[i]) {
            // Phase 2: Show final colors (after analysis)
            if (wordCorrect[i]) {
                setGreenHighlight(span); // Correct
            } else {
                setRedHighlight(span); // Incorrect
            }
        } else if (wordFinished[i]) {
            // Phase 1: Show blue (currently reading)
            setBlueHighlight(span); // Reading position
        } else {
            // Not reached yet
            setNoHighlight(span);
        }
    }
}
```

### 4. Improve Word Matching in VoskMFCCRecognizer
**Current:** Free-form recognition with fuzzy matching  
**Issue:** Too many false matches

**Solution:** Use hybrid approach
- Free-form recognition (to catch mispronunciations)
- Strict sequential matching (to prevent jumping)
- Small tolerance for recognition errors

```java
private void processRecognizedText(String recognizedText) {
    String[] recognizedWords = recognizedText.toLowerCase().trim().split("\\s+");
    
    for (String recognizedWord : recognizedWords) {
        if (currentWordIndex >= expectedWords.length) break;
        
        // Check ONLY the next expected word (strict sequential)
        String expectedWord = expectedWords[currentWordIndex].toLowerCase();
        
        // Calculate match score
        float matchScore = calculateMatchScore(recognizedWord, expectedWord);
        
        // Strict threshold: 75% similarity required
        if (matchScore >= 0.75f) {
            // Word matched - notify UI
            boolean isCorrect = matchScore >= 0.85f; // Preliminary score
            
            if (callback != null) {
                callback.onWordRecognized(recognizedWord, expectedWord, 
                    currentWordIndex, matchScore, isCorrect);
            }
            
            currentWordIndex++; // Move to next word
        } else {
            // No match - might be mispronunciation or noise
            Log.d(TAG, String.format("⚠️ Word mismatch: '%s' vs expected '%s' (%.0f%% match)",
                recognizedWord, expectedWord, matchScore * 100));
        }
    }
}
```

### 5. Add User Guidance
Show clear instructions to user:

```java
private void showReadingInstructions() {
    Toast.makeText(this, 
        "📖 Read the passage aloud\n" +
        "🔵 Blue = Current word\n" +
        "⏸️ Tap to stop when done", 
        Toast.LENGTH_LONG).show();
}
```

## Color Scheme

| Color | Meaning | When |
|-------|---------|------|
| 🔵 Blue | Currently reading this word | During reading (Phase 1) |
| ⚪ White | Not reached yet | During reading |
| 🟢 Green | Pronounced correctly | After analysis (Phase 2) |
| 🔴 Red | Mispronounced | After analysis (Phase 2) |

## Benefits

✅ **Clear User Guidance** - Blue shows current position  
✅ **No Confusion** - No premature green/red colors  
✅ **Accurate Scoring** - Final colors based on full analysis  
✅ **Sequential Flow** - Highlights move forward naturally  
✅ **Mispronunciation Detection** - Free-form catches errors  

## Implementation Files

1. `StudentDetail.java` - UI highlighting logic
2. `VoskMFCCRecognizer.java` - Word matching logic
3. `WordSpan.java` - Highlight color management

## Testing Checklist

- [ ] Blue highlight moves sequentially during reading
- [ ] No scattered highlights across passage
- [ ] Green/red colors appear only after completion
- [ ] "feather" vs "father" detected as incorrect
- [ ] "carry" is detected correctly
- [ ] User instructions are clear
- [ ] No confusion about what colors mean

---
**Next Step:** Implement these changes in code
