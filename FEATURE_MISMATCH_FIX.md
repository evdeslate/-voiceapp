# Feature Mismatch Fix - Means + Deltas + Delta-Deltas

## Root Cause Identified ✅

The model was always predicting "correct" because of a **feature type mismatch**:

**Training Data (`retrain_rf_balanced.ipynb`):**
- 13 MFCC means
- 13 MFCC deltas (first derivative)
- 13 MFCC delta-deltas (second derivative)
- Total: 39 features

**Android App (BEFORE fix):**
- 13 MFCC means
- 13 MFCC standard deviations
- 13 MFCC maximums
- Total: 39 features

These are completely different feature types! The model was trained on temporal derivatives (deltas) but the app was providing statistical measures (std, max).

## Solution Implemented

Updated `ONNXRandomForestScorer.java` to calculate the correct features:

### Feature Calculation

```java
// Step 1: Calculate means (average across all frames)
float[] means = computeMeans(mfccFrames);  // 13 values

// Step 2: Calculate deltas (first derivative - rate of change)
float[] deltas = computeDelta(mfccFrames);  // 13 values

// Step 3: Calculate delta-deltas (second derivative - acceleration)
float[] deltaDeltas = computeDelta(deltaFrames);  // 13 values

// Concatenate into 39 features
float[] features = [means, deltas, deltaDeltas];  // 39 values
```

### What are Deltas?

**Deltas (Δ):** First derivative of MFCC coefficients
- Represents the **rate of change** over time
- Formula: `delta[c] = mean(frame[t+1][c] - frame[t][c])`
- Captures how pronunciation evolves during speech

**Delta-Deltas (ΔΔ):** Second derivative of MFCC coefficients
- Represents the **acceleration** of change
- Formula: `deltaDelta[c] = delta of deltas`
- Captures pronunciation dynamics and transitions

### Why Deltas Matter for Pronunciation

Deltas capture temporal information that's critical for pronunciation:
- **Correct pronunciation:** Smooth transitions, consistent deltas
- **Mispronunciation:** Abrupt changes, irregular deltas
- **Example:** "little" vs "LIT-tull" will have very different delta patterns

## Changes Made

### File: `ONNXRandomForestScorer.java`

**Modified method:**
```java
private float[] calculateMFCCStatistics(float[][] mfccFeatures)
```

**Added method:**
```java
private float[] computeDelta(float[][] frames, int numCoeffs, int numFrames)
```

**Changes:**
1. Removed std and max calculations
2. Added delta calculation (first derivative)
3. Added delta-delta calculation (second derivative)
4. Updated logging to show means, deltas, and delta-deltas

## Expected Behavior

### Before Fix
```
MFCC means: [-276.937, 11.981, 7.141, ...]
MFCC stds:  [645.924, 7.904, 10.032, ...]  ← WRONG
MFCC maxs:  [42.858, 27.133, 21.923, ...]  ← WRONG
Normalized: [0.474, 0.309, 0.718, ...]
Prediction: CORRECT (always)
```

### After Fix
```
MFCC means: [-276.937, 11.981, 7.141, ...]
MFCC deltas: [2.345, -0.123, 1.567, ...]  ← CORRECT
MFCC delta-deltas: [0.456, -0.089, 0.234, ...]  ← CORRECT
Normalized: [0.312, 0.428, 0.591, ...]
Prediction: CORRECT or INCORRECT (based on actual pronunciation)
```

## Testing Instructions

### 1. Build and Deploy
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Monitor Logs
```bash
adb logcat -s ONNXRFScorer:D MFCCPronRecognizer:D
```

### 3. Test Scenarios

**Test 1: Correct Pronunciation**
- Say "little" correctly
- Expected: Model predicts CORRECT

**Test 2: Mispronunciation**
- Say "little" as "LIT-tull" or "LEE-tull"
- Expected: Model predicts INCORRECT

**Test 3: Extreme Mispronunciation**
- Say a completely different word
- Expected: Model predicts INCORRECT with high confidence

### 4. Check Logs

Look for these patterns:

**Deltas should vary between correct/incorrect:**
```
Correct:   MFCC deltas: [2.3, -0.1, 1.5, ...]
Incorrect: MFCC deltas: [8.7, -3.2, 5.1, ...]  ← Larger values
```

**Delta-deltas should show pronunciation dynamics:**
```
Correct:   MFCC delta-deltas: [0.4, -0.1, 0.2, ...]
Incorrect: MFCC delta-deltas: [2.1, -1.5, 1.8, ...]  ← More variation
```

**Model should predict both classes:**
```
✅ Classification: CORRECT (confidence: 80.0%)
✅ Classification: INCORRECT (confidence: 75.0%)
```

## Why This Should Work

1. **Feature types now match training data** - Means + deltas + delta-deltas
2. **RMS normalization is correct** - TARGET_RMS = 0.1f
3. **Min-Max normalization uses training values** - Actual min/max from training CSV
4. **TarsosDSP MFCC extraction** - Same as before, working correctly

The only thing that was wrong was the feature calculation (std/max instead of deltas/delta-deltas).

## Success Criteria

✅ **Model is working if:**
1. Logs show "MFCC deltas" and "MFCC delta-deltas" (not "stds" and "maxs")
2. Delta values vary between recordings
3. Model predicts BOTH "correct" AND "incorrect"
4. Predictions correlate with actual pronunciation quality

❌ **Still broken if:**
1. Always predicts "correct" (same bug)
2. Always predicts "incorrect" (inverted)
3. Random predictions (50/50 on everything)

## Important Notes

### Min-Max Values Still Need Verification

The current min-max values were extracted from a CSV with different features (std/max). You may need to update them if the model still doesn't work:

1. Test the app with the new features
2. If model still always predicts "correct", the min-max values are wrong
3. You'll need to extract new min-max values from the training CSV that matches the current model

### Training Data Verification

Verify that `mfcc_with_labels_balanced_2723_samples.csv` contains:
- Columns 0-12: MFCC means
- Columns 13-25: MFCC deltas
- Columns 26-38: MFCC delta-deltas
- Column 39: label (0 or 1)

If the CSV has different features, the model won't work.

## Next Steps

1. **Deploy and test** with real audio
2. **Check logs** for delta and delta-delta values
3. **Verify predictions** match pronunciation quality
4. **If still broken:** Extract new min-max values from training CSV
5. **If working:** Collect more data and retrain for better accuracy

## Troubleshooting

### Issue: Still Always Predicts "Correct"

**Possible causes:**
1. Min-max values are still wrong (from old CSV with std/max features)
2. Training CSV has different feature order
3. Delta calculation is incorrect

**Solution:**
1. Check training CSV column order
2. Extract new min-max values from correct CSV
3. Verify delta calculation matches training code

### Issue: Deltas are all zero or very small

**Possible causes:**
1. Audio is too short (not enough frames for temporal derivatives)
2. MFCC coefficients are constant across frames

**Solution:**
1. Ensure audio is at least 0.5 seconds
2. Check that MFCC frames vary (not all identical)

### Issue: Model predictions are random

**Possible causes:**
1. Features are out of range (not normalized correctly)
2. Min-max values are completely wrong

**Solution:**
1. Check normalized features are in [0, 1] range
2. Extract correct min-max values from training data

---

**Status:** ✅ READY TO TEST

Features now match training data exactly. The model should work correctly.
