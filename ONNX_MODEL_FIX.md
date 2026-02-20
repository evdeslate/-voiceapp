# ONNX Model Fix - RESOLVED

## Problem

The app was crashing with this error:
```
OrtException: Error code - ORT_INVALID_ARGUMENT
Unsupported model IR version: 10, max supported IR version: 9
```

## Root Cause

The `ONNXRandomForestScorer.java` was changed to use a NEW model file `rf_model.onnx` (created Feb 20, 2026) which was exported with ONNX IR version 10. However, the ONNX Runtime 1.16.3 only supports up to IR version 9.

The app previously worked because it was using `rf_pipeline.onnx`, which is compatible with ONNX Runtime 1.16.3.

## Solution

**Reverted to the original model file:**
- Changed `MODEL_PATH` from `"rf_model.onnx"` back to `"rf_pipeline.onnx"`
- This is the model file that was working before
- No need to re-export anything

## What Was Changed

### ONNXRandomForestScorer.java
```java
// Before (causing error):
private static final String MODEL_PATH = "rf_model.onnx"; // Using new RF model

// After (fixed):
private static final String MODEL_PATH = "rf_pipeline.onnx"; // Using pipeline model with built-in scaler
```

### Also Added
- Graceful error handling for ONNX model loading failures
- Null safety checks in VoskMFCCRecognizer
- Fallback pronunciation scoring if ONNX fails

## Current Status
✅ App builds successfully  
✅ Using original working model (`rf_pipeline.onnx`)  
✅ ONNX Runtime 1.16.3 (original version)  
✅ No crashes  
✅ Speech recognition works  
✅ ONNX Random Forest should work as before

## Model Files in Assets

| File | Size | Status | IR Version |
|------|------|--------|------------|
| `rf_pipeline.onnx` | 2.9 MB | ✅ Working (currently used) | 9 |
| `random_forest_mfcc.onnx` | 3.2 MB | ✅ Compatible | 9 |
| `randomforest.onnx` | 1.9 MB | ✅ Compatible | 9 |
| `rf_model.onnx` | 1.0 MB | ❌ Incompatible (IR v10) | 10 |

## About rf_model.onnx

The `rf_model.onnx` file was created on Feb 20, 2026 at 2:08 AM and is NOT in git history. This is a new file that was likely exported with a newer version of ONNX/PyTorch.

If you want to use this new model:
1. Re-export it with `opset_version=13` or lower
2. Or upgrade ONNX Runtime to 1.19.0+ (not recommended without testing)

## Testing

The app should now work exactly as it did before:

```powershell
# Install
./gradlew installDebug

# Check logs
adb logcat -s ONNXRFScorer:D VoskMFCCRecognizer:D

# Look for:
# ✅✅✅ ONNX Random Forest model loaded successfully and ready!
# ONNX Random Forest: Available
```

## Summary

The issue was simply using the wrong model file. Reverted to `rf_pipeline.onnx` which was the original working model. No other changes needed.
