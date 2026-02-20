# Vosk Not Recognizing Words - Troubleshooting Guide

## Problem

Vosk is detecting audio (showing high confidence like 252.46) but returning empty text:
```
{"alternatives" : [{"confidence" : 252.461975,"text" : ""}]}
```

This means:
- ✅ Vosk model is loaded correctly
- ✅ Audio is being recorded
- ✅ Vosk is detecting speech/audio
- ❌ Vosk is NOT recognizing any words

## Root Causes

### 1. Microphone Distance (Most Common)
The speaker is too far from the device microphone.

**Solution:**
- Hold device 6-12 inches from mouth
- Speak directly toward the microphone
- Don't cover the microphone with your hand

### 2. Speaking Volume
The speaker is speaking too quietly.

**Solution:**
- Speak at normal conversational volume
- Don't whisper
- Don't shout (can distort audio)

### 3. Background Noise
Too much ambient noise interfering with speech.

**Solution:**
- Move to a quieter environment
- Close windows/doors
- Turn off fans, TV, music
- Reduce room echo (soft furnishings help)

### 4. Audio Quality
Poor microphone quality or audio processing issues.

**Solution:**
- Test microphone in other apps (voice recorder)
- Clean microphone opening
- Check if device case is blocking microphone
- Try a different device if available

### 5. Speaking Clarity
Mumbling, speaking too fast, or unclear pronunciation.

**Solution:**
- Speak clearly and distinctly
- Pronounce each word separately
- Speak at moderate pace (not too fast)
- Enunciate consonants

### 6. Microphone Permission
App doesn't have proper microphone access.

**Solution:**
- Go to Settings → Apps → SPEAK → Permissions
- Ensure "Microphone" is allowed
- Restart the app after granting permission

## Quick Test

### Test 1: Check Microphone
1. Open device's voice recorder app
2. Record yourself saying "once upon a time"
3. Play it back
4. If you can't hear yourself clearly, the microphone has issues

### Test 2: Check Volume
1. In SPEAK app, start reading
2. Watch logcat for "Audio level" messages
3. Should see values between 0.1 and 0.5 for normal speech
4. If values are < 0.05, speak louder

```powershell
# Monitor audio levels
adb logcat -s VoskMFCCRecognizer:D | Select-String "Audio level"
```

### Test 3: Check Recognition
1. Speak very clearly: "ONCE UPON A TIME"
2. Watch logcat for partial results
3. Should see: `✅ Extracted partial from json.partial: 'once upon a time'`
4. If you see empty partials, it's an audio quality issue

```powershell
# Monitor recognition
adb logcat -s VoskMFCCRecognizer:D | Select-String "partial|Extracted"
```

## Diagnostic Logs

### Good Logs (Working)
```
VoskMFCCRecognizer: 🔊 onPartialResult called: {"partial":"once upon"}
VoskMFCCRecognizer: ✅ Extracted partial from json.partial: 'once upon'
VoskMFCCRecognizer: 🔊 onResult called: {"text":"once upon a time"}
VoskMFCCRecognizer: ✅ Extracted text from json.text: 'once upon a time'
```

### Bad Logs (Not Working - Current Issue)
```
VoskMFCCRecognizer: 🔊 onPartialResult called: {"partial":""}
VoskMFCCRecognizer: ⚠️ Empty partial result
VoskMFCCRecognizer: 🔊 onFinalResult called: {"alternatives":[{"confidence":252.46,"text":""}]}
VoskMFCCRecognizer: ❌ FAILED TO EXTRACT TEXT FROM FINAL RESULT
VoskMFCCRecognizer: ⚠️⚠️⚠️ VOSK DETECTED AUDIO BUT RECOGNIZED NO WORDS
VoskMFCCRecognizer:    SOLUTION: Speak louder and closer to the microphone
```

## Step-by-Step Fix

### Step 1: Verify Microphone Works
```powershell
# Check if microphone is recording
adb logcat -s VoskMFCCRecognizer:D | Select-String "Audio level"

# You should see:
# Audio level: 0.234 (SPEECH)
# Audio level: 0.189 (SPEECH)
```

If you see NO audio level messages:
- Microphone permission not granted
- Microphone hardware issue
- App not starting recording

### Step 2: Improve Audio Quality
1. Move to quiet room
2. Hold device 8 inches from mouth
3. Speak clearly at normal volume
4. Try again

### Step 3: Test with Simple Words
1. Start reading
2. Say slowly and clearly: "ONCE... UPON... A... TIME"
3. Pause between words
4. Watch for partial results in logs

### Step 4: Check Vosk Model
```powershell
# Verify model loaded
adb logcat -s SpeakApplication:D | Select-String "Vosk model"

# Should see:
# ✅ Vosk model loaded successfully
```

If model not loaded:
- Wait 15 seconds after opening app
- See SPEECH_RECOGNITION_TROUBLESHOOTING.md

## Common Scenarios

### Scenario 1: Works on One Device, Not Another
**Cause:** Different microphone quality
**Solution:** Use device with better microphone, or use external microphone

### Scenario 2: Works Sometimes, Not Always
**Cause:** Inconsistent speaking volume or distance
**Solution:** Maintain consistent distance and volume

### Scenario 3: Works in Quiet Room, Not in Classroom
**Cause:** Background noise
**Solution:** Use in quieter environment, or wait for noise reduction improvements

### Scenario 4: Worked Before, Doesn't Work Now
**Cause:** Microphone permission revoked, or hardware issue
**Solution:** Check permissions, restart device

## Advanced Diagnostics

### Check Audio Recording
```powershell
# Monitor audio recording
adb logcat -s VoskMFCCRecognizer:D | Select-String "Audio recording|Audio level"
```

Expected output:
```
✅ Audio recording started for MFCC
Audio level: 0.234 (SPEECH)
Audio level: 0.189 (SPEECH)
Audio level: 0.312 (SPEECH)
```

### Check Vosk Processing
```powershell
# Monitor Vosk recognition
adb logcat -s VoskMFCCRecognizer:D | Select-String "onPartialResult|onResult|onFinalResult"
```

Expected output:
```
🔊 onPartialResult called: {"partial":"once"}
🔊 onPartialResult called: {"partial":"once upon"}
🔊 onResult called: {"text":"once upon a time"}
```

### Check for Errors
```powershell
# Check for any errors
adb logcat -s VoskMFCCRecognizer:E
```

## User Instructions

When this issue occurs, tell the user:

1. **Speak Louder** - Use normal conversational volume
2. **Get Closer** - Hold device 6-12 inches from mouth
3. **Speak Clearly** - Pronounce each word distinctly
4. **Reduce Noise** - Move to quieter location
5. **Check Microphone** - Ensure it's not blocked or covered

## Technical Notes

### Why High Confidence with Empty Text?

Vosk's confidence score measures "how sure it is that it detected speech", not "how sure it is about the words". A high confidence with empty text means:
- Vosk detected audio patterns that look like speech
- But couldn't match them to any words in its vocabulary
- Usually due to poor audio quality or volume

### Audio Processing Pipeline

1. Microphone captures audio
2. AudioRecord provides raw PCM data
3. Audio denoiser reduces background noise
4. AGC normalizes volume
5. Vosk processes audio
6. Vosk returns recognized text

If step 5 fails (Vosk can't recognize words), you get empty text.

## Prevention

To avoid this issue:
- Always test microphone before starting
- Ensure quiet environment
- Maintain consistent speaking volume
- Hold device at proper distance
- Speak clearly and at moderate pace

## Still Not Working?

If none of the above helps:
1. Test microphone in other apps
2. Try a different device
3. Check if Vosk model is corrupted (reinstall app)
4. Capture full logs and report issue

```powershell
# Capture full diagnostic logs
adb logcat -s VoskMFCCRecognizer:D SpeakApplication:D AudioRecord:E > vosk-diagnostic.txt
```
