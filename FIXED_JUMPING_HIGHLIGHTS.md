# Fixed Jumping Highlights - Stable Sequential Progression

## Problem Identified

From the screenshot, highlights were scattered all over:
- "A little" (beginning)
- "I want to have" (middle)
- "snail", "house", "the", "snails" (various places)

This happened because:
1. Vosk partial results can reset/decrease
2. `highlightCurrentWord()` was removing old highlights
3. Logic was highlighting multiple words at once

## Solution Applied

### 1. Removed Gold Highlighting
Removed the `highlightCurrentWord()` call that was causing jumps.
- No more temporary gold highlights
- Only stable yellow highlights

### 2. Added Protection Against Resets
```java
// If word count decreased, Vosk reset - ignore
if (currentWords.length < lastWords.length) {
    return; // Ignore this update
}
```

### 3. Only Highlight NEW Words
```java
// Calculate difference
int newStableWords = currentStableCount - lastStableCount;

// Only highlight the NEW words
for (int i = 0; i < newStableWords; i++) {
    int wordIndex = lastFinishedIndex + 1; // Next word only
    wordFinished[wordIndex] = true;
    lastFinishedIndex++;
}
```

## How It Works Now

```
Partial: "a"
  → 1 word, 0 stable (1-1=0)
  → No highlighting yet

Partial: "a little"
  → 2 words, 1 stable (2-1=1)
  → NEW stable: 1 - 0 = 1
  → Highlight word 0 ("A")

Partial: "a little snail"
  → 3 words, 2 stable (3-1=2)
  → NEW stable: 2 - 1 = 1
  → Highlight word 1 ("little")

Partial: "a little snail told"
  → 4 words, 3 stable (4-1=3)
  → NEW stable: 3 - 2 = 1
  → Highlight word 2 ("snail")
```

## Key Improvements

✅ **One word at a time** - Only highlights next sequential word
✅ **Ignores resets** - Protects against Vosk resetting partial results
✅ **No gold highlights** - Removed jumping temporary highlights
✅ **Better logging** - Shows exactly which word is being highlighted

## Expected Behavior

1. **Start reading** - No highlights yet
2. **Say 2+ words** - First word gets yellow
3. **Continue reading** - Each new word adds one more yellow highlight
4. **Highlights accumulate** - Yellow highlights stay in place
5. **Final colors** - Green/red replace yellow after recognition completes

## Files Modified

- `app/src/main/java/com/example/speak/StudentDetail.java` (line ~2693-2745)
  - Added protection against decreasing word counts
  - Changed to only highlight NEW stable words
  - Removed `highlightCurrentWord()` call

## Testing

Rebuild and test - you should see:
1. Highlights start at beginning of passage
2. One new yellow highlight appears for each word spoken
3. Highlights move forward sequentially (never backward)
4. No jumping or scattered highlights

## Summary

Fixed jumping highlights by:
1. Protecting against Vosk resets (decreasing word counts)
2. Only highlighting NEW stable words (not all stable words)
3. Removing temporary gold highlights that were jumping around
4. Incrementing position one word at a time

The highlighting should now follow smoothly as the reader progresses through the passage.
