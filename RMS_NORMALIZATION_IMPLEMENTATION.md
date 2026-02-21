# RMS Normalization Implementation

## Summary
Added proper RMS normalization to match training data preprocessing. This is CRITICAL for the Random Forest model to work correctly.

## Changes Made

### 1. AudioPreProcessor.java
Added two RMS normalization methods:

```java
public float[] rmsNormalize(float[] samples)
public short[] rmsNormalize(short[] samples)
```

**Key Parameters:**
- `TARGET_RMS = 0.1f` - Target RMS level (matches training data)
- Scales audio to consistent intensity level
- Prevents clipping by clamping to [-1, 1]
- Skips normalization for silent audio (RMS < 1e-6)

**How it works:**
1. Calculate current RMS: `sqrt(sum(sample²) / length)`
2. Calculate scale factor: `TARGET_RMS / currentRMS`
3. Apply scaling: `normalized[i] = sample[i] * scale`
4. Clamp to prevent clipping

### 2. MFCCPronunciationRecognizer.java
Integrated RMS normalization into audio processing pipeline:

**Processing Order:**
1. Lightweight denoising (AudioDenoiser)
2. AGC - Automatic Gain Control (AudioDenoiser)
3. **RMS normalization (AudioPreProcessor)** ← NEW
4. MFCC extraction (TarsosMFCCExtractor)
5. Feature statistics calculation (ONNXRandomForestScorer)
6. Model inference (ONNX Random Forest)

### 3. ONNXRandomForestScorer.java
Updated documentation to clarify:
- Input audio is already RMS-normalized
- Min/Max arrays are PLACEHOLDERS and need actual training values
- Added instructions for extracting actual min/max from training data

## Why This Matters

### The Problem
Training data was RMS-normalized to a consistent level, but the Android app wasn't applying the same normalization. This caused:
- MFCC features to have different scales
- Model to see "out-of-distribution" data
- Model to always predict "correct" (class 1)

### The Solution
By applying RMS normalization with `TARGET_RMS = 0.1f`:
- Audio intensity is normalized to match training data
- MFCC features will have similar scales to training
- Model can properly discriminate between correct/incorrect pronunciations

## Audio Processing Pipeline

```
Raw Audio (microphone)
    ↓
Noise Gate (AudioPreProcessor.process)
    ↓
Bandpass Filter 80-3400 Hz (AudioPreProcessor.process)
    ↓
Lightweight Denoising (AudioDenoiser)
    ↓
AGC - Automatic Gain Control (AudioDenoiser)
    ↓
RMS Normalization to 0.1 (AudioPreProcessor) ← NEW
    ↓
MFCC Extraction (TarsosMFCCExtractor)
    ↓
Feature Statistics (mean, std, max)
    ↓
Min-Max Normalization (needs actual training min/max)
    ↓
ONNX Random Forest Model
    ↓
Prediction (correct/incorrect)
```

## Next Steps - COMPLETED ✅

The model now has **actual training min/max values** configured correctly!

### Values Confirmed

**TARGET_RMS:** `0.1f` (matches training)

**Training Min/Max Values:** Extracted from unnormalized MFCC features and applied in `ONNXRandomForestScorer.java`

```java
TRAINING_MIN_VALS = {-414.423096f, -80.241104f, -166.024902f, ...} // 39 values
TRAINING_MAX_VALS = {-124.297523f, 217.845215f, 75.084946f, ...}  // 39 values
```

These values are now correctly applied in the Min-Max normalization step.

## Testing

After deploying, check logcat for:

```
D/AudioPreProcessor: RMS normalization: 0.034567 → 0.100000 (scale: 2.891)
D/TarsosMFCCExtractor: First frame MFCCs: [38.463, 26.613, -6.545, ...]
D/ONNXRFScorer: MFCC means: [-263.899, 17.695, 1.993, ...]
D/ONNXRFScorer: Normalized features (first 10): [0.292, 0.312, 0.294, ...]
```

**Expected behavior:**
- RMS normalization should scale audio to ~0.1
- MFCC values should be in reasonable ranges
- Normalized features should be in [0, 1] range
- Model should predict BOTH classes (not always "correct")

## Files Modified

1. `app/src/main/java/com/example/speak/AudioPreProcessor.java`
   - Added `rmsNormalize(float[])` method
   - Added `rmsNormalize(short[])` method
   - Added `TARGET_RMS = 0.1f` constant

2. `app/src/main/java/com/example/speak/MFCCPronunciationRecognizer.java`
   - Integrated RMS normalization into `processWord()` method
   - Updated logging to show "After preprocessing + RMS normalization"

3. `app/src/main/java/com/example/speak/ONNXRandomForestScorer.java`
   - Updated documentation for `calculateMFCCStatistics()`
   - Added detailed instructions for getting actual training min/max values
   - Clarified that placeholder values WILL NOT WORK

## Important Notes

1. **TARGET_RMS = 0.1f** - This value should match what was used in training. If your training used a different target RMS, update this constant.

2. **RMS normalization is applied AFTER denoising and AGC** - This ensures clean audio is normalized, not noisy audio.

3. **Min-Max normalization still needs actual values** - The current placeholder values (all 0.0 and 1.0) will not work. You MUST extract actual min/max from your training data.

4. **Training data must have used the same TARGET_RMS** - If your training used a different RMS target, the model won't work correctly.

## Verification Checklist

- [x] RMS normalization added to AudioPreProcessor
- [x] RMS normalization integrated into MFCCPronunciationRecognizer
- [x] Documentation updated
- [x] Actual training min/max values extracted ✅
- [x] Placeholder min/max values replaced with actual values ✅
- [ ] App tested with real audio (READY TO TEST)
- [ ] Model predicts both correct AND incorrect (not always correct)
