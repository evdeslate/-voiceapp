# Typo Fix - "mispronunced" vs "mispronounced"

## Issue Found

23 files were skipped during batch extraction. Investigation revealed:
- 21 files have typo: `mispronunced` (missing 'o')
- 2 files may have other issues (too short, invalid format, etc.)

## Root Cause

The label extraction logic only checked for:
- `mispronounced` (correct spelling)
- `correctlypronounced`

But some files were named with the typo:
- `6behind_mispronunced.wav`
- `6eaten_mispronunced.wav`
- `6his_mispronunced.wav`
- `6snail_mispronunced.wav`
- `6until_mispronunced.wav`
- ... (21 files total)

## Fix Applied

Updated `BatchFeatureExtractor.java`:

### extractLabel() method
```java
if (lower.contains("mispronounced") || lower.contains("mispronunced") || lower.contains("_incorrect")) {
    return 0;  // Handle both spellings
}
```

### extractWord() method
```java
name = name.replace("_mispronounced", "")
           .replace("_mispronunced", "")  // Handle typo
           .replace("_correctlypronounced", "")
           ...
```

## Expected Results After Fix

### Before Fix
- Processed: 1642
- Skipped: 23
- Total: 1665

### After Fix
- Processed: ~1663-1665
- Skipped: 0-2
- Total: 1665

The remaining 0-2 skipped files (if any) would be due to:
- Audio too short (< 3200 samples = 0.2 seconds)
- Invalid WAV format
- Corrupted file

## How to Apply Fix

```powershell
# Quick rebuild and reinstall
.\rebuild-quick.ps1

# Or manually
.\gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch and run extraction again
adb shell am start -n com.example.speak/.BatchExtractorActivity
```

## Verification

After running extraction again:

```powershell
# Check the CSV
adb pull /sdcard/mfcc_features.csv

# Count rows (should be 1664-1666: 1 header + 1663-1665 data)
(Get-Content mfcc_features.csv).Count

# Check label distribution
python -c "import pandas as pd; df = pd.read_csv('mfcc_features.csv'); print(df['label'].value_counts())"
```

Expected output:
```
1    832  # Correctly pronounced
0    831  # Mispronounced (including typo files)
```

## Files Affected by Typo

All files with speaker ID "6" appear to have the typo:
- `6behind_mispronunced.wav`
- `6eaten_mispronunced.wav`
- `6his_mispronunced.wav`
- `6snail_mispronunced.wav`
- `6until_mispronunced.wav`
- ... (21 total)

This suggests the typo was introduced during the preprocessing/labeling of speaker 6's recordings.

## Impact on Training

With the fix:
- 21 more samples will be included in training
- Better representation of speaker 6's pronunciation patterns
- More balanced dataset (if speaker 6 had mostly mispronunciations)

## Next Steps

1. Run `.\rebuild-quick.ps1`
2. Tap "Start Extraction" in the app
3. Verify processed count is ~1663-1665
4. Pull the CSV and verify row count
5. Proceed with model training

---

**Status:** ✅ FIXED - Ready to rebuild and re-run extraction
