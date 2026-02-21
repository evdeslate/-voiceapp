# Permission Fix Guide - Batch Extraction

## Problem
The app couldn't access `/sdcard/preprocessed_output_v2/` because Android 11+ requires special "All Files Access" permission.

## What Was Fixed

### 1. AndroidManifest.xml Changes
- Added `android:requestLegacyExternalStorage="true"` for Android 10 compatibility
- Added `maxSdkVersion="32"` to READ/WRITE_EXTERNAL_STORAGE (they're deprecated on Android 13+)
- Kept MANAGE_EXTERNAL_STORAGE for Android 11+

### 2. BatchExtractorActivity.java Changes
- Updated permission checking to handle Android 11+ (API 30+)
- Android 11+: Requests MANAGE_EXTERNAL_STORAGE via Settings
- Android 10 and below: Uses legacy READ/WRITE_EXTERNAL_STORAGE

## Steps to Fix

### Step 1: Rebuild and Install
```powershell
cd C:\Users\Elizha\AndroidStudioProjects\SPEAK
.\gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Grant Permissions

#### For Android 11+ (API 30+):
The app will automatically open Settings when you tap "Start Extraction". Toggle on "Allow access to manage all files".

#### Manual Method (if needed):
```powershell
# Open app settings
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.example.speak

# Then manually:
# Settings → Apps → SPEAK → Permissions → Files and media → Allow management of all files
```

### Step 3: Verify Directory Access
```powershell
# Check if directory exists and has files
adb shell ls -la /sdcard/preprocessed_output_v2/ | Select-Object -First 20
```

### Step 4: Launch Batch Extractor
The app should now launch directly to BatchExtractorActivity (it's set as LAUNCHER in manifest).

1. Tap "Start Extraction"
2. Grant permission when prompted
3. Wait for processing (1665 files)
4. Check progress bar and status

### Step 5: Retrieve Output
```powershell
# Copy the CSV file to your PC
adb pull /sdcard/mfcc_features.csv C:\Users\Elizha\Downloads\

# Verify file size
Get-Item C:\Users\Elizha\Downloads\mfcc_features.csv
```

## Expected Output

The CSV will have this format:
```
filename,word,f0,f1,f2,...,f38,label
31keep_mispronounced.wav,keep,-245.123,12.456,...,1.234,0
31until_correctlypronounced.wav,until,-198.765,8.901,...,2.345,1
```

- 1665 rows (one per audio file)
- 42 columns: filename, word, 39 features (f0-f38), label
- Labels: 0 = mispronounced, 1 = correct

## Troubleshooting

### If permission still denied:
```powershell
# Check current permissions
adb shell dumpsys package com.example.speak | Select-String "permission"

# Force grant (may not work on Android 11+)
adb shell appops set com.example.speak MANAGE_EXTERNAL_STORAGE allow
```

### If directory not found:
```powershell
# List all directories in /sdcard/
adb shell ls -la /sdcard/ | Select-String "preprocessed"

# If in different location, update INPUT_DIR in BatchFeatureExtractor.java
```

### If app crashes:
```powershell
# View crash logs
adb logcat -s BatchExtractor:* AndroidRuntime:E
```

## Next Steps After Extraction

1. Train new model using the extracted CSV
2. Replace `random_forest_model_retrained.onnx` in assets
3. Deploy updated app
4. Test pronunciation scoring with real TarsosDSP features
