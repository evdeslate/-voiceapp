# Current RF Model Information

## Model File Being Used

The app is currently using:

**File:** `rf_pipeline.onnx`
**Location:** `app/src/main/assets/rf_pipeline.onnx`
**Loaded by:** `ONNXRandomForestScorer.java` (line 23)

```java
private static final String MODEL_PATH = "rf_pipeline.onnx"; // Using pipeline model with built-in scaler
```

## Available Model Files in Assets

The assets folder contains multiple RF model files:

1. **rf_pipeline.onnx** ← Currently in use
2. **randomforest.onnx** (older version)
3. **random_forest_mfcc.onnx** (older version)

## What is rf_pipeline.onnx?

This is a **pipeline model** that includes:
- Random Forest classifier
- Built-in StandardScaler for feature normalization
- MFCC feature processing

The pipeline model was created to ensure features are properly scaled before classification.

## Model Behavior

Based on the logs, this model:
- Returns **class labels (0 or 1)** instead of probabilities
- All predictions get assigned **80% confidence** (hardcoded)
- Marks **all words as correct** (100% accuracy in logs)

```
Audio-based: 100.0% (47/47 correct)
Word 0 'maria': ✅ (80% confidence)
Word 1 'woke': ✅ (80% confidence)
...all words get 80% confidence
```

## Why It's Not Working

The model is marking everything as correct because:

1. **Training data issue** - Model may not have enough negative examples (mispronunciations)
2. **Feature extraction issue** - MFCC features may not capture pronunciation differences
3. **Model export issue** - Exported with class labels instead of probabilities
4. **Threshold issue** - Model's decision boundary may be too lenient

## How to Replace the Model

If you have a new/better trained model:

1. **Export the model** with probability outputs:
   ```python
   from skl2onnx import convert_sklearn
   from skl2onnx.common.data_types import FloatTensorType
   
   initial_type = [('input', FloatTensorType([None, num_features]))]
   onx = convert_sklearn(
       pipeline,  # Your sklearn pipeline with scaler + RF
       initial_types=initial_type,
       options={id(rf_model): {'zipmap': False, 'output_class_labels': False}}
   )
   
   with open("rf_pipeline.onnx", "wb") as f:
       f.write(onx.SerializeToString())
   ```

2. **Replace the file** in `app/src/main/assets/rf_pipeline.onnx`

3. **Rebuild the app**

## Model Training Notebooks

I see you have training notebooks open:
- `random_forest_mfcc_onnx.ipynb`
- `strategy2_retrain_rf.ipynb`
- `strategy2_retrain_rf (1).ipynb`

These likely contain the code used to train and export the current model.

## Recommendation

To fix the "all words marked correct" issue, you need to:

1. **Check training data** - Ensure you have balanced examples of correct and incorrect pronunciations
2. **Re-export model** - Use `'output_class_labels': False` to get probabilities
3. **Test model** - Verify it can distinguish between correct and incorrect pronunciations before deploying

## Alternative: Use DirectAudio

Since the RF model isn't working properly, I've enabled **Hybrid Mode** which uses DirectAudio for pronunciation analysis. DirectAudio uses a different approach that doesn't rely on the RF model.

## Files Using RF Model

1. **ONNXRandomForestScorer.java** - Loads and runs the model
2. **VoskMFCCRecognizer.java** - Uses it for pronunciation analysis
3. **DirectAudioPronunciationAnalyzer.java** - Also uses it for pronunciation analysis

All three use the same `rf_pipeline.onnx` model file.

## Summary

- **Current model:** `rf_pipeline.onnx`
- **Problem:** Marks all words as correct with 80% confidence
- **Cause:** Model not properly trained or exported
- **Workaround:** Hybrid mode with DirectAudio (enabled)
- **Long-term fix:** Retrain and re-export the model with proper data and settings
