# Model Deployment Issues - Diagnosis

## Problem Summary
Random Forest model achieves 90% accuracy in training but always predicts "correct" (class 1) in production Android app.

## Training Performance
```
Class 0 (mispronounced): 87% precision, 93% recall
Class 1 (correct): 92% precision, 86% recall
Overall accuracy: 90%
```

## Production Performance
```
Always predicts: Class 1 (correct)
Confidence: 80% (hardcoded fallback)
```

## Root Cause Analysis

### Issue 1: Feature Preprocessing Mismatch ✅ PARTIALLY FIXED
**Problem:** Training data was RMS-normalized, but Android app wasn't applying normalization.

**Solution:** Added RMS normalization to `ONNXRandomForestScorer.java`

**Status:** Implemented but model still not working correctly

### Issue 2: RMS Normalization Method Mismatch ⚠️ INVESTIGATING
**Problem:** Different RMS normalization methods produce different feature scales:
- Per-sample RMS (current Android implementation)
- Global RMS (across entire dataset)
- Per-coefficient RMS

**Need to verify:** Which method was used in training?

### Issue 3: MFCC Extraction Parameters
**Current Android:**
```java
TarsosMFCCExtractor(
    sampleRate: 16000,
    fftSize: 512,
    numCoeffs: 13,
    numFilters: 40
)
```

**Need to verify:** Do these match training parameters?

### Issue 4: Feature Calculation
**Android calculates:**
- 13 means (average per MFCC coefficient)
- 13 stds (standard deviation per coefficient)
- 13 maxs (maximum per coefficient)
- Total: 39 features

**Need to verify:** Does this match training feature extraction?

### Issue 5: Audio Preprocessing
**Android applies:**
1. Lightweight denoising (noise gate)
2. AGC (Automatic Gain Control)

**Need to verify:** Was training data preprocessed similarly?

## Next Steps

1. **Verify RMS normalization method** used in training
2. **Get sample feature values** from training CSV to compare scales
3. **Check MFCC extraction parameters** in training code
4. **Verify feature calculation** matches training
5. **Consider retraining** with exact Android preprocessing pipeline

## Temporary Workarounds

### Option A: Lower Decision Threshold
If model outputs probabilities, use stricter threshold:
```java
// Require 70% confidence for "correct" instead of 50%
isCorrect = (correctProb > 0.7);
```

### Option B: Export Model with Probabilities
Current model outputs class labels (0/1). Export with probabilities:
```python
from skl2onnx import convert_sklearn
onnx_model = convert_sklearn(
    model,
    options={id(model): {'zipmap': False}}  # Get probabilities
)
```

### Option C: Retrain with Android Features
1. Extract features using Android app
2. Save to CSV
3. Retrain model on Android-extracted features
4. This guarantees perfect feature matching

## Files Modified
- `app/src/main/java/com/example/speak/ONNXRandomForestScorer.java` - Added RMS normalization
- `app/src/main/java/com/example/speak/MFCCPronunciationRecognizer.java` - Improved VAD thresholds

## Current Status
- ✅ RMS normalization added
- ✅ VAD thresholds adjusted
- ❌ Model still always predicts "correct"
- ⚠️ Need to verify exact training preprocessing pipeline
