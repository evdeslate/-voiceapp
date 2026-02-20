# Logcat Quick Reference for Pronunciation Debugging

## Quick Filters for Android Studio Logcat

### See All Pronunciation Logs
```
tag:VoskMFCCRecognizer
```

### See Only Word-by-Word Pronunciation
```
tag:VoskMFCCRecognizer 🎤 WORD
```

### See Only Mispronunciation Overrides
```
tag:VoskMFCCRecognizer OVERRIDE
```

### See Only Final Analysis
```
tag:VoskMFCCRecognizer HYBRID ANALYSIS
```

### See Only RF Model Results
```
tag:VoskMFCCRecognizer AUDIO-ONLY
```

## What Each Log Means

| Log Message | What It Means | Action Needed |
|------------|---------------|---------------|
| `🎤 WORD #X PRONUNCIATION:` | Start of word analysis | Check what user said vs expected |
| `👂 User said: 'pader'` | What Vosk heard | This is the actual pronunciation |
| `📖 Expected word: 'father'` | What should be said | Compare with what user said |
| `🎯 Match quality: 65%` | How similar they are | <70% = likely mispronounced |
| `✅ Result: CORRECT ✅` | Word marked correct | Green checkmark in UI |
| `✅ Result: INCORRECT ❌` | Word marked incorrect | Red X in UI |
| `🚫 OVERRIDE:` | Filipino pattern detected | f→p, v→b, or th→d found |
| `🔀 HYBRID ANALYSIS:` | Final combined results | Shows RF + text matching |
| `ONNX Ready: ✅ YES` | RF model loaded | Audio analysis available |
| `ONNX Ready: ❌ NO` | RF model not loaded | Only text matching available |

## Common Scenarios

### Scenario 1: User Says "pader" for "father"
**Expected Logs:**
```
🎤 WORD #5 PRONUNCIATION:
   👂 User said:     'pader'
   📖 Expected word: 'father'
   🎯 Match quality: 65% (acceptable)
   ✅ Result:        INCORRECT ❌ (55% score)
```
**Later:**
```
🚫 PATTERN OVERRIDE: f→p detected in 'pader' → 'father' forced INCORRECT
```
**Result:** ✅ Correctly marked as incorrect

### Scenario 2: User Says "father" Correctly
**Expected Logs:**
```
🎤 WORD #5 PRONUNCIATION:
   👂 User said:     'father'
   📖 Expected word: 'father'
   🎯 Match quality: 100% (perfect)
   ✅ Result:        CORRECT ✅ (95% score)
```
**Result:** ✅ Correctly marked as correct

### Scenario 3: User Says "hab" for "have"
**Expected Logs:**
```
🎤 WORD #12 PRONUNCIATION:
   👂 User said:     'hab'
   📖 Expected word: 'have'
   🎯 Match quality: 70% (acceptable)
   ✅ Result:        INCORRECT ❌ (60% score)
```
**Later:**
```
🚫 PATTERN OVERRIDE: v→b detected in 'hab' → 'have' forced INCORRECT
```
**Result:** ✅ Correctly marked as incorrect

### Scenario 4: User Says "de" for "the"
**Expected Logs:**
```
🎤 WORD #1 PRONUNCIATION:
   👂 User said:     'de'
   📖 Expected word: 'the'
   🎯 Match quality: 60% (weak)
   ✅ Result:        INCORRECT ❌ (45% score)
```
**Later:**
```
🚫 PATTERN OVERRIDE: th→d/t detected in 'de' → 'the' forced INCORRECT
```
**Result:** ✅ Correctly marked as incorrect

## Debugging Checklist

### ✅ Before Testing
- [ ] App rebuilt with latest changes
- [ ] Logcat open in Android Studio
- [ ] Filter set to `tag:VoskMFCCRecognizer`
- [ ] Log level set to "Debug" or "Verbose"
- [ ] Device/emulator connected

### ✅ During Testing
- [ ] Watch for "Using FREE-FORM recognition" message
- [ ] Check each word's pronunciation log appears
- [ ] Note any unexpected "User said" values
- [ ] Watch for OVERRIDE messages
- [ ] Check ONNX Ready status

### ✅ After Testing
- [ ] Review HYBRID ANALYSIS summary
- [ ] Compare match-based vs audio-based scores
- [ ] Check mispronunciation override count
- [ ] Verify final correct/incorrect counts
- [ ] Save logs if issues found

## Red Flags to Watch For

| Red Flag | Problem | Solution |
|----------|---------|----------|
| No pronunciation logs appear | Logs not enabled or filtered out | Check filter, rebuild app |
| "ONNX Ready: ❌ NO" | RF model not loaded | Check model file in assets |
| "User said" always matches expected | Grammar mode still active | Verify free-form recognition code |
| No OVERRIDE messages for "pader" | Override not working | Check MispronunciationOverride.java |
| Match quality always 100% | Not detecting differences | Check text matching logic |
| "Total audio buffers: 0" | Audio not recording | Check microphone permissions |

## Copy-Paste Logcat Commands

### Save Logs to File
```bash
adb logcat -d VoskMFCCRecognizer:D *:S > pronunciation_logs.txt
```

### Clear Logs Before Test
```bash
adb logcat -c
```

### Live Tail Pronunciation Logs
```bash
adb logcat VoskMFCCRecognizer:D *:S
```

### Filter for Errors Only
```bash
adb logcat VoskMFCCRecognizer:E *:S
```

## Quick Test Script

1. **Start fresh:**
   ```bash
   adb logcat -c
   ```

2. **Start logging:**
   ```bash
   adb logcat VoskMFCCRecognizer:D *:S > test_session.txt
   ```

3. **Run test session** (have student read passage)

4. **Stop logging** (Ctrl+C)

5. **Review logs:**
   ```bash
   grep "🎤 WORD" test_session.txt
   grep "OVERRIDE" test_session.txt
   grep "HYBRID ANALYSIS" test_session.txt
   ```

## Expected Log Flow

```
1. ✅ Using FREE-FORM recognition...
2. 🎤 WORD #1 PRONUNCIATION: ...
3. 🎤 WORD #2 PRONUNCIATION: ...
   ... (for each word)
4. 📊 AUDIO-ONLY PRONUNCIATION ANALYSIS
5. 🔀 HYBRID ANALYSIS (RF Model Primary + Mispronunciation Override)
6. ✅ AUDIO-ONLY ANALYSIS COMPLETE!
```

## Need Help?

If logs show unexpected behavior:
1. Copy the full log section
2. Note the expected word
3. Note what user actually said
4. Check if ONNX is ready
5. Check if overrides were applied
6. Compare match-based vs audio-based results
