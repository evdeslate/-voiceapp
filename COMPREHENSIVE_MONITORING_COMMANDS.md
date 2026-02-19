# Comprehensive Monitoring Commands

## All-in-One Monitoring Command

This command monitors ALL relevant components for RF model diagnosis:

```bash
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D ReadingLevelClassifier:D DistilBERTTextAnalyzer:D AudioDenoiser:D
```

## What Each Tag Shows

| Tag | What It Shows |
|-----|---------------|
| `VoskMFCCRecognizer:D` | Word matching, text recognition, RF analysis |
| `StudentDetail:D` | Hybrid mode blending, UI updates, session saving |
| `ONNXRFScorer:D` | ONNX model loading, input names, feature extraction |
| `ONNXRFScorer:W` | ONNX warnings (scaler, feature count) |
| `RF_MODEL_OUTPUT:I` | **RF model predictions** (Class 0/1, confidence) |
| `DirectAudioPronunciationAnalyzer:D` | DirectAudio pronunciation analysis |
| `MFCCPronunciationScorer:D` | MFCC-based pronunciation scoring |
| `ReadingLevelClassifier:D` | Reading level classification |
| `DistilBERTTextAnalyzer:D` | Text analysis with DistilBERT |
| `AudioDenoiser:D` | Audio preprocessing |

## Easy Scripts

### Option 1: Batch File (Simple)
```bash
diagnose_rf_model.bat
```

### Option 2: PowerShell (With Colors)
```bash
.\Diagnose-RFModel.ps1
```

### Option 3: RF Model Only (Minimal)
```bash
monitor_rf_only.bat
```

## What to Look For

### 1. ONNX Model Loading
```
ONNXRFScorer: 🔄 Loading ONNX Random Forest model from: random_forest_mfcc.onnx
ONNXRFScorer: ✅ MFCC extractor initialized
ONNXRFScorer: ✅ Model loaded from assets: 12345 bytes
ONNXRFScorer: ✅✅✅ ONNX Random Forest model loaded successfully and ready!
```

### 2. Model Info (Critical!)
```
ONNXRFScorer: ═══════════════════════════════════════════════════════
ONNXRFScorer: ONNX Model Info:
ONNXRFScorer:   Input count: 1
ONNXRFScorer:   Output count: 2
ONNXRFScorer:   Input names: [mfcc_input]  ← Should match your model
ONNXRFScorer:   Output names: [label, probabilities]
ONNXRFScorer: ═══════════════════════════════════════════════════════
```

### 3. StandardScaler Status
```
ONNXRFScorer: ⚠️ No StandardScaler found (this is OK if model wasn't trained with scaling)
```
OR
```
ONNXRFScorer: ✅ StandardScaler loaded: 39 features
ONNXRFScorer:    Mean range: [-12.34, 5.67]
ONNXRFScorer:    Scale range: [2.34, 8.90]
```

### 4. Feature Extraction
```
ONNXRFScorer: 🔍 MFCC (raw) for 'Maria': features=39, min=-15.23, max=8.45, avg=-2.34
ONNXRFScorer: 🔍 Using input name: 'mfcc_input' with shape [1, 39]
```

### 5. RF Model Predictions (Most Important!)
```
RF_MODEL_OUTPUT: 📊 Word: 'Maria' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
RF_MODEL_OUTPUT: 📊 Word: 'woke' | Class: 1 (CORRECT) | Correct: 82.0% | Incorrect: 18.0%
RF_MODEL_OUTPUT: 📊 Word: 'sinking' | Class: 0 (INCORRECT) | Correct: 35.0% | Incorrect: 65.0%
```

### 6. Hybrid Mode Blending
```
StudentDetail: 🔀 BLENDING VOSK RF + DIRECTAUDIO SCORES
StudentDetail:   Vosk RF results: 47 words
StudentDetail:   DirectAudio results: 47 words
StudentDetail:   Vosk RF: 41 correct, 6 incorrect
StudentDetail:   DirectAudio: 41 correct, 6 incorrect
StudentDetail:   Blended result: 41 correct, 6 incorrect
```

## Filtering Specific Information

### Only RF Model Predictions
```bash
adb logcat -s RF_MODEL_OUTPUT:I
```

### Only ONNX Model Info
```bash
adb logcat -s ONNXRFScorer:D ONNXRFScorer:W
```

### Only Hybrid Mode Blending
```bash
adb logcat -s StudentDetail:D | findstr "BLENDING"
```

### Only Feature Extraction
```bash
adb logcat -s ONNXRFScorer:D | findstr "MFCC"
```

### Only Input Name Info
```bash
adb logcat -s ONNXRFScorer:D | findstr "Input names"
```

## Save to File

```bash
# Save all logs
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D > full_diagnosis.txt

# Save only RF predictions
adb logcat -s RF_MODEL_OUTPUT:I > rf_predictions.txt

# Save only ONNX model info
adb logcat -s ONNXRFScorer:D ONNXRFScorer:W > onnx_model_info.txt
```

## Troubleshooting

### No RF_MODEL_OUTPUT logs?
→ App not rebuilt with updated code. Run `./gradlew clean assembleDebug`

### No ONNXRFScorer logs?
→ Model not loading. Check if `random_forest_mfcc.onnx` exists in assets

### All Class 0 predictions?
→ Check input name mismatch or feature count mismatch

### No StandardScaler logs?
→ Normal if you didn't use StandardScaler in training

## Quick Diagnostic Checklist

Run `diagnose_rf_model.bat` and check:

- [ ] Model loads successfully (✅✅✅)
- [ ] Input name matches (e.g., `mfcc_input`)
- [ ] Feature count is 39
- [ ] StandardScaler status (loaded or not found)
- [ ] RF predictions show BOTH Class 0 and Class 1
- [ ] Confidence varies (not all 50% or 80%)
- [ ] Hybrid mode blending works correctly

If all checked, RF model is working correctly! 🎉
