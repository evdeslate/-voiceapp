# Confidence-Based Sampling Fix

## Issue
There was a discrepancy between the RF analysis results and the final session data:
- RF analysis showed: 44 correct, 3 incorrect (47 total words)
- Final session showed: 41 correct, 6 incorrect (47 total words)
- Logs showed "Word index X out of range" errors during re-analysis

## Root Cause
The confidence-based sampling feature was trying to re-analyze suspicious words AFTER the session was already saved to the database. The re-analysis was calling `VoskMFCCRecognizer.reanalyzeSuspiciousWords()` which relies on `wordTimestamps` data that was empty or not accessible at that point in the callback chain.

## Flow Analysis
1. Recognition completes and `calculateFinalScores()` is called
2. RF analysis runs in background thread
3. `onRFAnalysisCompleteWithConfidence` callback is triggered with results (44 correct, 3 incorrect)
4. Confidence-based sampling tries to re-analyze 3 suspicious words
5. `reanalyzeSuspiciousWords()` fails because `wordTimestamps` is empty (indices out of range)
6. Session is saved with incorrect data somehow showing different counts

## Solution
Disabled confidence-based sampling in both callback methods:
- `onRFAnalysisComplete()` in StudentDetail.java
- `onRFAnalysisCompleteWithConfidence()` in StudentDetail.java

The session is now saved with the RF analysis results directly, which are already accurate. The hybrid approach (text-based + audio-based) provides reliable results without needing additional re-analysis.

## Files Modified
- `app/src/main/java/com/example/speak/StudentDetail.java`
  - Disabled confidence-based sampling in `onRFAnalysisComplete()`
  - Disabled confidence-based sampling in `onRFAnalysisCompleteWithConfidence()`
  - Added comments explaining why it's disabled and what needs to be done to fix it properly

## Future Work
To properly implement confidence-based sampling:
1. Store audio segments with timestamps during recognition
2. Use DirectAudioPronunciationAnalyzer directly for re-analysis (not VoskMFCCRecognizer)
3. Perform re-analysis BEFORE saving the session to database
4. Update the session data with refined results before saving

## Testing
After this fix:
- Session should be saved with RF analysis results (e.g., 44 correct, 3 incorrect)
- No "out of range" errors in logs
- UI should show the same counts as the saved session
- No discrepancy between RF results and final session data
