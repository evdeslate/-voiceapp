# Random Forest Pipeline Model Update

## Change Summary
Updated the ONNX Random Forest scorer to use `rf_pipeline.onnx` instead of `randomforest.onnx`.

## What Changed

### File: `app/src/main/java/com/example/speak/ONNXRandomForestScorer.java`

**Before:**
```java
private static final String MODEL_PATH = "randomforest.onnx";
```

**After:**
```java
private static final String MODEL_PATH = "rf_pipeline.onnx"; // Using pipeline model with built-in scaler
```

## What is rf_pipeline.onnx?

The `rf_pipeline.onnx` model is a complete pipeline that includes:
1. **StandardScaler**: Normalizes MFCC features (mean=0, std=1)
2. **Random Forest Classifier**: Trained model for pronunciation scoring

This is different from `randomforest.onnx` which only contained the Random Forest model without the scaler.

## Benefits

### 1. Built-in Feature Scaling
- The pipeline automatically scales features using the same scaler used during training
- No need to manually implement scaling in Java
- Ensures consistency between training and inference

### 2. Correct Predictions
- Previous model (`randomforest.onnx`) was receiving unscaled features
- This caused all predictions to be incorrect (80% confidence for everything)
- Pipeline model receives features in the correct format

### 3. Simplified Code
- No need to maintain separate scaler parameters
- Single model file handles entire preprocessing + prediction pipeline
- Easier to update model in the future

## How It Works

### Input Flow
```
Audio Samples (short[])
    ↓
MFCCExtractor.extractMFCC()
    ↓
MFCC Features (float[][])
    ↓
MFCCExtractor.getMFCCStatistics()
    ↓
MFCC Statistics (float[]) - 26 features
    ↓
rf_pipeline.onnx
    ├─ StandardScaler (normalizes features)
    └─ Random Forest (classifies pronunciation)
    ↓
Output: Probabilities [incorrect_prob, correct_prob]
```

### Pipeline Stages

**Stage 1: StandardScaler**
- Input: Raw MFCC statistics (26 features)
- Process: `(x - mean) / std` for each feature
- Output: Normalized features

**Stage 2: Random Forest**
- Input: Normalized features
- Process: Ensemble of decision trees
- Output: Class probabilities [0.0-1.0, 0.0-1.0]

## Model Files in Assets

Current models in `app/src/main/assets/`:
- ✅ `rf_pipeline.onnx` - **NOW USING** (pipeline with scaler + RF)
- ❌ `randomforest.onnx` - Old model (RF only, no scaler)
- ❌ `random_forest_mfcc.onnx` - Alternative model (not used)
- `pronunciation_mfcc.tflite` - TensorFlow Lite model (used by DirectAudio)
- `distilbert_fp16.tflite` - Text analysis model

## Testing

### Expected Behavior
After this change, the Random Forest model should:
1. Load successfully on app start
2. Provide accurate pronunciation predictions
3. Show varied confidence scores (not always 80%)
4. Correctly identify mispronunciations

### Logcat Monitoring
```bash
adb logcat | findstr /C:"ONNXRFScorer" /C:"rf_pipeline"
```

### Expected Log Messages

**On Model Load:**
```
🔄 Loading ONNX Random Forest model from: rf_pipeline.onnx
✅ MFCC extractor initialized
✅ ONNX Runtime environment created
✅ Model loaded from assets: XXXXX bytes
✅✅✅ ONNX Random Forest model loaded successfully and ready!
ONNX Model Info:
  Input count: 1
  Output count: 2
  Input names: [input]
  Output names: [label, probabilities]
```

**During Inference:**
```
📊 RF Analysis: Word X - Correct: XX%, Incorrect: XX%
```

### Verification Steps

1. **Build and Install:**
   ```bash
   ./gradlew clean assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Test Reading Session:**
   - Start a reading session
   - Read a passage
   - Check logcat for RF analysis results
   - Verify varied confidence scores

3. **Check Model Output:**
   - Should see different confidence values for different words
   - Not all words should be 80% confidence
   - Mispronunciations should be detected

## Troubleshooting

### If Model Fails to Load
```
❌❌❌ Failed to load ONNX model: [error message]
```

**Possible causes:**
1. Model file corrupted or missing
2. ONNX Runtime version incompatible
3. Model format incorrect

**Solution:**
- Verify `rf_pipeline.onnx` exists in `app/src/main/assets/`
- Check file size (should be > 0 bytes)
- Re-export model from training notebook

### If All Predictions Are Still 80%
```
📊 RF Analysis: Word 0 - Correct: 80%, Incorrect: 20%
📊 RF Analysis: Word 1 - Correct: 80%, Incorrect: 20%
📊 RF Analysis: Word 2 - Correct: 80%, Incorrect: 20%
```

**Possible causes:**
1. Model still outputting class labels instead of probabilities
2. Pipeline not configured correctly
3. Wrong output being read

**Solution:**
- Check model export code in training notebook
- Ensure pipeline outputs probabilities, not labels
- Verify output tensor format

### If Model Crashes
```
❌ Error during ONNX inference: [error message]
```

**Possible causes:**
1. Input shape mismatch
2. Feature count incorrect
3. Data type mismatch

**Solution:**
- Verify MFCC statistics produce 26 features
- Check input tensor shape: [1, 26]
- Ensure float32 data type

## Model Export Reference

The `rf_pipeline.onnx` model should be exported using:

```python
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# Create pipeline
pipeline = Pipeline([
    ('scaler', StandardScaler()),
    ('classifier', RandomForestClassifier())
])

# Train pipeline
pipeline.fit(X_train, y_train)

# Convert to ONNX
initial_type = [('input', FloatTensorType([None, 26]))]
onnx_model = convert_sklearn(
    pipeline,
    initial_types=initial_type,
    target_opset=12,
    options={
        'zipmap': False,  # Don't use ZipMap for output
        'nocl': False,    # Use class labels
        'raw_scores': False  # Use probabilities
    }
)

# Save model
with open('rf_pipeline.onnx', 'wb') as f:
    f.write(onnx_model.SerializeToString())
```

## Performance Impact

- **Model Size**: Slightly larger than `randomforest.onnx` (includes scaler parameters)
- **Inference Speed**: Negligible difference (scaler is very fast)
- **Memory Usage**: Minimal increase (scaler parameters are small)
- **Accuracy**: Should be significantly improved (correct feature scaling)

## Rollback Instructions

If you need to revert to the old model:

```java
private static final String MODEL_PATH = "randomforest.onnx";
```

However, note that the old model has the scaling issue and will not work correctly.

## Next Steps

1. Build and install the app
2. Test pronunciation scoring
3. Monitor logcat for model output
4. Verify varied confidence scores
5. Test with known mispronunciations

## Files Modified
- `app/src/main/java/com/example/speak/ONNXRandomForestScorer.java` (line 19)
  - Changed MODEL_PATH from "randomforest.onnx" to "rf_pipeline.onnx"

## Related Documentation
- `RF_MODEL_FIX_COMPLETE.md` - Previous RF model issues
- `FEATURE_SCALING_FIX.md` - Feature scaling problem explanation
- `HYBRID_MODE_SYNCHRONIZED_ANALYSIS.md` - How RF integrates with hybrid mode
