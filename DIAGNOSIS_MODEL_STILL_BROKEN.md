# Diagnosis: Model Still Predicting Only "Correct"

## Current Status

✅ RMS normalization is working (RMS energy = 3276.5 ≈ 0.1 * 32768)
✅ Min-Max normalization is working (features in [0, 1] range)
❌ Model still always predicts class 1 (correct)

## Log Analysis

### Word "a"
```
MFCC means: [-276.937, 11.981, 7.141, 8.362, 4.973, -4.620, 8.244, -9.456, 6.594, -4.263, 4.175, -2.576, 6.193]
Normalized: [0.474, 0.309, 0.718, 0.350, 0.819, 0.472, 0.775, 0.535, 0.721, 0.588...]
Prediction: Class 1 (CORRECT)
```

### Word "little"
```
MFCC means: [-558.516, 16.515, 3.573, 3.623, 4.379, -4.419, 6.375, -6.568, 4.572, -4.478, 4.398, -4.512, 5.755]
Normalized: [0.000, 0.325, 0.703, 0.322, 0.815, 0.474, 0.757, 0.571, 0.696, 0.585...]
Prediction: Class 1 (CORRECT)
```

## Key Observations

1. **MFCC means are very negative** (-276, -558) - This is unusual
   - Training min/max: [-414.42, -124.30]
   - Your values fit within this range

2. **Normalized features cluster around 0.3-0.8** - Not much variation
   - All features end up in similar ranges
   - Model can't discriminate

3. **Model outputs hard labels** - No probabilities
   - Can't see confidence
   - Can't adjust thresholds

4. **First MFCC coefficient is extremely negative**
   - Word "a": -276.937
   - Word "little": -558.516
   - This is the energy coefficient (C0)

## Possible Root Causes

### Issue 1: MFCC C0 (Energy) Problem

The first MFCC coefficient (C0) represents signal energy and is typically much larger than other coefficients. Your values are extremely negative, which suggests:

**Hypothesis:** TarsosDSP might be computing MFCCs differently than your training code.

**Check:**
- Does your training code use the same MFCC library?
- Are the MFCC parameters identical (FFT size, mel filters, etc.)?
- Is C0 included or excluded in training?

### Issue 2: Model Was Not Retrained

**Critical Question:** Is `random_forest_model_retrained.onnx` actually trained with:
1. RMS-normalized audio (TARGET_RMS = 0.1)?
2. TarsosDSP MFCC extraction?
3. The same 39 features (13 means + 13 stds + 13 maxs)?

If the model was trained with different preprocessing, it won't work.

### Issue 3: Min/Max Values Are Wrong

The min/max values you provided might be from:
- A different preprocessing pipeline
- Different MFCC extraction method
- Different feature calculation

**Verify:** Do these min/max values come from features extracted with:
- RMS normalization (0.1)?
- Same MFCC parameters as TarsosDSP?
- Same feature calculation (mean/std/max)?

### Issue 4: Model Is Fundamentally Biased

If the model was trained on imbalanced data or with poor features, it might have learned to always predict "correct" because:
- Training data was mostly "correct" samples
- Features don't capture pronunciation differences
- Model overfit to training data

## Recommended Actions

### Action 1: Verify Training Pipeline

Check your training notebook (`rf_correct_training.ipynb`):

```python
# What MFCC library was used?
# librosa? python_speech_features? TarsosDSP equivalent?

# What were the exact MFCC parameters?
# - Sample rate: 16000?
# - FFT size: 512?
# - Num coefficients: 13?
# - Num mel filters: 40?

# Was RMS normalization applied BEFORE MFCC extraction?
# TARGET_RMS = 0.1?

# How were features calculated?
# means = mfcc.mean(axis=0)  # 13 values
# stds = mfcc.std(axis=0)    # 13 values
# maxs = mfcc.max(axis=0)    # 13 values
# features = np.concatenate([means, stds, maxs])  # 39 values
```

### Action 2: Export Model with Probabilities

Your current model outputs hard labels (0 or 1). Export it with probabilities:

```python
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# Define input shape
initial_type = [('float_input', FloatTensorType([None, 39]))]

# Convert with probability output
onnx_model = convert_sklearn(
    rf_model,
    initial_types=initial_type,
    options={id(rf_model): {'zipmap': False, 'nocl': False}}
)

# Save
with open('random_forest_model_with_probs.onnx', 'wb') as f:
    f.write(onnx_model.SerializeToString())
```

This will output probabilities instead of hard labels, allowing us to:
- See model confidence
- Adjust decision thresholds
- Debug better

### Action 3: Test with Known Mispronunciation

Record yourself saying a word VERY incorrectly (e.g., "little" as "LEE-tull" or "LIT-tull") and check:
- Do the MFCC features change significantly?
- Does the model still predict "correct"?
- What are the normalized feature values?

### Action 4: Compare Training vs Production Features

Extract features from the same audio file using:
1. Your training pipeline (Python)
2. Your production pipeline (Android)

Compare the 39 features - they should be nearly identical.

### Action 5: Retrain Model with Android-Extracted Features

The most reliable solution:

1. **Extract features using Android app:**
   - Record 50-100 audio samples (correct and incorrect)
   - Log the 39 features for each
   - Save to CSV

2. **Retrain model on Android features:**
   ```python
   # Load Android-extracted features
   df = pd.read_csv('android_features.csv')
   
   # Train model
   X = df.iloc[:, :39]
   y = df['label']
   
   rf = RandomForestClassifier(n_estimators=100)
   rf.fit(X, y)
   
   # Export to ONNX
   # ... (same as before)
   ```

This guarantees perfect feature matching.

## Immediate Next Steps

1. **Check training notebook** - Verify MFCC extraction method and parameters
2. **Export model with probabilities** - So we can see confidence scores
3. **Test with extreme mispronunciation** - See if features change at all
4. **Compare feature values** - Training vs production for same audio

## Why This Is Hard

Pronunciation detection is challenging because:
- MFCC features are sensitive to extraction parameters
- Small differences in preprocessing cause large feature differences
- Random Forest models are sensitive to feature scales
- Without probabilities, we can't debug model decisions

The fact that your model achieves 90% accuracy in training but fails in production strongly suggests a **preprocessing mismatch**, not a model quality issue.

## Questions to Answer

1. What MFCC library was used in training? (librosa, python_speech_features, etc.)
2. Were the exact same MFCC parameters used? (FFT size, mel filters, etc.)
3. Was RMS normalization applied in training BEFORE MFCC extraction?
4. Can you export the model with probability outputs instead of hard labels?
5. Can you share a sample from your training CSV so we can compare feature values?

Once we answer these questions, we can identify the exact mismatch and fix it.
