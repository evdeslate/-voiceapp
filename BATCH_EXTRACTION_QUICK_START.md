# Batch Feature Extraction - Quick Start Guide

## Overview

Extract features from all 1665 WAV files in minutes using the BatchExtractorActivity. This processes files using the exact same TarsosDSP pipeline as production.

## Step 1: Audio Files Already on Phone

Your audio files are already in internal storage:
```
/sdcard/preprocessed_output_v2/
```

Verify they're there:
```bash
adb shell ls /sdcard/preprocessed_output_v2/ | head
adb shell ls /sdcard/preprocessed_output_v2/*.wav | wc -l
```

Expected: 1665 WAV files

**Expected filenames:**
- `31keep_mispronounced.wav`
- `31until_correctlypronounced.wav`
- `32little_mispronounced.wav`
- etc.

The extractor automatically detects labels from filenames:
- Contains "correctlypronounced" or "_correct" → Label 1
- Contains "mispronounced" or "_incorrect" → Label 0

## Step 2: Build and Install App

```powershell
# Automated rebuild and install
.\rebuild-and-test.ps1
```

Or manually:
```powershell
.\gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Step 3: Grant Storage Permissions

### Android 11+ (API 30+)
The app will automatically prompt for "All Files Access" permission:
1. Tap "Start Extraction"
2. You'll be redirected to Settings
3. Toggle on "Allow access to manage all files"
4. Return to the app

### Android 10 and below
Standard storage permissions will be requested automatically.

### Manual Permission Grant (if needed)
```powershell
# Open app settings
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.example.speak
```

Then navigate: Permissions → Files and media → Allow management of all files

## Step 4: Launch Batch Extractor

The BatchExtractorActivity is set as a launcher activity, so it will appear in your app drawer.

1. **Open the app** - You'll see the Batch Feature Extractor screen
2. **Tap "Start Extraction"** - Grant permissions if prompted
3. **Processing begins** - Watch the progress bar

## Step 5: Monitor Progress

Watch the progress bar and status text:

```
Processing: 31keep_mispronounced.wav
100 / 1665
```

**Time estimate:** 5-10 minutes for 1665 files

You can also monitor in logcat:
```powershell
adb logcat -s BatchExtractor:I
```

## Step 6: Retrieve CSV File

Once complete, copy the output file:

```powershell
adb pull /sdcard/mfcc_features.csv C:\Users\Elizha\Downloads\
```

## Step 7: Verify CSV

```python
import pandas as pd

df = pd.read_csv('mfcc_features.csv')
print(f"Total samples: {len(df)}")
print(f"Features: {df.shape[1] - 3}")  # Exclude filename, word, label
print(f"\nClass distribution:")
print(df['label'].value_counts())
print(f"\nFirst few rows:")
print(df.head())
```

Expected output:
```
Total samples: 1665
Features: 39

Class distribution:
1    832
0    833

First few rows:
                      filename   word        f0  ...       f38  label
0  31keep_mispronounced.wav    keep  0.257000  ...  0.575000      0
1  31until_correctlypronounced.wav  until  0.312000  ...  0.591000      1
...
```

## Step 8: Train Model

```python
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# Load features
df = pd.read_csv('mfcc_features.csv')

# Separate features and labels
X = df.iloc[:, 2:41].values.astype(np.float32)  # Columns 2-40 (f0-f38)
y = df['label'].values

print(f"Dataset shape: {X.shape}")
print(f"Class distribution: {np.bincount(y)}")

# Split data
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

# Train Random Forest
rf = RandomForestClassifier(
    n_estimators=100,
    max_depth=10,
    min_samples_split=5,
    min_samples_leaf=2,
    random_state=42,
    n_jobs=-1
)

rf.fit(X_train, y_train)

# Evaluate
y_pred = rf.predict(X_test)
print("\nClassification Report:")
print(classification_report(y_test, y_pred, target_names=['Incorrect', 'Correct']))

print("\nConfusion Matrix:")
cm = confusion_matrix(y_test, y_pred)
print(cm)

# Export to ONNX
initial_type = [('float_input', FloatTensorType([None, 39]))]
onnx_model = convert_sklearn(rf, initial_types=initial_type, target_opset=12)

with open('random_forest_tarsosdsp.onnx', 'wb') as f:
    f.write(onnx_model.SerializeToString())

print("\n✅ Model saved: random_forest_tarsosdsp.onnx")
```

## Step 9: Deploy New Model

### 8.1 Disable Logging Mode

In `ONNXRandomForestScorer.java`:
```java
private static final boolean LOGGING_MODE = false;
```

### 8.2 Remove Batch Extractor from Launcher

In `AndroidManifest.xml`, remove the intent-filter from BatchExtractorActivity:
```xml
<activity
    android:name=".BatchExtractorActivity"
    android:exported="false"
    android:configChanges="fontScale|uiMode" />
```

### 8.3 Replace Model

```bash
cp random_forest_tarsosdsp.onnx app/src/main/assets/random_forest_model_retrained.onnx
```

### 9.4 Build and Test

```powershell
.\gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### Issue: Permission denied / Security exception

**Cause:** Android 11+ requires "All Files Access" permission

**Solution:**
```powershell
# Method 1: Let app request it (recommended)
# Just tap "Start Extraction" and follow prompts

# Method 2: Manual grant via Settings
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.example.speak
# Then: Permissions → Files and media → Allow management of all files

# Method 3: Via adb (may not work on all devices)
adb shell appops set com.example.speak MANAGE_EXTERNAL_STORAGE allow
```

### Issue: "Directory not found"

**Solution:** Verify audio files are in the correct location:
```powershell
adb shell ls /sdcard/preprocessed_output_v2/ | Select-Object -First 10
```

### Issue: "No WAV files found"

**Solution:** Check file count:
```powershell
$count = adb shell "ls /sdcard/preprocessed_output_v2/*.wav 2>/dev/null | wc -l"
Write-Host "WAV files found: $count"
```
Expected: 1665 files

### Issue: "Unknown label for file"

**Solution:** Ensure filenames contain "correctlypronounced" or "mispronounced"

### Issue: Files in different location

**Solution:** Find the correct path:
```powershell
adb shell "find /sdcard -name '*.wav' -path '*/preprocessed_output_v2/*' | head -5"
```

Then update `INPUT_DIR` in `BatchFeatureExtractor.java` if needed.

## What Gets Extracted

For each WAV file, the extractor:
1. ✅ Loads audio (16-bit PCM WAV)
2. ✅ Applies denoising (same as production)
3. ✅ Applies AGC (same as production)
4. ✅ Applies RMS normalization (TARGET_RMS = 0.1)
5. ✅ Extracts MFCC using TarsosDSP
6. ✅ Calculates 39 features (13 means + 13 deltas + 13 delta-deltas)
7. ✅ Writes to CSV: filename, word, f0-f38, label

## CSV Format

```
filename,word,f0,f1,f2,...,f38,label
31keep_mispronounced.wav,keep,0.257000,0.344000,...,0.575000,0
31until_correctlypronounced.wav,until,0.312000,0.428000,...,0.591000,1
```

## Success Criteria

✅ **Extraction successful if:**
- All 1665 files processed
- CSV has 1666 rows (1 header + 1665 data)
- Features are in range [0, 1] (normalized)
- Labels are balanced (~50/50 split)

## Time Estimates

- **Feature extraction:** 5-10 minutes (1665 files)
- **Copy CSV from phone:** 1 minute
- **Train model:** 2 minutes
- **Deploy and test:** 5 minutes
- **Total:** 15-20 minutes

## Why This Works

The batch extractor uses the **exact same code** as production:
- Same TarsosDSP MFCC extraction
- Same preprocessing (denoising, AGC, RMS)
- Same feature calculation (means, deltas, delta-deltas)
- Same normalization (none - features are raw)

This guarantees perfect feature matching between training and production.

---

**Status:** ✅ READY TO USE

All files created and ready for batch extraction!
