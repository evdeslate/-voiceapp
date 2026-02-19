# UI Highlight Color Fix

## Issue
After reading session completes and results are ready, the passage text remained highlighted in yellow (in-progress state) instead of showing final green/red colors for correct/incorrect words.

## Root Cause
The `onSessionSaved` callback in Vosk-only mode was calling `invalidate()` and `requestLayout()` to refresh the UI, but was NOT calling `redrawHighlights()` to actually update the word colors from yellow to green/red.

## How redrawHighlights Works
The `redrawHighlights()` method applies colors based on word state:
- **Yellow (#FFF59D)**: Word is finished but not yet scored by Random Forest (`wordFinished[i] = true` but `wordScored[i] = false`)
- **Green (#66BB6A)**: Word is scored and correct (`wordScored[i] = true` and `wordCorrect[i] = true`)
- **Red (#EF5350)**: Word is scored and incorrect (`wordScored[i] = true` and `wordCorrect[i] = false`)
- **Subtle Yellow (#FFF9C4)**: Word is currently being processed (`isProcessingWord = true`)

## Solution
Added `redrawHighlights(passageContentView)` call in the `onSessionSaved` callback before showing the results modal. This ensures the final colors (green/red) are applied after RF analysis completes.

## Code Changes
In `StudentDetail.java`, `onSessionSaved()` method (Vosk-only mode path):

```java
// Before (missing redrawHighlights):
if (passageContentView != null) {
    android.util.Log.d("StudentDetail", "🔄 Final UI refresh before showing results");
    passageContentView.invalidate();
    passageContentView.requestLayout();
}

// After (with redrawHighlights):
if (passageContentView != null) {
    android.util.Log.d("StudentDetail", "🔄 Final UI refresh before showing results");
    android.util.Log.d("StudentDetail", "🎨 Redrawing highlights with final colors (green/red)");
    redrawHighlights(passageContentView);
    passageContentView.invalidate();
    passageContentView.requestLayout();
}
```

## Testing
After this fix:
1. Start a reading session
2. Read the passage (words turn yellow as you read)
3. Wait for RF analysis to complete
4. Session is saved to database
5. **Words should now turn green (correct) or red (incorrect)** before the results modal appears
6. No more yellow highlights after session completes

## Related Files
- `app/src/main/java/com/example/speak/StudentDetail.java`
  - Modified `onSessionSaved()` callback to call `redrawHighlights()`
  - Hybrid mode path already had this fix in the fallback timeout

## Notes
- The hybrid mode path already had `redrawHighlights()` in the DirectAudio timeout fallback
- This fix only affects Vosk-only mode (when `useHybridMode = false`)
- The 2-second delay before showing results gives time for the UI to fully update with final colors
