# Feature Scaling Fix - Critical Issue

## The Problem

You identified three critical issues that cause the model to predict Class 0 for everything:

### 1. ⚠️ Missing StandardScaler (MOST LIKELY)
```python
# In your training notebook:
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)  # Features scaled to mean=0, std=1
model.fit(X_scaled, y)

# In your app:
float[] mfccStats = mfccExtractor.getMFCCStatistics(mfccFeatures);
# ❌ Raw features sent to model (NOT scaled!)
# Model expects scaled features → predicts default class (0)
```

### 2. ⚠️ Feature Shape Mismatch
```python
# Training: 39 features (13 MFCCs × 3 stats)
# Runtime: ??? features

# If mismatch, model fails silently
```

### 3. ⚠️ ONNX Input Name Mismatch
```python
# Model expects: "float_input" or "X" or something else
# App sends: "input" (hardcoded)

# If mismatch, ONNX returns default output
```

## Immediate Diagnostic

Run the app and check logcat for:
```bash
adb logcat -s ONNXRFScorer:D
```

Look for:
```
ONNX Model Info:
  Input names: [float_input]  ← Check this!
  
🔍 Using input name: 'float_input' with shape [1, 39]  ← Verify this!
🔍 MFCC stats for 'singing': features=39, min=-15.23, max=8.45, avg=-2.34
```

## Solution 1: Add StandardScaler to App

If your training used StandardScaler, you MUST apply the same scaling in the app.

### Step 1: Export Scaler Parameters from Training Notebook

Add this to your notebook AFTER training:
```python
import json

# Get scaler parameters
scaler_params = {
    'mean': scaler.mean_.tolist(),
    'scale': scaler.scale_.tolist()
}

# Save to JSON
with open('scaler_params.json', 'w') as f:
    json.dump(scaler_params, f)

print(f"Scaler mean: {scaler.mean_[:5]}...")  # First 5 values
print(f"Scaler scale: {scaler.scale_[:5]}...")  # First 5 values
```

### Step 2: Copy scaler_params.json to App Assets

```bash
copy scaler_params.json app/src/main/assets/
```

### Step 3: Update ONNXRandomForestScorer.java

I'll add StandardScaler support to the code.

## Solution 2: Retrain Without Scaler

If you don't want to deal with scaling, retrain your model WITHOUT StandardScaler:

```python
# ❌ Remove this:
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)
model.fit(X_scaled, y)

# ✅ Use this instead:
model.fit(X, y)  # Train on raw features
```

Then export to ONNX and replace the model file.

## Solution 3: Verify Feature Count

Check that your app extracts exactly 39 features:

```bash
adb logcat -s ONNXRFScorer:D | grep "features="
```

Should show:
```
🔍 MFCC stats for 'singing': features=39, min=-15.23, max=8.45, avg=-2.34
```

If it shows features=26 or features=52, there's a mismatch!

## Which Solution to Use?

### If you used StandardScaler in training:
→ Use Solution 1 (add scaler to app)

### If you didn't use StandardScaler:
→ Check Solutions 2 and 3 (verify input name and feature count)

### Not sure?
→ Check your training notebook for `StandardScaler` or `scaler.fit_transform`

## Next Steps

1. **Check logcat** for input name and feature count
2. **Check your notebook** for StandardScaler usage
3. **Tell me what you find** and I'll implement the fix
