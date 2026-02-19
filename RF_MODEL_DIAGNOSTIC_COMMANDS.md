# RF Model Diagnostic Commands

## Quick Commands to Monitor RF Model Output

### 1. Monitor ONLY RF Model Predictions (Class 0 or 1)
```bash
adb logcat -s RF_MODEL_OUTPUT:I
```

This shows ONLY the RF model predictions in a clean format:
```
📊 Word: 'singing' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'walked' | Class: 1 (CORRECT) | Correct: 80.0% | Incorrect: 20.0%
```

### 2. Monitor RF Model + MFCC Feature Stats
```bash
adb logcat -s ONNXRFScorer:D RF_MODEL_OUTPUT:I
```

This shows:
- MFCC feature statistics (min, max, avg)
- RF model predictions with confidence

### 3. Monitor Everything (Full Debug)
```bash
adb logcat -s ONNXRFScorer:D RF_MODEL_OUTPUT:I VoskMFCCRecognizer:D StudentDetail:D
```

### 4. Save RF Model Output to File
```bash
adb logcat -s RF_MODEL_OUTPUT:I > rf_model_output.txt
```

### 5. Count Class Predictions
```bash
adb logcat -s RF_MODEL_OUTPUT:I | grep "Class: 0" | wc -l  # Count INCORRECT
adb logcat -s RF_MODEL_OUTPUT:I | grep "Class: 1" | wc -l  # Count CORRECT
```

## What to Look For

### ✅ HEALTHY MODEL (Balanced Predictions)
```
📊 Word: 'singing' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
📊 Word: 'walked' | Class: 1 (CORRECT) | Correct: 82.0% | Incorrect: 18.0%
📊 Word: 'sinking' | Class: 0 (INCORRECT) | Correct: 35.0% | Incorrect: 65.0%
📊 Word: 'worked' | Class: 0 (INCORRECT) | Correct: 28.0% | Incorrect: 72.0%
```
- Mix of Class 0 and Class 1
- Confidence varies based on pronunciation quality

### ❌ BROKEN MODEL (All Same Class)
```
📊 Word: 'singing' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'walked' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'the' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'cat' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
```
- ALL predictions are Class 0 (or all Class 1)
- Model is not discriminating between good/bad pronunciation

## Current Issue

Based on the summary, your retrained model (`random_forest_mfcc.onnx`) is predicting:
- **Class 0 (INCORRECT)** for ALL words
- **50% confidence** (which gets converted to 20% correct / 80% incorrect)

This suggests:
1. Model is severely biased toward Class 0
2. Training data may have class imbalance
3. Model may be overfitted to training data
4. Feature extraction might differ between training and runtime

## Next Steps

1. **Run the monitoring command** while testing the app:
   ```bash
   adb logcat -s RF_MODEL_OUTPUT:I
   ```

2. **Read a passage** and check if you see ANY Class 1 predictions

3. **Check your training notebook** for:
   - Class distribution in training data (should be ~50/50)
   - Train/test split (should use `stratify=y`)
   - Model accuracy on TEST set (not training set)
   - Feature extraction method (should match MFCCExtractor.java)

4. **Verify MFCC features** match between training and runtime:
   - Sample rate: 16000 Hz
   - Number of MFCCs: 13
   - Statistics: mean + std + delta (39 features total)
