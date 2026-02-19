# 🎯 Quick RF Model Monitoring

## Fastest Way to See RF Outputs

### Option 1: Use the Batch Script (Easiest)
```bash
monitor_rf_model.bat
```
Then choose option 1 to see all outputs, or option 6 to count CORRECT vs INCORRECT.

### Option 2: Direct Command (Fastest)
```bash
adb logcat -s RF_MODEL_OUTPUT:I
```

---

## 📊 What You'll See

```
RF_MODEL_OUTPUT: 📊 Word: 'the' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
RF_MODEL_OUTPUT: 📊 Word: 'cat' | Class: 1 (CORRECT) | Correct: 78.0% | Incorrect: 22.0%
RF_MODEL_OUTPUT: 📊 Word: 'singing' | Class: 0 (INCORRECT) | Correct: 30.0% | Incorrect: 70.0%
RF_MODEL_OUTPUT: 📊 Word: 'walked' | Class: 0 (INCORRECT) | Correct: 25.0% | Incorrect: 75.0%
```

**Key:**
- `Class: 0` = INCORRECT pronunciation (red highlight)
- `Class: 1` = CORRECT pronunciation (green highlight)
- `Correct %` = Model's confidence in correct pronunciation
- `Incorrect %` = Model's confidence in incorrect pronunciation

---

## 🔍 Filter Specific Results

### See ONLY correct pronunciations:
```bash
adb logcat -s RF_MODEL_OUTPUT:I | findstr "Class: 1"
```

### See ONLY incorrect pronunciations:
```bash
adb logcat -s RF_MODEL_OUTPUT:I | findstr "Class: 0"
```

### Search for specific word:
```bash
adb logcat -s RF_MODEL_OUTPUT:I | findstr "singing"
```

---

## 📈 Quick Diagnostics

### Count classifications:
```bash
adb logcat -d -s RF_MODEL_OUTPUT:I > temp.txt
findstr /C:"Class: 1" temp.txt | find /C /V ""
findstr /C:"Class: 0" temp.txt | find /C /V ""
del temp.txt
```

### Clear logs and start fresh:
```bash
adb logcat -c && adb logcat -s RF_MODEL_OUTPUT:I
```

---

## 🐛 Current Issue: All Words Showing Class 0

If you see ALL words showing `Class: 0 (INCORRECT)`, this means:
- Model is classifying everything as incorrect
- Likely trained on imbalanced data (too many incorrect samples)
- OR feature extraction mismatch between training and runtime

**Next steps:**
1. Run the monitoring command
2. Check if you see ANY `Class: 1` outputs
3. If all are `Class: 0`, the model needs retraining with balanced data

---

## 💡 What to Look For

**Healthy model:**
- Mix of `Class: 0` and `Class: 1`
- Confidence values vary (not all 50% or 80%)
- Correct pronunciations → `Class: 1`
- Mispronunciations → `Class: 0`

**Problem indicators:**
- ALL `Class: 0` → Model too strict
- ALL `Class: 1` → Model too lenient  
- ALL 50% confidence → Model not working
- ALL 80% confidence → Using fallback values

---

## 🚀 Testing Workflow

1. Clear logs: `adb logcat -c`
2. Start monitoring: `adb logcat -s RF_MODEL_OUTPUT:I`
3. Open app and start reading session
4. Pronounce some words correctly, some incorrectly
5. Watch the output - you should see both Class 0 and Class 1
6. If all same class, model needs retraining

---

## 📝 Additional Context Logs

For more detailed context (which word in passage, timestamps):
```bash
adb logcat -s RF_MODEL_OUTPUT:I VoskMFCCRecognizer:D
```

This shows:
- RF model classification
- Word position in passage
- Audio segment timestamps
- Match-based results for comparison
