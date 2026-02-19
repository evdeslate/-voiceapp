# Rebuild and Test Guide - RF Model Fix

## Quick Steps

### 1. Rebuild the App (2 minutes)

```bash
# Clean and rebuild
./gradlew clean assembleDebug
```

Wait for build to complete. You should see:
```
BUILD SUCCESSFUL in 1m 23s
```

### 2. Install on Device (30 seconds)

```bash
# Install the new APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You should see:
```
Success
```

### 3. Start Monitoring (Immediate)

```bash
# Run the comprehensive diagnostic
diagnose_rf_model.bat
```

OR manually:
```bash
adb logcat -c
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D
```

### 4. Test in App (1 minute)

1. Open the app
2. Select a student
3. Choose a passage
4. Start reading
5. Watch the logs

## What You Should See

### On App Launch (Model Loading)
```
ONNXRFScorer: 🔄 Loading ONNX Random Forest model from: random_forest_mfcc.onnx
ONNXRFScorer: ✅ MFCC extractor initialized
ONNXRFScorer: ✅ Model loaded from assets: 12345 bytes
ONNXRFScorer: ✅✅✅ ONNX Random Forest model loaded successfully and ready!
ONNXRFScorer: ═══════════════════════════════════════════════════════
ONNXRFScorer: ONNX Model Info:
ONNXRFScorer:   Input count: 1
ONNXRFScorer:   Output count: 2
ONNXRFScorer:   Input names: [mfcc_input]  ← KEY: Should match your model
ONNXRFScorer:   Output names: [label, probabilities]
ONNXRFScorer: ═══════════════════════════════════════════════════════
ONNXRFScorer: ⚠️ IMPORTANT: Check if model expects:
ONNXRFScorer:   1. Scaled features (StandardScaler)?
ONNXRFScorer:   2. Exactly 39 features (13 MFCCs × 3 stats)?
ONNXRFScorer:   3. Input name matches (not hardcoded 'input')?
ONNXRFScorer: ═══════════════════════════════════════════════════════
ONNXRFScorer: ⚠️ No StandardScaler found (this is OK if model wasn't trained with scaling)
```

### During Reading (Per Word)
```
ONNXRFScorer: 🔍 MFCC (raw) for 'Maria': features=39, min=-15.23, max=8.45, avg=-2.34
ONNXRFScorer: 🔍 Using input name: 'mfcc_input' with shape [1, 39]
RF_MODEL_OUTPUT: 📊 Word: 'Maria' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%

ONNXRFScorer: 🔍 MFCC (raw) for 'woke': features=39, min=-12.45, max=7.23, avg=-1.89
ONNXRFScorer: 🔍 Using input name: 'mfcc_input' with shape [1, 39]
RF_MODEL_OUTPUT: 📊 Word: 'woke' | Class: 1 (CORRECT) | Correct: 82.0% | Incorrect: 18.0%

ONNXRFScorer: 🔍 MFCC (raw) for 'early': features=39, min=-14.12, max=6.78, avg=-2.34
ONNXRFScorer: 🔍 Using input name: 'mfcc_input' with shape [1, 39]
RF_MODEL_OUTPUT: 📊 Word: 'early' | Class: 1 (CORRECT) | Correct: 88.0% | Incorrect: 12.0%
```

### After Reading (Blending)
```
StudentDetail: 🔀 BLENDING VOSK RF + DIRECTAUDIO SCORES
StudentDetail:   Vosk RF results: 47 words
StudentDetail:   DirectAudio results: 47 words
StudentDetail:   Vosk RF: 41 correct, 6 incorrect
StudentDetail:   DirectAudio: 41 correct, 6 incorrect
StudentDetail:   Blended result: 41 correct, 6 incorrect
StudentDetail:   Blended pronunciation score: 87.2%
```

## Success Indicators

### ✅ Model is Working Correctly
- Input name matches (e.g., `mfcc_input`)
- Feature count is 39
- Predictions show BOTH Class 0 and Class 1
- Confidence varies (not all 50% or 80%)
- Good pronunciations → Class 1 (high confidence)
- Bad pronunciations → Class 0 (low confidence)

### ❌ Model Still Broken
- All predictions are Class 0
- All predictions are Class 1
- All confidence is 50% or 80%
- Input name mismatch (expects `mfcc_input`, app sends `input`)
- Feature count is not 39

## If Still All Class 0

If you still see only Class 0 predictions after rebuild, check:

### 1. Input Name
```
ONNXRFScorer:   Input names: [mfcc_input]  ← Note this
ONNXRFScorer: 🔍 Using input name: 'mfcc_input'  ← Should match
```

If these don't match, there's still an issue.

### 2. Feature Count
```
ONNXRFScorer: 🔍 MFCC (raw) for 'Maria': features=39
                                                  ↑↑
                                            Should be 39
```

If not 39, there's a feature extraction mismatch.

### 3. Model Output Type
Check if model outputs labels or probabilities:
```
ONNXRFScorer: 🔍 Model output for 'Maria': class=0 (0=incorrect, 1=correct)
```

## Troubleshooting

### No ONNXRFScorer logs?
→ App not rebuilt. Run `./gradlew clean assembleDebug` again

### No RF_MODEL_OUTPUT logs?
→ Model not being called. Check if hybrid mode is enabled

### Build fails?
→ Check for syntax errors in ONNXRandomForestScorer.java

### Install fails?
→ Uninstall old app first: `adb uninstall com.example.speak`

## Quick Commands Reference

```bash
# Rebuild
./gradlew clean assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor (comprehensive)
diagnose_rf_model.bat

# Monitor (RF only)
monitor_rf_only.bat

# Clear logs
adb logcat -c

# Check if app is running
adb shell ps | findstr speak
```

## Expected Timeline

1. Rebuild: 1-2 minutes
2. Install: 30 seconds
3. Start monitoring: Immediate
4. Test in app: 1 minute
5. Verify logs: 30 seconds

Total: ~5 minutes

## Next Steps After Testing

Once you see the logs:

1. **If working** (mix of Class 0 and 1):
   - ✅ RF model is fixed!
   - Test with various pronunciations
   - Verify mispronunciations are caught

2. **If still broken** (all Class 0):
   - Share the logs with me
   - I'll analyze the input name and feature count
   - We'll identify the remaining issue

Ready? Run these commands:
```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
diagnose_rf_model.bat
```

Then open the app and start reading!
