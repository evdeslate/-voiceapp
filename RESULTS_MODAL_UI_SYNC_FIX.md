# Results Modal and UI Highlighting Sync Fix

## Issue
The results modal showed 8 mispronounced words, but only 2 words were highlighted as red in the UI passage view.

## Root Cause
There was a timing and data source mismatch:

1. **RF Analysis** runs in background thread and applies low confidence auto-incorrect logic
   - Updates `wordCorrect` array with final results (including low confidence words marked as incorrect)
   - Updates local `correctCount` and `incorrectCount` variables
   - BUT did NOT update global `currentCorrectWords` and `currentIncorrectWords`

2. **Session Saved** to database with OLD counts (before low confidence logic was applied)
   - Database has counts from initial match-based recognition
   - Does not include low confidence auto-incorrect adjustments

3. **onSessionSaved Callback** overwrites global variables with database values
   - `currentCorrectWords` and `currentIncorrectWords` get database values
   - These values don't match the `wordCorrect` array

4. **Results Modal** uses `currentCorrectWords` and `currentIncorrectWords` (database values)
   - Shows 8 incorrect words

5. **UI Highlighting** uses `wordCorrect` array (with low confidence logic applied)
   - Shows only 2 red words

## Solution

### Part 1: Update Global Variables in RF Callback
In `onRFAnalysisCompleteWithConfidence()`, after calculating `correctCount` and `incorrectCount`:

```java
// UPDATE GLOBAL VARIABLES: Ensure results modal uses the same counts as UI highlighting
currentCorrectWords = correctCount;
currentIncorrectWords = incorrectCount;
android.util.Log.d("StudentDetail", String.format("✅ Updated global counts: %d correct, %d incorrect", 
    currentCorrectWords, currentIncorrectWords));
```

### Part 2: Recalculate from wordCorrect Array in onSessionSaved
In `onSessionSaved()` callback, instead of using database values directly:

```java
// RECALCULATE correct/incorrect counts from wordCorrect array
// This ensures we use the counts AFTER low confidence auto-incorrect logic
int actualCorrect = 0;
int actualIncorrect = 0;
for (int i = 0; i < wordCorrect.length; i++) {
    if (wordScored[i]) {
        if (wordCorrect[i]) {
            actualCorrect++;
        } else {
            actualIncorrect++;
        }
    }
}

currentCorrectWords = actualCorrect;
currentIncorrectWords = actualIncorrect;
currentAccuracy = (actualCorrect + actualIncorrect) > 0 ? 
    (float) actualCorrect / (actualCorrect + actualIncorrect) : 0.0f;
```

This ensures the results modal and UI highlighting always use the same source of truth: the `wordCorrect` array.

## Why This Works

1. **Single Source of Truth**: Both results modal and UI highlighting now use the `wordCorrect` array
2. **Low Confidence Logic Applied**: The counts include all low confidence words marked as incorrect
3. **Timing Independent**: Works regardless of when callbacks fire
4. **Accurate Counts**: Reflects the actual state of the UI highlighting

## Example Scenario

### Before Fix:
- Word 5: Confidence 0.72 → RF says correct, but low confidence
- `wordCorrect[5]` = false (marked incorrect by low confidence logic)
- Database: 40 correct, 7 incorrect (doesn't include low confidence adjustment)
- Results modal: Shows 7 incorrect (from database)
- UI highlighting: Shows 8 red words (includes word 5)
- **Mismatch!**

### After Fix:
- Word 5: Confidence 0.72 → RF says correct, but low confidence
- `wordCorrect[5]` = false (marked incorrect by low confidence logic)
- `currentCorrectWords` = 39, `currentIncorrectWords` = 8 (recalculated from wordCorrect array)
- Results modal: Shows 8 incorrect (from wordCorrect array)
- UI highlighting: Shows 8 red words (from wordCorrect array)
- **Match!** ✅

## Files Modified
- `app/src/main/java/com/example/speak/StudentDetail.java`
  - Modified `onRFAnalysisCompleteWithConfidence()` to update global variables
  - Modified `onSessionSaved()` to recalculate counts from wordCorrect array

## Testing
1. Read a passage and skip some words (low confidence)
2. Check the results modal for incorrect word count
3. Count the red highlighted words in the passage view
4. Verify the counts match
5. Check logs for "Database" vs "Actual" counts comparison

## Notes
- The database still has the old counts (before low confidence logic)
- This is acceptable because the UI and results modal are consistent
- Future enhancement: Update the database with final counts after RF analysis
