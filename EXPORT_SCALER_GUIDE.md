# Export StandardScaler Parameters - Step by Step

## Why This is Critical

If your training notebook used `StandardScaler`, the model expects scaled features:
```python
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)  # Features scaled to mean=0, std=1
model.fit(X_scaled, y)
```

But your app sends RAW features → Model sees wrong data → Predicts default class (0)

## Step 1: Check if You Used StandardScaler

Open your training notebook: `C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb`

Search for:
- `StandardScaler`
- `scaler.fit_transform`
- `scaler.transform`

If you find these, you MUST export the scaler parameters.

## Step 2: Export Scaler Parameters

Add this cell to your notebook AFTER training:

```python
import json
import numpy as np

# Check if scaler exists
if 'scaler' in locals() or 'scaler' in globals():
    print("✅ StandardScaler found!")
    
    # Get scaler parameters
    scaler_params = {
        'mean': scaler.mean_.tolist(),
        'scale': scaler.scale_.tolist()
    }
    
    # Save to JSON
    with open('scaler_params.json', 'w') as f:
        json.dump(scaler_params, f, indent=2)
    
    print(f"✅ Scaler parameters saved to scaler_params.json")
    print(f"   Features: {len(scaler.mean_)}")
    print(f"   Mean range: [{np.min(scaler.mean_):.2f}, {np.max(scaler.mean_):.2f}]")
    print(f"   Scale range: [{np.min(scaler.scale_):.2f}, {np.max(scaler.scale_):.2f}]")
    
    # Show first 5 values as example
    print(f"\n   First 5 means: {scaler.mean_[:5]}")
    print(f"   First 5 scales: {scaler.scale_[:5]}")
    
else:
    print("❌ No StandardScaler found!")
    print("   If you didn't use StandardScaler, you can skip this step.")
```

## Step 3: Copy scaler_params.json to App

After running the cell above, you'll have `scaler_params.json` in the same folder as your notebook.

Copy it to your app's assets folder:

```bash
copy "C:\Users\Elizha\Downloads\scaler_params.json" "app\src\main\assets\"
```

## Step 4: Verify the File

Check that the file exists:
```bash
dir app\src\main\assets\scaler_params.json
```

The file should look like this:
```json
{
  "mean": [
    -12.345,
    2.456,
    -0.123,
    ...
  ],
  "scale": [
    5.678,
    3.456,
    2.345,
    ...
  ]
}
```

## Step 5: Rebuild and Test

The app will automatically detect and load the scaler parameters.

Check logcat:
```bash
adb logcat -s ONNXRFScorer:D
```

You should see:
```
✅ StandardScaler loaded: 39 features
   Mean range: [-15.23, 8.45]
   Scale range: [2.34, 12.56]
```

And when processing words:
```
🔍 MFCC (raw) for 'singing': features=39, min=-15.23, max=8.45, avg=-2.34
🔍 MFCC (scaled) for 'singing': features=39, min=-1.23, max=1.45, avg=0.12
```

## Alternative: Retrain Without Scaler

If you don't want to deal with the scaler, you can retrain your model WITHOUT it:

```python
# ❌ Remove this:
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)
model.fit(X_scaled, y)

# ✅ Use this instead:
model.fit(X, y)  # Train on raw MFCC features
```

Then export to ONNX and replace the model file.

## Troubleshooting

### Issue: "No StandardScaler found"
- You didn't use StandardScaler in training
- No need to export scaler parameters
- App will use raw features (correct behavior)

### Issue: "Feature count mismatch"
- Scaler expects 39 features but app extracts different number
- Check MFCC extraction parameters (should be 13 coefficients × 3 stats = 39)

### Issue: Model still predicts all Class 0
- Check input name mismatch (see next section)
- Verify feature extraction matches training
- Check class balance in training data

## Next: Check Input Name

After fixing the scaler, check if the ONNX input name matches:

```bash
adb logcat -s ONNXRFScorer:D | grep "Input names"
```

Should show:
```
Input names: [float_input]  ← or whatever your model expects
```

The app now automatically uses the correct input name from the model.
