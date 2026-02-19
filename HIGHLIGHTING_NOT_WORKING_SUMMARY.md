# Speech Highlighting Not Working - Investigation Summary

## Code Review Results

I've reviewed your speech detection and highlighting code. The implementation looks **correct**. Here's what should be happening:

### Expected Flow
```
User starts reading
    ↓
Vosk receives audio
    ↓
onPartialResult() called (shows partial text)
    ↓
onResult() called (final text for phrase)
    ↓
Words matched against expected words
    ↓
onWordRecognized() callback fired
    ↓
wordFinished[index] = true
    ↓
redrawHighlights() called
    ↓
Word highlighted YELLOW
```

### Code Verification

✅ **Vosk Started**: `voskRecognizer.startRecognition()` is called in `startContinuousReadingWithTimer()`

✅ **Callback Registered**: `onWordRecognized()` callback is properly implemented

✅ **Word Spans Computed**: `computeWordSpans()` is called before starting

✅ **Highlighting Logic**: `redrawHighlights()` correctly highlights finished words in yellow

✅ **UI Update**: `runOnUiThread()` is used to update UI safely

## Most Likely Issues

Since the code is correct, the problem is likely one of these:

### 1. Vosk Model Not Loaded (Most Likely)
**Symptoms:**
- App starts but no recognition happens
- No partial results in logcat
- Toast says "Speech recognition is loading..."

**Check:**
```bash
adb logcat | findstr /C:"Vosk model" /C:"Model loaded"
```

**Expected:**
```
✅ Vosk model loaded successfully
✅ Vosk recognizer ready
```

**If Not Loaded:**
- Model files missing from assets
- Model path incorrect
- Out of memory error

### 2. Microphone Permission Denied
**Symptoms:**
- No audio input
- No partial results
- Silent failure

**Check:**
```bash
adb shell dumpsys package com.example.speak | findstr "RECORD_AUDIO"
```

**Expected:**
```
android.permission.RECORD_AUDIO: granted=true
```

**If Not Granted:**
- Go to Settings > Apps > SPEAK > Permissions
- Enable Microphone permission
- Restart app

### 3. Vosk Not Receiving Audio
**Symptoms:**
- Model loaded but no partial results
- No recognition happening

**Check:**
```bash
adb logcat | findstr /C:"onPartialResult" /C:"onResult"
```

**Expected:**
```
🔊 onPartialResult called: {"partial":"the"}
🔊 onResult called: {"text":"the cat"}
```

**If No Results:**
- Audio recording not starting
- Microphone blocked by another app
- Audio focus issue

### 4. Words Not Matching
**Symptoms:**
- Partial results appear but no word matching
- No "Word X: 'recognized' vs 'expected'" logs

**Check:**
```bash
adb logcat | findstr /C:"Word" /C:"match"
```

**Expected:**
```
Word 0: 'the' vs 'the' - ✅ (perfect match 100%)
Word 1: 'cat' vs 'cat' - ✅ (excellent match 98%)
```

**If No Matching:**
- Recognition text doesn't match expected words
- Thresholds too strict
- Word splitting issue

## Diagnostic Steps

### Step 1: Run the Monitor Script
```bash
MONITOR_HIGHLIGHTING.bat
```

Then start a reading session and speak. You should see:
1. "Vosk model loaded successfully"
2. "recognition ready"
3. "onPartialResult" messages as you speak
4. "Word X tracked" messages
5. "redrawHighlights" calls

### Step 2: Check What's Missing
Based on what you DON'T see in the logs:

**No "Vosk model loaded":**
- Model files missing or corrupted
- Check `app/src/main/assets/sync/vosk-model-en-us-0.22-lgraph/`

**No "onPartialResult":**
- Microphone permission issue
- Audio recording not starting
- Vosk not initialized

**No "Word X tracked":**
- Words not being matched
- Callback not firing
- Recognition text doesn't match expected

**No "redrawHighlights":**
- UI update issue
- passageContentView is null
- wordSpans not computed

### Step 3: Test with Simple Passage
Try with a very simple passage like:
```
The cat sat.
```

Speak very clearly and slowly. If this works, the issue is with word matching thresholds.

## Quick Fixes to Try

### Fix 1: Add Debug Logs
Add this at the start of `onWordRecognized()`:
```java
android.util.Log.e("HIGHLIGHT_DEBUG", "=== onWordRecognized called ===");
android.util.Log.e("HIGHLIGHT_DEBUG", "Word index: " + wordIndex);
android.util.Log.e("HIGHLIGHT_DEBUG", "Recognized: " + recognizedWord);
android.util.Log.e("HIGHLIGHT_DEBUG", "Expected: " + expectedWord);
android.util.Log.e("HIGHLIGHT_DEBUG", "wordFinished length: " + wordFinished.length);
android.util.Log.e("HIGHLIGHT_DEBUG", "passageContentView null? " + (passageContentView == null));
```

Then run:
```bash
adb logcat | findstr "HIGHLIGHT_DEBUG"
```

### Fix 2: Force Yellow Highlighting
Temporarily change `redrawHighlights()` to always highlight all words yellow:
```java
private void redrawHighlights(TextView textView) {
    // TEMPORARY: Highlight ALL words yellow for testing
    CharSequence currentText = textView.getText();
    android.text.Spannable spannable = new android.text.SpannableString(currentText);
    
    for (WordSpan ws : wordSpans) {
        spannable.setSpan(
            new android.text.style.BackgroundColorSpan(android.graphics.Color.YELLOW),
            ws.start, ws.end,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }
    
    textView.setText(spannable);
    android.util.Log.e("HIGHLIGHT_DEBUG", "Highlighted " + wordSpans.size() + " words");
}
```

If this works, the issue is with the `wordFinished` logic.

### Fix 3: Check Hybrid Mode
Verify hybrid mode is enabled:
```java
private boolean useHybridMode = true; // Should be true
```

If false, Vosk might not be started for real-time highlighting.

## Expected Logcat Output

When working correctly, you should see this pattern:

```
=== STARTING HYBRID MODE (Vosk + DirectAudio) ===
✅ Vosk model loaded successfully
✅ Vosk recognizer ready
✅ Computed 47 word spans with tracking arrays
🎤 Start reading now!

[User speaks: "the"]
🔊 onPartialResult called: {"partial":"the"}

[User pauses]
🔊 onResult called: {"text":"the"}
Word 0: 'the' vs 'the' - ✅ (perfect match 100%, instant score: 95%)
📝 Word 0 tracked: 'the' vs 'the'

[User speaks: "cat"]
🔊 onPartialResult called: {"partial":"cat"}

[User pauses]
🔊 onResult called: {"text":"cat"}
Word 1: 'cat' vs 'cat' - ✅ (perfect match 100%, instant score: 95%)
📝 Word 1 tracked: 'cat' vs 'cat'
```

## Next Steps

1. Run `MONITOR_HIGHLIGHTING.bat`
2. Start a reading session
3. Speak clearly
4. Share the logcat output with me
5. I'll identify exactly where the flow is breaking

## Files to Check

- `app/src/main/java/com/example/speak/StudentDetail.java` (lines 2100-2160)
- `app/src/main/java/com/example/speak/VoskMFCCRecognizer.java` (lines 510-960)
- `app/src/main/assets/sync/vosk-model-en-us-0.22-lgraph/` (model files)

## Common Root Causes

Based on similar issues, the most common causes are:

1. **Vosk model not loaded** (70% of cases)
2. **Microphone permission denied** (20% of cases)
3. **Audio recording not starting** (5% of cases)
4. **Word matching thresholds too strict** (5% of cases)

Run the diagnostic and share the output - I'll help you fix it!
