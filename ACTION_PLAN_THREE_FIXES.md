# Action Plan: Fix RF Model "All Class 0" Issue

## Three Critical Issues Identified

You correctly identified the three most likely causes:

1. **Missing StandardScaler** - Model trained on scaled features, app sends raw features
2. **Feature Shape Mismatch** - Model expects 39 features, app sends different number
3. **ONNX Input Name Mismatch** - Model expects specific input name, app uses hardcoded "input"

## What I've Fixed in the Code

### ✅ Fix 1: Dynamic Input Name (DONE)
The app now automatically detects and uses the correct input name from the model:
```java
String inputName = session.getInputNames().iterator().next();
Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, inputTensor);
```

### ✅ Fix 2: StandardScaler Support (DONE)
The app now:
- Tries to load `scaler_params.json` from assets
- Applies StandardScaler if available: `(x - mean) / scale`
- Logs whether scaler is loaded and applied
- Works with or without scaler

### ✅ Fix 3: Enhanced Diagnostics (DONE)
The app now logs:
- Input names and shapes
- Feature count (should be 39)
- Raw MFCC stats (before scaling)
- Scaled MFCC stats (after scaling)
- Whether scaler is loaded

## What You Need to Do

### Step 1: Run Diagnostic (5 minutes)

```bash
diagnose_rf_model.bat
```

Then open your app and read a passage. Look for:

**Check 1: StandardScaler**
```
✅ StandardScaler loaded: 39 features  ← GOOD
   OR
⚠️ No StandardScaler found  ← OK if you didn't use it in training
```

**Check 2: Input Name**
```
Input names: [float_input]  ← Note this name
Using input name: 'float_input' with shape [1, 39]  ← Should match
```

**Check 3: Feature Count**
```
🔍 MFCC (raw) for 'singing': features=39  ← Should be 39
```

**Check 4: Model Predictions**
```
📊 Word: 'singing' | Class: 0 (INCORRECT)  ← Still all Class 0?
📊 Word: 'walked' | Class: 0 (INCORRECT)   ← Or mix of 0 and 1?
```

### Step 2: Export Scaler (if needed)

If diagnostic shows "No StandardScaler found" but you used it in training:

1. Open your notebook: `C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb`

2. Add this cell:
```python
import json

# Export scaler parameters
scaler_params = {
    'mean': scaler.mean_.tolist(),
    'scale': scaler.scale_.tolist()
}

with open('scaler_params.json', 'w') as f:
    json.dump(scaler_params, f, indent=2)

print(f"✅ Saved scaler_params.json with {len(scaler.mean_)} features")
```

3. Copy to app:
```bash
copy "C:\Users\Elizha\Downloads\scaler_params.json" "app\src\main\assets\"
```

4. Rebuild and test again

### Step 3: Verify Feature Count

If diagnostic shows `features=26` or `features=52` instead of 39:

**Check your training notebook:**
```python
print(f"Feature count: {X.shape[1]}")  # Should be 39
```

**Check MFCCExtractor.java:**
- Should extract 13 MFCC coefficients
- Should compute 3 statistics (mean + std + delta)
- Total: 13 × 3 = 39 features

### Step 4: Check Training Notebook

Even with the fixes above, if model still predicts all Class 0, check:

```python
# 1. Class distribution
print(y.value_counts())
# Should be roughly 50/50

# 2. Train/test split
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, stratify=y, random_state=42
)

# 3. Test predictions
y_pred = model.predict(X_test)
print(f"Class 0: {sum(y_pred == 0)}, Class 1: {sum(y_pred == 1)}")
# Should show BOTH classes

# 4. Test accuracy
print(f"Test accuracy: {model.score(X_test, y_test)}")
# Should be 70-90%, NOT 100%
```

## Decision Tree

```
Start: Model predicts all Class 0
  │
  ├─ Run diagnose_rf_model.bat
  │
  ├─ Check 1: StandardScaler loaded?
  │  ├─ NO → Did you use StandardScaler in training?
  │  │  ├─ YES → Export scaler_params.json and copy to assets
  │  │  └─ NO → OK, continue to Check 2
  │  └─ YES → Continue to Check 2
  │
  ├─ Check 2: Feature count = 39?
  │  ├─ NO → Fix MFCC extraction or retrain with correct features
  │  └─ YES → Continue to Check 3
  │
  ├─ Check 3: Input name matches?
  │  ├─ NO → Already fixed in code (auto-detects)
  │  └─ YES → Continue to Check 4
  │
  └─ Check 4: Still all Class 0?
     ├─ YES → Problem is in training (class imbalance, overfitting)
     └─ NO → ✅ FIXED! Model now predicts both classes
```

## Expected Results After Fixes

### Before Fixes
```
⚠️ No StandardScaler found
Input names: [float_input]
Using input name: 'input' with shape [1, 39]  ← WRONG NAME!
🔍 MFCC (raw) for 'singing': features=39, min=-15.23, max=8.45, avg=-2.34
📊 Word: 'singing' | Class: 0 (INCORRECT) | Correct: 20.0%
📊 Word: 'walked' | Class: 0 (INCORRECT) | Correct: 20.0%
📊 Word: 'the' | Class: 0 (INCORRECT) | Correct: 20.0%
```

### After Fixes
```
✅ StandardScaler loaded: 39 features
   Mean range: [-12.34, 5.67]
   Scale range: [2.34, 8.90]
Input names: [float_input]
Using input name: 'float_input' with shape [1, 39]  ← CORRECT!
🔍 MFCC (raw) for 'singing': features=39, min=-15.23, max=8.45, avg=-2.34
🔍 MFCC (scaled) for 'singing': features=39, min=-1.23, max=1.45, avg=0.12
📊 Word: 'singing' | Class: 1 (CORRECT) | Correct: 85.0%  ← FIXED!
📊 Word: 'walked' | Class: 1 (CORRECT) | Correct: 82.0%   ← FIXED!
📊 Word: 'sinking' | Class: 0 (INCORRECT) | Correct: 35.0% ← FIXED!
```

## Files Created

1. **diagnose_rf_model.bat** - Run this first to diagnose issues
2. **Diagnose-RFModel.ps1** - PowerShell version with colors
3. **EXPORT_SCALER_GUIDE.md** - How to export scaler from notebook
4. **FEATURE_SCALING_FIX.md** - Detailed explanation of scaling issue
5. **ACTION_PLAN_THREE_FIXES.md** - This file

## Quick Start

```bash
# 1. Rebuild app with fixes
./gradlew assembleDebug

# 2. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Run diagnostic
diagnose_rf_model.bat

# 4. Open app and read a passage

# 5. Check output for issues

# 6. If needed, export scaler and rebuild
```

## Summary

The code is now fixed to handle all three issues:
- ✅ Dynamic input name detection
- ✅ StandardScaler support (optional)
- ✅ Enhanced diagnostics

Your next step: Run `diagnose_rf_model.bat` and tell me what you see!
