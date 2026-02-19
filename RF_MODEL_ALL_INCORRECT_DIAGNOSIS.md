# RF Model "All Incorrect" Diagnosis & Fix

## Current Problem

Your retrained RF model (`random_forest_mfcc.onnx`) is predicting **Class 0 (INCORRECT)** for ALL words, regardless of pronunciation quality.

### Symptoms
- All words highlighted RED in the app
- RF model always outputs: `Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%`
- No variation in predictions

### Previous Problem
- Old model (`randomforest.onnx`) predicted Class 1 for everything (all CORRECT)
- New model has opposite problem (all INCORRECT)

## Root Cause Analysis

The model is severely biased toward Class 0. This typically happens due to:

### 1. Training on Same Data as Testing
```python
# ❌ WRONG - Model memorizes training data
model.fit(X, y)
accuracy = model.score(X, y)  # 100% accuracy (overfitted)

# When deployed, sees NEW audio → predicts default class (0)
```

**Fix**: Use proper train/test split
```python
# ✅ CORRECT
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, stratify=y, random_state=42)
model.fit(X_train, y_train)
test_accuracy = model.score(X_test, y_test)  # 70-90% (generalizes)
```

### 2. Class Imbalance in Training Data
```python
# ❌ WRONG - Heavily imbalanced
# Class 0: 900 samples (incorrect)
# Class 1: 100 samples (correct)
# Model learns to always predict Class 0 (90% accuracy by default)
```

**Fix**: Balance your dataset
```python
# ✅ CORRECT - Balanced classes
# Class 0: 500 samples (incorrect)
# Class 1: 500 samples (correct)
```

### 3. Feature Extraction Mismatch
Training uses different MFCC parameters than runtime:

| Parameter | Training | Runtime (MFCCExtractor.java) |
|-----------|----------|------------------------------|
| Sample Rate | ??? | 16000 Hz |
| MFCC Coefficients | ??? | 13 |
| Statistics | ??? | mean + std + delta (39 features) |
| Normalization | ??? | Audio normalized to [-1, 1] |

**Fix**: Ensure training matches runtime exactly

### 4. Model Overfitting
```python
# Model achieves 100% accuracy on training data
# But fails on new data → defaults to most common class
```

**Fix**: 
- Use cross-validation
- Reduce model complexity (max_depth, n_estimators)
- Collect more diverse training data

## Diagnostic Steps

### Step 1: Monitor RF Model Output
Run this command while testing the app:
```bash
monitor_rf_only.bat
```

Or manually:
```bash
adb logcat -s RF_MODEL_OUTPUT:I
```

### Step 2: Check for Class Diversity
Read a passage with GOOD pronunciation. You should see:
```
📊 Word: 'the' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
📊 Word: 'cat' | Class: 1 (CORRECT) | Correct: 82.0% | Incorrect: 18.0%
```

If you ONLY see Class 0, the model is broken.

### Step 3: Analyze Training Notebook
Copy your notebook to the workspace:
```bash
copy "C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb" .
```

Then check:
1. **Class distribution**: `y.value_counts()`
2. **Train/test split**: Look for `train_test_split(..., stratify=y)`
3. **Test accuracy**: Should be 70-90%, NOT 100%
4. **Test predictions**: Should show BOTH Class 0 and Class 1

## Quick Fixes to Try

### Fix 1: Verify Training Notebook
Open `C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb` and check:

```python
# 1. Check class distribution
print("Class distribution:")
print(y.value_counts())
# Should show roughly 50/50 split

# 2. Verify train/test split
X_train, X_test, y_train, y_test = train_test_split(
    X, y, 
    test_size=0.2,      # 80% train, 20% test
    stratify=y,         # Maintain class balance
    random_state=42
)

# 3. Check test set predictions
y_pred = model.predict(X_test)
print(f"Test set - Class 0: {sum(y_pred == 0)}, Class 1: {sum(y_pred == 1)}")
# Should show BOTH classes

# 4. Verify test accuracy
print(f"Test accuracy: {model.score(X_test, y_test)}")
# Should be 70-90%, NOT 100%
```

### Fix 2: Retrain with Proper Split
```python
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier

# Load your data
X = ...  # MFCC features (N samples x 39 features)
y = ...  # Labels (0 or 1)

# Check class balance
print("Class distribution:")
print(pd.Series(y).value_counts())

# Split with stratification
X_train, X_test, y_train, y_test = train_test_split(
    X, y, 
    test_size=0.2, 
    stratify=y,  # CRITICAL: Maintains class balance
    random_state=42
)

# Train model
model = RandomForestClassifier(
    n_estimators=100,
    max_depth=10,  # Prevent overfitting
    random_state=42
)
model.fit(X_train, y_train)

# Evaluate on TEST set (not training set!)
train_acc = model.score(X_train, y_train)
test_acc = model.score(X_test, y_test)

print(f"Train accuracy: {train_acc:.2%}")
print(f"Test accuracy: {test_acc:.2%}")

# Check test predictions
y_pred = model.predict(X_test)
print(f"Test predictions - Class 0: {sum(y_pred == 0)}, Class 1: {sum(y_pred == 1)}")

# If test accuracy is 100% or only one class is predicted, model is overfitted!
```

### Fix 3: Collect More Diverse Data
If model is overfitted, you need:
- More training samples (at least 500 per class)
- More diverse speakers
- Various recording conditions
- Different words/phonemes

## Expected Behavior

### Healthy Model
```
📊 Word: 'singing' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
📊 Word: 'walked' | Class: 1 (CORRECT) | Correct: 78.0% | Incorrect: 22.0%
📊 Word: 'sinking' | Class: 0 (INCORRECT) | Correct: 35.0% | Incorrect: 65.0%
📊 Word: 'worked' | Class: 0 (INCORRECT) | Correct: 28.0% | Incorrect: 72.0%
```
- Mix of Class 0 and Class 1
- Confidence varies based on pronunciation
- Correctly identifies mispronunciations

### Broken Model (Current State)
```
📊 Word: 'singing' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'walked' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'the' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
📊 Word: 'cat' | Class: 0 (INCORRECT) | Correct: 20.0% | Incorrect: 80.0%
```
- ALL predictions are Class 0
- Fixed confidence (20%/80%)
- No discrimination between good/bad pronunciation

## Next Steps

1. **Run monitoring script**: `monitor_rf_only.bat`
2. **Test the app**: Read a passage with good pronunciation
3. **Check output**: Do you see ANY Class 1 predictions?
4. **Copy notebook**: `copy "C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb" .`
5. **Share findings**: Tell me what you see in the monitoring output

## Temporary Workaround

If you need the app to work NOW while fixing the model, you can:

1. **Disable RF model** and use only DirectAudio:
   - Set `useHybridMode = false` in StudentDetail.java
   - DirectAudio will handle pronunciation scoring

2. **Use old model** (but it marks everything correct):
   - Rename `randomforest.onnx` back in ONNXRandomForestScorer.java
   - Not recommended, but better than all incorrect

3. **Invert predictions** (temporary hack):
   ```java
   // In ONNXRandomForestScorer.java, line ~145
   classification = correctProb > incorrectProb ? INCORRECT_PRONUNCIATION : CORRECT_PRONUNCIATION;
   // Swap the classes temporarily
   ```
   - This assumes your model is consistently wrong
   - NOT a real fix, just a band-aid
