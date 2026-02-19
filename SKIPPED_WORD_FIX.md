# Skipped Word Handling Fix

## Issue
When a word was skipped (not recognized), it was being logged correctly as "⏭️ Skipped word" and marked as incorrect in the match-based scoring, but the RF analysis was using incorrect data due to a duplicate entry in the `matchBasedCorrectness` list.

### Example from Logs:
```
⚠️  Unmatched word: 'walked' (possible insertion/noise)
⏭️  Skipped word 1: 'woke' (not recognized)
```

Yet "woke" was still being marked as correct in the final results.

## Root Cause

There was a duplicate call to `matchBasedCorrectness.add(isCorrect)` in the word matching code:

```java
// First call (correct)
matchBasedCorrectness.add(isCorrect);

// ... some code ...

// Second call (DUPLICATE - causing index mismatch)
matchBasedCorrectness.add(isCorrect);
```

This caused the `matchBasedCorrectness` list to have more entries than expected, leading to an index mismatch when the RF analysis tried to use it as a fallback.

### How It Caused the Issue:

1. **Word 0**: "Maria" - recognized correctly
   - `matchBasedCorrectness[0]` = true
   - `matchBasedCorrectness[1]` = true (DUPLICATE)

2. **Word 1**: "woke" - SKIPPED
   - `matchBasedCorrectness[2]` = false (correct)

3. **Word 2**: "up" - recognized correctly
   - `matchBasedCorrectness[3]` = true
   - `matchBasedCorrectness[4]` = true (DUPLICATE)

4. **RF Analysis** tries to get fallback for word 1 ("woke"):
   - Looks up `matchBasedCorrectness[1]`
   - Gets `true` (the duplicate from word 0)
   - **Incorrectly marks "woke" as correct!**

## Solution

Removed the duplicate `matchBasedCorrectness.add(isCorrect)` call:

```java
// Before (with duplicate)
pronunciationScores.add(pronunciationScore);
matchBasedCorrectness.add(isCorrect);  // First call
// ... code ...
matchBasedCorrectness.add(isCorrect);  // DUPLICATE - REMOVED

// After (no duplicate)
pronunciationScores.add(pronunciationScore);
matchBasedCorrectness.add(isCorrect);  // Single call
// ... code ...
// No duplicate
```

## How Skipped Words Are Handled (After Fix)

### 1. Detection
When a recognized word doesn't match the current expected word well enough, the system looks ahead to find a better match.

### 2. Marking Skipped Words
For each word between the current position and the matched position:
```java
while (currentWordIndex < bestMatchIndex) {
    String skippedWord = expectedWords[currentWordIndex];
    Log.d(TAG, String.format("⏭️  Skipped word %d: '%s' (not recognized)", 
        currentWordIndex, skippedWord));
    
    pronunciationScores.add(0.2f); // Very low score (20%)
    incorrectWordsCount++;  // Count as incorrect
    matchBasedCorrectness.add(false); // Mark as incorrect
    
    callback.onWordRecognized("", skippedWord, currentWordIndex, 0.2f, false);
    currentWordIndex++;
}
```

### 3. RF Analysis Fallback
When RF analysis can't process a word (e.g., no audio segment), it uses the match-based result:
```java
boolean fallback = i < matchBasedCorrectness.size() ? matchBasedCorrectness.get(i) : false;
rfWordCorrectness.add(fallback);
```

With the duplicate removed, the index `i` now correctly maps to the right entry in `matchBasedCorrectness`.

## Example Scenario (After Fix)

### Passage: "Maria woke up early"
### Student reads: "Maria walked up early"

**Processing:**
1. "Maria" matches word 0 → ✅ correct
   - `matchBasedCorrectness[0]` = true

2. "walked" doesn't match word 1 ("woke") well enough
   - System looks ahead and finds "walked" doesn't match any upcoming words
   - Logs: "⚠️ Unmatched word: 'walked' (possible insertion/noise)"
   - No entry added to `matchBasedCorrectness`

3. System realizes word 1 ("woke") was skipped
   - Logs: "⏭️ Skipped word 1: 'woke' (not recognized)"
   - `matchBasedCorrectness[1]` = false ❌
   - `pronunciationScores[1]` = 0.2 (20%)
   - `incorrectWordsCount++`

4. "up" matches word 2 → ✅ correct
   - `matchBasedCorrectness[2]` = true

5. "early" matches word 3 → ✅ correct
   - `matchBasedCorrectness[3]` = true

**RF Analysis:**
- Word 0: Uses RF analysis or fallback to `matchBasedCorrectness[0]` = true ✅
- Word 1: Uses RF analysis or fallback to `matchBasedCorrectness[1]` = false ❌ (CORRECT!)
- Word 2: Uses RF analysis or fallback to `matchBasedCorrectness[2]` = true ✅
- Word 3: Uses RF analysis or fallback to `matchBasedCorrectness[3]` = true ✅

**Final Results:**
- Correct: 3 words (Maria, up, early)
- Incorrect: 1 word (woke - skipped)
- Accuracy: 75%

## Files Modified
- `app/src/main/java/com/example/speak/VoskMFCCRecognizer.java`
  - Removed duplicate `matchBasedCorrectness.add(isCorrect)` call

## Testing
1. Read a passage and intentionally skip a word
2. Check logs for "⏭️ Skipped word" message
3. Verify the skipped word is highlighted in RED
4. Check the results modal shows the correct number of incorrect words
5. Verify the accuracy score reflects the skipped word as incorrect

## Related Issues
- This fix also resolves potential index mismatches in the RF analysis
- Ensures the `matchBasedCorrectness` list has exactly one entry per expected word
- Improves reliability of the fallback mechanism when RF analysis fails
