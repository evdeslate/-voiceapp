# Quick Test Guide - Speech Detection Fix

## 1. Rebuild and Install (2 minutes)

```bash
# Windows CMD
gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 2. Start Monitoring (1 minute)

**Option A: PowerShell (with colors)**
```powershell
.\Monitor-SpeechDetection.ps1
```

**Option B: CMD (simple)**
```cmd
monitor_speech_detection.bat
```

## 3. Test Speech Detection (3 minutes)

### Test 1: Basic Detection
1. Open app, select a student
2. Start reading a passage
3. **Look for in logs**:
   ```
   📝 Extracted text from result array: 'a little snail'
   Processing recognized text: 'a little snail'
   Word 0: 'a' vs 'a' - ✅ (perfect match 100%)
   ```

**✅ PASS**: Words are being detected and processed
**❌ FAIL**: See "Empty intermediate result" or no word processing

### Test 2: Mispronunciation Detection
1. Read passage but say "feather" instead of "father"
2. **Look for in logs**:
   ```
   Word X: 'feather' vs 'father' - ❌ (weak match 60%)
   ```

**✅ PASS**: Mispronunciation marked as incorrect
**❌ FAIL**: Mispronunciation marked as correct (✅)

### Test 3: Sequential Highlighting
1. Read passage normally
2. **Watch the screen**: Words should highlight in order
3. **Look for in logs**: Sequential word indices (0, 1, 2, 3...)

**✅ PASS**: Words highlight in order, no jumping
**❌ FAIL**: Highlighting jumps around, skips multiple words

### Test 4: Specific Word ("carry")
1. Find a passage with "carry" or similar word
2. Say the word clearly
3. **Look for in logs**:
   ```
   Word X: 'carry' vs 'carry' - ✅ (perfect match 100%)
   ```

**✅ PASS**: Word detected and matched
**❌ FAIL**: See "Unmatched word: 'carry'" in logs

### Test 5: Latency Check
1. Read words at normal pace
2. **Watch the screen**: Note delay between speaking and highlighting
3. **Expected**: < 0.5 seconds delay

**✅ PASS**: Real-time highlighting
**❌ FAIL**: Noticeable delay (> 1 second)

## 4. Quick Results Check

### All Tests Pass ✅
- Speech detection working
- Mispronunciations detected
- Sequential highlighting
- Low latency
- **Action**: Use the app normally, monitor for issues

### Some Tests Fail ❌
- Check logs for specific error messages
- Note which test failed
- Report findings with log excerpts

## Common Issues and Solutions

### Issue: "Empty intermediate result"
**Cause**: Result array not being extracted
**Check**: Look for "📝 Extracted text from result array" in logs
**Solution**: Verify VoskMFCCRecognizer.java onResult callback

### Issue: Mispronunciations marked as correct
**Cause**: Matching too lenient
**Check**: Look at match scores in logs (should be < 70% for mismatches)
**Solution**: Verify calculateMatchScore and soundsLike methods

### Issue: Scattered highlighting
**Cause**: Look-ahead window too large
**Check**: Look for "⏭️ Skipped word" messages
**Solution**: Verify processRecognizedText look-ahead logic

### Issue: High latency
**Cause**: Processing only in onFinalResult
**Check**: Look for "📝 Intermediate text" during reading
**Solution**: Verify onResult callback is processing words

## Log Interpretation

### Good Logs ✅
```
📝 Extracted text from result array: 'word1 word2'
Processing recognized text: 'word1 word2'
Word 0: 'word1' vs 'word1' - ✅ (perfect match 100%, instant score: 87%)
Word 1: 'word2' vs 'word2' - ✅ (excellent match 95%, instant score: 78%)
```

### Warning Logs ⚠️
```
⚠️ Unmatched word: 'xyz' (best match: 45%, threshold: 72%)
⏭️ Skipped word 5: 'word' (recognized next word first)
```

### Error Logs ❌
```
⚠️ Empty intermediate result after extraction attempts
Error parsing result
```

## Quick Commands

```bash
# Rebuild
gradlew assembleDebug

# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Monitor (PowerShell)
.\Monitor-SpeechDetection.ps1

# Monitor (CMD)
monitor_speech_detection.bat

# Clear logs
adb logcat -c

# Check if device connected
adb devices
```

## Expected Timeline

- Rebuild: 1-2 minutes
- Install: 10-30 seconds
- Testing: 3-5 minutes
- **Total**: ~5-8 minutes

## Success Criteria

1. ✅ Speech detected in real-time
2. ✅ Mispronunciations marked as incorrect
3. ✅ Sequential word highlighting
4. ✅ Low latency (< 0.5s)
5. ✅ All words detected

If all 5 criteria met: **FIX SUCCESSFUL** 🎉

---

**Next**: See SPEECH_DETECTION_AND_ACCURACY_FIX.md for detailed information
