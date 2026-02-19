# RF Model Output Logging Guide

## 🎯 FASTEST COMMANDS (Copy & Paste)

### Windows Command Prompt:
```bash
adb logcat -s RF_MODEL_OUTPUT:I
```

### Windows PowerShell (with colors):
```powershell
.\Monitor-RFModel.ps1 -Mode all
```

### Batch Script (interactive menu):
```bash
monitor_rf_model.bat
```

---

## 📊 Output Format

Each RF model prediction will show:
```
RF_MODEL_OUTPUT: 📊 Word: 'singing' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
RF_MODEL_OUTPUT: 📊 Word: 'walked' | Class: 0 (INCORRECT) | Correct: 30.0% | Incorrect: 70.0%
```

**Fields explained:**
- **Word**: The expected word being analyzed
- **Class**: 0 = INCORRECT, 1 = CORRECT
- **Correct %**: Model's confidence that pronunciation is correct
- **Incorrect %**: Model's confidence that pronunciation is incorrect

---

## 🚀 PowerShell Script Features (Recommended)

The PowerShell script provides colored output and better diagnostics:

### Monitor all outputs with colors:
```powershell
.\Monitor-RFModel.ps1 -Mode all
```
- Green = CORRECT (class 1)
- Red = INCORRECT (class 0)

### Count and analyze:
```powershell
.\Monitor-RFModel.ps1 -Mode count
```
Shows:
- Total CORRECT vs INCORRECT
- Percentages
- Automatic diagnosis (too strict/lenient/balanced)

### Filter specific word:
```powershell
.\Monitor-RFModel.ps1 -Mode all -Word "singing"
```

### Other modes:
```powershell
.\Monitor-RFModel.ps1 -Mode correct    # Only class 1
.\Monitor-RFModel.ps1 -Mode incorrect  # Only class 0
.\Monitor-RFModel.ps1 -Mode clear      # Clear logs first
```

---

## 🔍 Advanced Filtering

### See RF outputs with timestamps:
```bash
adb logcat -v time -s RF_MODEL_OUTPUT:I
```

### Save RF outputs to file:
```bash
adb logcat -s RF_MODEL_OUTPUT:I > rf_model_outputs.txt
```

### Monitor RF outputs in real-time with color (PowerShell):
```powershell
adb logcat -s RF_MODEL_OUTPUT:I | Select-String -Pattern "CORRECT|INCORRECT"
```

### Count how many CORRECT vs INCORRECT:
```bash
adb logcat -d -s RF_MODEL_OUTPUT:I > temp.txt && (findstr /C:"CORRECT" temp.txt | find /C /V "" && findstr /C:"INCORRECT" temp.txt | find /C /V "") && del temp.txt
```

---

## 🚀 Usage Workflow

1. **Clear old logs:**
   ```bash
   adb logcat -c
   ```

2. **Start monitoring RF outputs:**
   ```bash
   adb logcat -s RF_MODEL_OUTPUT:I
   ```

3. **Run your app** and perform a reading session

4. **Analyze results** - you'll see each word's classification in real-time

---

## 🐛 Troubleshooting

### No output showing?
- Make sure your app is running and processing audio
- Check if device is connected: `adb devices`
- Try: `adb logcat -s RF_MODEL_OUTPUT:* ONNXRFScorer:*`

### Too much output?
- Use `findstr` to filter specific words:
  ```bash
  adb logcat -s RF_MODEL_OUTPUT:I | findstr "singing"
  ```

### Want to see model loading info too?
```bash
adb logcat -s RF_MODEL_OUTPUT:I ONNXRFScorer:D
```

---

## 📈 Expected Behavior

**Healthy model should show:**
- Mix of class=0 and class=1 outputs
- Confidence values varying based on pronunciation quality
- NOT all words showing same class

**Problem indicators:**
- All words showing class=0 (model too strict)
- All words showing class=1 (model too lenient)
- All confidence values at 50% (model not working)

---

## 💡 Quick Diagnostics

### Check if model is balanced:
```bash
adb logcat -d -s RF_MODEL_OUTPUT:I | findstr /C:"Class: 0" /C:"Class: 1"
```

### See confidence distribution:
```bash
adb logcat -d -s RF_MODEL_OUTPUT:I | findstr "Correct:"
```

### Monitor specific word:
```bash
adb logcat -s RF_MODEL_OUTPUT:I | findstr "singing"
```
