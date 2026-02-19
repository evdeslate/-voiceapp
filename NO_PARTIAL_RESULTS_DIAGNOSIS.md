# No Partial Results / No Yellow Highlighting - Diagnosis

## The Problem

Your logs show:
```
D VoskMFCCRecognizer: 🔊 onPartialResult called: {
D VoskMFCCRecognizer:   "partial" : ""
D VoskMFCCRecognizer: 📝 Partial text: ''
```

Vosk is returning **empty partial results**, which means:
1. No speech is being detected in real-time
2. No yellow highlighting can occur (needs partial results)
3. Words only get recognized in batches (intermediate/final results)

## Root Causes

### 1. Audio Level Too Low

From your earlier logs:
```
Audio level: 0.001 (SILENT)
Audio level: 0.012 (QUIET)
Audio level: 0.016 (QUIET)
```

The audio levels are classified as SILENT or QUIET, which means:
- Microphone volume is too low
- User is speaking too softly
- Microphone is not picking up speech properly

**Thresholds:**
- SILENT: < 0.01 (1%)
- QUIET: 0.01 - 0.05 (1-5%)
- SPEECH: 0.05 - 0.3 (5-30%)
- LOUD: > 0.3 (30%+)

### 2. Partial Results Highlighting is Disabled

Even if Vosk detected speech, the yellow highlighting is intentionally disabled:

```java
// In StudentDetail.java onPartialResult()
// DISABLED: Do not highlight words from partial results
// Partial results can be inaccurate and cause premature green highlighting
// Only show final colors after word is fully recognized via onWordRecognized
// This prevents false positives like "sing-ging" being marked correct

// runOnUiThread(() -> {
//     onPartialUpdate(partial, passageContentView);
// });
```

This was disabled to prevent false positives, but it means NO yellow highlighting at all.

### 3. Grammar-Constrained Recognition

Vosk uses grammar-constrained recognition which:
- Only recognizes words from the expected passage
- May be more conservative about partial results
- Waits for more confident matches before outputting

## Why This Happens

### Audio Detection Flow

1. **Microphone captures audio** → AudioRecord
2. **Audio level calculated** → RMS (root mean square)
3. **If level > threshold** → Vosk processes audio
4. **If level < threshold** → Vosk ignores (treats as silence)
5. **Vosk outputs partial results** → Only if speech detected
6. **Partial results trigger highlighting** → Currently disabled

### Current State

```
Microphone → QUIET audio (0.01-0.05) → Vosk ignores → No partial results → No highlighting
```

## Solutions

### Solution 1: Increase Microphone Volume (USER ACTION)

The user needs to:
1. Speak louder and closer to the microphone
2. Check device microphone settings
3. Ensure microphone is not blocked/covered
4. Test in a quieter environment

### Solution 2: Lower Audio Detection Threshold (CODE CHANGE)

Vosk may have an internal threshold for speech detection. We can try:

```java
// In VoskMFCCRecognizer.java startRecognition()
recognizer.setMaxAlternatives(1);
recognizer.setWords(true);
recognizer.setPartialWords(true); // Enable partial word results
```

### Solution 3: Enable Partial Result Highlighting (CODE CHANGE)

Re-enable yellow highlighting for partial results:

```java
// In StudentDetail.java onPartialResult()
@Override
public void onPartialResult(String partial) {
    if (!partial.isEmpty() && callback != null) {
        runOnUiThread(() -> {
            // Show yellow highlighting for current word
            highlightCurrentWord(partial);
        });
    }
}
```

### Solution 4: Use AGC (Automatic Gain Control)

The code already has AGC, but it may need tuning:

```java
// In AudioDenoiser.java
public short[] applyAGC(short[] audioData) {
    // Increase target RMS for louder output
    float targetRMS = 0.15f; // Increase from current value
    // ... rest of AGC code
}
```

## Recommended Immediate Actions

### For User:
1. **Speak louder** - Audio levels show QUIET/SILENT
2. **Get closer to microphone** - Improve audio capture
3. **Check microphone permissions** - Ensure app has access
4. **Test microphone** - Use device's voice recorder to verify it works

### For Developer:
1. **Enable partial highlighting** - Uncomment the code in onPartialResult()
2. **Add AGC boost** - Increase audio levels automatically
3. **Log Vosk configuration** - Check if partial results are enabled
4. **Test with louder audio** - Verify system works with proper audio levels

## Testing

### Check Audio Levels
```bash
adb logcat -s VoskMFCCRecognizer:D | findstr "Audio level"
```

Expected output for speech:
```
Audio level: 0.080 (SPEECH)
Audio level: 0.120 (SPEECH)
Audio level: 0.250 (SPEECH)
```

### Check Partial Results
```bash
adb logcat -s VoskMFCCRecognizer:D | findstr "Partial"
```

Expected output when working:
```
📝 Partial text: 'maria'
📝 Partial text: 'maria woke'
📝 Partial text: 'maria woke up'
```

## Why Yellow Highlighting Was Disabled

The comment explains:
> "Partial results can be inaccurate and cause premature green highlighting"
> "This prevents false positives like 'sing-ging' being marked correct"

This was done because:
1. Partial results change frequently as Vosk refines recognition
2. A word might show as correct (green) then change to incorrect (red)
3. This creates a confusing user experience
4. Better to wait for final recognition before showing colors

However, this means **no real-time feedback** during reading, which may be desired for user experience.

## Trade-offs

### With Partial Highlighting (Yellow)
- ✅ Real-time feedback - user sees progress
- ✅ More engaging experience
- ❌ May show incorrect words temporarily
- ❌ Colors may flicker as recognition refines

### Without Partial Highlighting (Current)
- ✅ Only shows final, accurate colors
- ✅ No flickering or confusion
- ❌ No real-time feedback
- ❌ User doesn't know if system is working

## Summary

The main issue is **audio levels are too low** (SILENT/QUIET), so Vosk isn't detecting speech and returning empty partial results. Even if it did, partial highlighting is disabled to prevent false positives.

**Immediate fix:** User needs to speak louder and closer to the microphone.

**Code fix:** Re-enable partial highlighting if real-time feedback is desired, accepting the trade-off of potentially inaccurate temporary colors.
