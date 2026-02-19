# Speech Highlighting Not Working - Diagnostic Guide

## Problem
Words are not being highlighted (yellow) when the user reads during a session.

## Possible Causes

### 1. Vosk Recognizer Not Receiving Audio
**Symptoms:**
- No partial results in logcat
- No "onPartialResult" or "onResult" messages
- No word matching logs

**Check:**
```bash
adb logcat | findstr /C:"VoskMFCCRecognizer" /C:"onPartialResult" /C:"onResult"
```

**Expected Output:**
```
🔊 onPartialResult called: {"partial":"the"}
🔊 onPartialResult called: {"partial":"the cat"}
🔊 onResult called: {"text":"the cat sat"}
```

**If No Output:**
- Microphone permission not granted
- Audio recording not starting
- Vosk model not loaded

### 2. Vosk Model Not Loaded
**Symptoms:**
- Error messages about model loading
- "Model not ready" warnings

**Check:**
```bash
adb logcat | findstr /C:"Vosk model" /C:"Model loaded" /C:"Model not ready"
```

**Expected Output:**
```
✅ Vosk model loaded successfully
✅ Vosk recognizer ready
```

**If Model Not Loaded:**
- Check if vosk-model files exist in assets/sync/
- Verify model path is correct
- Check for OOM errors (model too large)

### 3. Word Matching Not Triggering
**Symptoms:**
- Partial results appear but no word matching logs
- No "Word X: 'recognized' vs 'expected'" messages

**Check:**
```bash
adb logcat | findstr /C:"Word" /C:"match" /C:"recognized"
```

**Expected Output:**
```
Word 0: 'the' vs 'the' - ✅ (perfect match 100%, instant score: 95%)
Word 1: 'cat' vs 'cat' - ✅ (excellent match 98%, instant score: 90%)
```

**If No Matching:**
- onResult callback not being called
- recognizedText is empty
- Word splitting not working

### 4. Callback Not Set Up
**Symptoms:**
- Word matching logs appear but no highlighting
- No "📝 Word X tracked" messages in StudentDetail

**Check:**
```bash
adb logcat | findstr /C:"StudentDetail" /C:"Word" /C:"tracked"
```

**Expected Output:**
```
📝 Word 0 tracked: 'the' vs 'the'
📝 Word 10 tracked: 'jumped' vs 'jumped'
```

**If No Tracking:**
- Callback not registered with VoskMFCCRecognizer
- runOnUiThread not executing
- wordFinished array not being updated

### 5. UI Not Updating
**Symptoms:**
- Word tracking logs appear but UI doesn't change
- wordFinished[i] = true but no yellow highlight

**Check:**
```bash
adb logcat | findstr /C:"redrawHighlights" /C:"wordFinished" /C:"wordSpans"
```

**Expected Output:**
```
✅ Computed 47 word spans with tracking arrays
```

**If UI Not Updating:**
- passageContentView is null
- wordSpans not computed
- redrawHighlights not being called
- TextView not updating

## Diagnostic Steps

### Step 1: Check Microphone Permission
```bash
adb shell dumpsys package com.example.speak | findstr "RECORD_AUDIO"
```

Should show: `android.permission.RECORD_AUDIO: granted=true`

### Step 2: Check Vosk Model Loading
```bash
adb logcat -c
adb logcat | findstr /C:"Vosk" /C:"Model"
```

Start the app and look for:
```
🔄 Loading Vosk model from: vosk-model-en-us-0.22-lgraph
✅ Vosk model loaded successfully
```

### Step 3: Start Reading Session and Monitor
```bash
adb logcat | findstr /C:"onPartialResult" /C:"onResult" /C:"Word" /C:"tracked"
```

Start reading and speak clearly. You should see:
1. Partial results as you speak
2. Final results when you pause
3. Word matching logs
4. Word tracking logs

### Step 4: Check Word Spans
```bash
adb logcat | findstr /C:"Computed" /C:"word spans"
```

Should show:
```
✅ Computed 47 word spans with tracking arrays
```

### Step 5: Check Highlighting
```bash
adb logcat | findstr /C:"redrawHighlights" /C:"wordFinished"
```

Should see redrawHighlights being called after each word.

## Common Issues and Fixes

### Issue 1: No Audio Input
**Cause:** Microphone permission denied or audio recording not starting

**Fix:**
1. Check app permissions in Settings
2. Grant RECORD_AUDIO permission
3. Restart app

### Issue 2: Vosk Model Not Found
**Cause:** Model files missing or path incorrect

**Fix:**
1. Verify files exist in `app/src/main/assets/sync/vosk-model-en-us-0.22-lgraph/`
2. Check model path in code matches folder name
3. Rebuild app to include assets

### Issue 3: Words Not Matching
**Cause:** Thresholds too strict or recognition poor

**Fix:**
1. Lower acceptance thresholds temporarily for testing
2. Speak more clearly and slowly
3. Check if recognized text matches expected text

### Issue 4: Callback Not Firing
**Cause:** Callback not registered or hybrid mode issue

**Fix:**
1. Check if `useHybridMode = true` in StudentDetail
2. Verify callback is set in `startContinuousReadingWithTimer`
3. Check if `voskRecognizer.startRecognition()` is called

### Issue 5: UI Not Updating
**Cause:** TextView reference null or wordSpans not computed

**Fix:**
1. Check if `passageContentView` is not null
2. Verify `computeWordSpans()` is called before starting
3. Check if `redrawHighlights()` is being called

## Quick Test

Run this command and start reading:
```bash
adb logcat -c && adb logcat | findstr /C:"onPartialResult" /C:"Word" /C:"tracked" /C:"redrawHighlights"
```

**Expected Flow:**
```
🔊 onPartialResult called: {"partial":"the"}
Word 0: 'the' vs 'the' - ✅ (perfect match 100%)
📝 Word 0 tracked: 'the' vs 'the'
[redrawHighlights called]

🔊 onPartialResult called: {"partial":"the cat"}
Word 1: 'cat' vs 'cat' - ✅ (perfect match 100%)
📝 Word 1 tracked: 'cat' vs 'cat'
[redrawHighlights called]
```

## Code Checkpoints

### 1. Vosk Recognizer Started?
Check in `startContinuousReadingWithTimer()`:
```java
voskRecognizer.startRecognition(expectedWords, callback);
```

### 2. Callback Registered?
Check in `startContinuousReadingWithTimer()`:
```java
new VoskMFCCRecognizer.RecognitionCallback() {
    @Override
    public void onWordRecognized(...) {
        // Should be implemented
    }
}
```

### 3. Word Spans Computed?
Check before starting:
```java
computeWordSpans(currentPassageText);
```

### 4. Arrays Initialized?
Check in `computeWordSpans()`:
```java
wordCorrect = new boolean[n];
wordScored = new boolean[n];
wordFinished = new boolean[n];
```

### 5. Highlighting Called?
Check in `onWordRecognized()`:
```java
wordFinished[wordIndex] = true;
redrawHighlights(passageContentView);
```

## Most Likely Issues

Based on the code review, the most likely issues are:

1. **Vosk not receiving audio** - Check microphone permission
2. **Vosk model not loaded** - Check model files in assets
3. **Callback not firing** - Check if recognizer is started
4. **passageContentView is null** - Check TextView reference

## Next Steps

1. Run the diagnostic commands above
2. Share the logcat output
3. Identify which stage is failing
4. Apply the appropriate fix

## Emergency Fix

If nothing works, try this minimal test:

1. Add this log in `onWordRecognized()`:
```java
android.util.Log.e("HIGHLIGHT_TEST", "Word " + wordIndex + " recognized!");
```

2. Add this log in `redrawHighlights()`:
```java
android.util.Log.e("HIGHLIGHT_TEST", "Redrawing highlights for " + wordSpans.size() + " words");
```

3. Run:
```bash
adb logcat | findstr "HIGHLIGHT_TEST"
```

4. Start reading and see which logs appear
