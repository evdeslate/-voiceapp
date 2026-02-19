# Partial Highlighting Fix - Word Matching

## Problem Found

The logs showed Vosk detecting "i want" but no yellow highlighting appeared because:

1. **Old logic assumed sequential order** - Expected words to come in order (word 0, 1, 2...)
2. **Didn't search for words** - Just used word count to determine position
3. **Failed with grammar constraints** - When Vosk skips words, counting breaks

Example:
- Vosk says "i want" (2 words)
- Old code: Highlight word at index 0 (wrong - that's "A")
- Should: Search for "i" and "want" in passage and highlight those

## Fix Applied

Rewrote `onPartialUpdate()` to actually search for recognized words in the passage:

### Before (Broken)
```java
// Assumed words come in order
int finishedIndex = currentWords.length - 2;
wordFinished[finishedIndex] = true; // Wrong index!
```

### After (Fixed)
```java
// Search for each recognized word in the passage
for (String recognizedWord : currentWords) {
    for (int j = 0; j < wordSpans.size(); j++) {
        String expectedWord = getWordAtIndex(j);
        if (recognizedWord.matches(expectedWord)) {
            highlightCurrentWord(textView, j); // Correct index!
        }
    }
}
```

## What This Does

1. **Takes partial result** from Vosk (e.g., "i want")
2. **Splits into words** ["i", "want"]
3. **Searches passage** for each word
4. **Highlights matching words** with yellow
5. **Skips already processed words** to avoid re-highlighting

## Expected Behavior

When you say "I want to have":
- "I" → Finds word ~10 in passage → Yellow highlight
- "want" → Finds word ~11 in passage → Yellow highlight  
- "to" → Finds word ~12 in passage → Yellow highlight
- "have" → Finds word ~13 in passage → Yellow highlight

## Files Modified

- `app/src/main/java/com/example/speak/StudentDetail.java` (line ~2693-2745)
  - Rewrote `onPartialUpdate()` to search for words instead of assuming order

## Next Steps

1. **Rebuild the app**
2. **Test with speech** - Say words from the passage
3. **Watch for yellow highlighting** - Should appear on correct words
4. **Verify final colors** - Green/red should replace yellow after recognition

## Summary

Fixed partial highlighting to actually search for recognized words in the passage text instead of assuming they come in sequential order. This handles cases where Vosk skips words or recognizes out of order due to grammar constraints.
