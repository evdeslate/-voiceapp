# Build and Test - Robust Word Detection

## Quick Build & Install

```cmd
cd C:\Users\Elizha\AndroidStudioProjects\SPEAK
gradlew clean assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Start Monitoring

Open PowerShell and run:
```powershell
.\Test-RobustDetection.ps1
```

This will show:
- ⏱ **Cyan**: Watchdog started for a word
- ⏰ **Red**: Word timed out (auto-advanced)
- **Yellow**: Phonetic validation results
- 🚫 **Magenta**: Vosk false-positive rejected
- **Green**: Audio preprocessor activity
- **Gray**: Partial results (display-only)

## Test Scenarios

### 1. Normal Reading (Baseline)
**Steps**:
1. Select a student and passage
2. Read clearly at normal pace
3. Complete the passage

**Expected**:
- Words highlight immediately as you read
- No timeouts
- Smooth progression

**Monitor for**:
```
⏱ Watching word 0 'the' (timeout: 3000ms)
📝 Word 0 'the' → heard 'the' | vosk=true | phonetic=PhoneticMatch(...) | final=true
⏱ Watching word 1 'cat' (timeout: 3000ms)
```

---

### 2. Mumbled Word (Timeout Test)
**Steps**:
1. Start reading
2. When you reach a complex word (e.g., "subterranean"), mumble it unclearly
3. Wait and observe

**Expected**:
- After 5 seconds, word turns RED
- Reading automatically advances to next word
- No freeze or stall

**Monitor for**:
```
⏱ Watching word 15 'subterranean' (timeout: 5000ms)
⏰ Timeout: word 15 'subterranean' (waited 5000ms)
⏱ Watching word 16 'passage' (timeout: 3000ms)
```

---

### 3. Skipped Word (Auto-Advance Test)
**Steps**:
1. Start reading
2. Intentionally skip a word (don't say it at all)
3. Continue reading the next word

**Expected**:
- After 3 seconds, skipped word turns RED
- Reading advances automatically
- Next word is recognized normally

**Monitor for**:
```
⏱ Watching word 5 'jumped' (timeout: 3000ms)
⏰ Timeout: word 5 'jumped' (waited 3000ms)
⏱ Watching word 6 'over' (timeout: 3000ms)
📝 Word 6 'over' → heard 'over' | vosk=true | phonetic=... | final=true
```

---

### 4. Mispronunciation Detection (Phonetic Test)
**Steps**:
1. Start reading
2. Intentionally mispronounce a word:
   - Say "feyther" instead of "father"
   - Say "singin" instead of "singing"
   - Say "walkin" instead of "walking"
3. Observe the result

**Expected**:
- Word marked as incorrect if similarity < 0.75
- Phonetic validation catches the mispronunciation

**Monitor for**:
```
📝 Word 10 'father' → heard 'feyther' | vosk=true | phonetic=PhoneticMatch('feyther' vs 'father': ... sim=0.65 close=true) | final=false
🚫 Vosk false-positive rejected: heard 'feyther' for 'father' (sim=0.65)
```

---

### 5. Noisy Environment (Preprocessing Test)
**Steps**:
1. Play background noise (fan, music, or classroom sounds)
2. Start reading
3. Read at normal volume

**Expected**:
- Recognition still works
- Noise gate filters background before Vosk
- No significant degradation in accuracy

**Monitor for**:
```
AudioPreProcessor init: sr=16000, hpAlpha=0.9950, lpAlpha=0.0050
Audio level: 0.015 (QUIET)  ← Background noise filtered
Audio level: 0.120 (SPEECH) ← Your voice detected
```

---

### 6. Rapid Reading (Stress Test)
**Steps**:
1. Start reading
2. Read as fast as you can
3. Don't pause between words

**Expected**:
- All words recognized
- No timeouts (words confirmed before timeout)
- Smooth highlighting

**Monitor for**:
```
⏱ Watching word 0 'the' (timeout: 3000ms)
📝 Word 0 'the' → heard 'the' | ... | final=true
⏱ Watching word 1 'quick' (timeout: 3000ms)
📝 Word 1 'quick' → heard 'quick' | ... | final=true
```

---

## Success Criteria

✅ **Normal reading**: Words highlight immediately, no delays
✅ **Mumbled words**: Auto-advance after 3-5 seconds, marked incorrect
✅ **Skipped words**: Auto-advance after 3 seconds, marked incorrect
✅ **Mispronunciations**: Caught by phonetic validation (similarity < 0.75)
✅ **Noisy environment**: Recognition still works with noise filtering
✅ **Rapid reading**: All words recognized, no timeouts

## Troubleshooting

### Issue: Words not advancing at all
**Check**:
- Is watchdog initialized? Look for "⏱ Watching word" in logs
- Is Vosk model loaded? Check for "Vosk model ready" message

**Fix**: Restart app, ensure model loads successfully

---

### Issue: Too many timeouts (words timing out when spoken clearly)
**Cause**: Timeout too short for reading speed

**Fix**: Increase timeout values in `WordTimeoutWatchdog.java`:
```java
private static final int NORMAL_WORD_TIMEOUT_MS  = 4000;  // was 3000
private static final int COMPLEX_WORD_TIMEOUT_MS = 6000;  // was 5000
```

---

### Issue: Mispronunciations marked as correct
**Cause**: Phonetic similarity threshold too low

**Fix**: Increase threshold in `StudentDetail.java`:
```java
} else if (voskSaysCorrect && phonetic.similarity >= 0.80f) {  // was 0.75f
```

---

### Issue: Correct words marked as incorrect
**Cause**: Phonetic similarity threshold too high

**Fix**: Decrease threshold in `StudentDetail.java`:
```java
} else if (voskSaysCorrect && phonetic.similarity >= 0.70f) {  // was 0.75f
```

---

## Monitoring Commands

### Full monitoring (all components):
```powershell
.\Test-RobustDetection.ps1
```

### Watchdog only:
```cmd
adb logcat -v time | findstr /C:"WordTimeoutWatchdog"
```

### Phonetic validation only:
```cmd
adb logcat -v time | findstr /C:"PhoneticMatch" /C:"false-positive"
```

### Audio preprocessing only:
```cmd
adb logcat -v time | findstr /C:"AudioPreProcessor"
```

### JSON extraction (if still having issues):
```powershell
.\Monitor-VoskJSON.ps1
```

## Next Steps After Testing

1. **Collect feedback** from actual students/teachers
2. **Tune parameters** based on real-world usage:
   - Timeout values
   - Phonetic similarity threshold
   - Noise gate threshold
3. **Monitor logs** in production to identify edge cases
4. **Iterate** on the decision logic if needed

## Files to Review

- `ROBUST_WORD_DETECTION_IMPLEMENTATION.md` - Full architecture documentation
- `Test-RobustDetection.ps1` - Monitoring script
- `JSON_EXTRACTION_FIX.md` - JSON extraction improvements
