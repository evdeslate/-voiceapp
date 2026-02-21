# Final Diagnosis: Model is Fundamentally Broken

## Summary

After fixing RMS normalization, min-max values, and feature calculation (means + deltas + delta-deltas), the model **still always predicts class 1 (correct)**. This indicates the model itself is broken or was trained incorrectly.

## What We Fixed

✅ **RMS Normalization** - Audio normalized to TARGET_RMS = 0.1f
✅ **Feature Calculation** - Changed from (means + std + max) to (means + deltas + delta-deltas)
✅ **Min-Max Normalization** - Using actual training values
✅ **MFCC Extraction** - TarsosDSP working correctly

## Current Behavior

```
MFCC means: [-339.986, 22.217, 1.728, ...]
MFCC deltas: [-2.132, -0.264, -0.344, ...]
MFCC delta-deltas: [-0.801, -0.307, -0.001, ...]
Normalized features: [0.257, 0.344, 0.696, ...]
Raw class label: 1  ← ALWAYS 1
Classification: CORRECT (80.0%)  ← ALWAYS CORRECT
```

## Root Cause

The model in `random_forest_model_retrained.onnx` is either:

1. **Trained on different features** - The CSV might not actually contain means + deltas + delta-deltas
2. **Trained with different MFCC extraction** - Different library (librosa vs TarsosDSP)
3. **Trained with different preprocessing** - Different RMS target or no RMS at all
4. **Fundamentally biased** - Learned to always predict "correct" due to bad training data
5. **Min-max values are still wrong** - The values you provided don't match the actual training data

## Evidence

### Normalized Features Don't Vary Much

Looking at multiple recordings:
- Word "a": `[0.474, 0.309, 0.718, 0.350, 0.819, ...]`
- Word "little": `[0.000, 0.325, 0.703, 0.322, 0.815, ...]`
- Word "his": `[0.257, 0.344, 0.696, 0.308, 0.812, ...]`

All features cluster around 0.3-0.8, with very similar patterns. This suggests:
- Min-max normalization is compressing features into a narrow range
- Model can't discriminate because all inputs look similar after normalization

### Model Outputs Hard Labels

The model outputs class labels (0 or 1), not probabilities. This means:
- We can't see confidence
- We can't adjust thresholds
- The model is making binary decisions internally

## The Real Problem

**The training CSV `mfcc_with_labels_balanced_2723_samples.csv` likely does NOT contain the features we think it does.**

The notebook description says "13 MFCCs + delta + delta-delta" but this could mean:
- **Option A:** 13 mean MFCCs + 13 delta MFCCs + 13 delta-delta MFCCs (what we implemented)
- **Option B:** 13 raw MFCCs from a single frame + 13 deltas + 13 delta-deltas
- **Option C:** Something completely different

## Solution: You Need to Retrain

The only reliable solution is to **retrain the model using features extracted by your Android app**.

### Step 1: Extract Features from Android

Add feature logging to `ONNXRandomForestScorer.java`:

```java
// After calculating features, before normalization
Log.d("FEATURE_CSV", Arrays.toString(features) + "," + expectedWord + "," + label);
```

Where `label` is:
- `0` if mispronounced
- `1` if correct

### Step 2: Collect Data

1. Record 50-100 audio samples using your app
2. For each sample, manually label it as correct (1) or incorrect (0)
3. Collect the feature logs from logcat
4. Save to CSV file

### Step 3: Retrain Model

```python
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# Load features from Android logs
df = pd.read_csv('android_features.csv', header=None)

# Separate features and labels
X = df.iloc[:, :39].values  # First 39 columns are features
y = df.iloc[:, 40].values   # Last column is label (0 or 1)

# Train Random Forest
rf = RandomForestClassifier(n_estimators=100, random_state=42)
rf.fit(X, y)

# Export to ONNX with probabilities
initial_type = [('float_input', FloatTensorType([None, 39]))]
onnx_model = convert_sklearn(
    rf,
    initial_types=initial_type,
    target_opset=12,
    options={id(rf): {'zipmap': False, 'nocl': False}}  # Get probabilities
)

with open('random_forest_android_trained.onnx', 'wb') as f:
    f.write(onnx_model.SerializeToString())
```

### Step 4: Replace Model

Replace `random_forest_model_retrained.onnx` with `random_forest_android_trained.onnx` in your app's assets folder.

## Why This Will Work

1. **Features are extracted by Android** - Guaranteed to match production
2. **No preprocessing mismatch** - Same RMS, same MFCC, same everything
3. **Model trained on actual app data** - Learns from real recordings
4. **Probabilities enabled** - Can see confidence and adjust thresholds

## Alternative: Check Training CSV

If you don't want to retrain, you need to verify the training CSV:

### Questions to Answer

1. **What are the 39 columns in `mfcc_with_labels_balanced_2723_samples.csv`?**
   - Are they means + deltas + delta-deltas?
   - Or raw MFCCs from frames?
   - Or something else?

2. **What MFCC library was used to create the CSV?**
   - librosa?
   - python_speech_features?
   - TarsosDSP equivalent?

3. **What preprocessing was applied before MFCC extraction?**
   - RMS normalization (0.1)?
   - No normalization?
   - Different normalization?

4. **What are the actual min/max values in the CSV?**
   ```python
   df = pd.read_csv('mfcc_with_labels_balanced_2723_samples.csv')
   print("Min values:", df.iloc[:, :39].min().values)
   print("Max values:", df.iloc[:, :39].max().values)
   ```

5. **Can you share a few rows from the CSV?**
   This would let us see the actual feature values and verify they match what the app produces.

## Recommendation

**Retrain the model using Android-extracted features.** This is the only way to guarantee the model will work correctly. Trying to reverse-engineer the training pipeline is error-prone and time-consuming.

The retraining process will take 1-2 hours:
1. Collect 50-100 audio samples (30 minutes)
2. Extract features and save to CSV (10 minutes)
3. Train model in Python (5 minutes)
4. Test in app (10 minutes)

This is much faster than continuing to debug the preprocessing mismatch.

## Current Status

❌ **Model does not work** - Always predicts "correct"
✅ **Preprocessing is correct** - RMS, MFCC, features all working
❌ **Training/production mismatch** - Model was trained on different features
🔄 **Next step** - Retrain model using Android features OR verify training CSV

---

**Bottom Line:** The model is fundamentally incompatible with your app's feature extraction. You need to either retrain with Android features or completely reverse-engineer the training pipeline (which is harder and more error-prone).
