# 🚀 START HERE: RF Model Monitoring

## Copy & Paste This Command

```bash
adb logcat -s RF_MODEL_OUTPUT:I
```

Then run your app and start a reading session. You'll see output like:

```
RF_MODEL_OUTPUT: 📊 Word: 'the' | Class: 1 (CORRECT) | Correct: 85.0% | Incorrect: 15.0%
RF_MODEL_OUTPUT: 📊 Word: 'cat' | Class: 0 (INCORRECT) | Correct: 30.0% | Incorrect: 70.0%
```

---

## What You're Looking For

### ✅ Good (Model Working)
- You see BOTH `Class: 0` and `Class: 1`
- Confidence values vary
- Reflects actual pronunciation quality

### ❌ Problem (Model Too Strict) - CURRENT ISSUE
- ALL words show `Class: 0 (INCORRECT)`
- Even correct pronunciations marked as incorrect
- Model needs retraining with balanced data

### ❌ Problem (Model Too Lenient)
- ALL words show `Class: 1 (CORRECT)`
- Even mispronunciations marked as correct
- Model needs more incorrect samples in training

---

## Quick Count

To see how many Class 0 vs Class 1:

```powershell
.\Monitor-RFModel.ps1 -Mode count
```

This will show:
```
CORRECT (class 1):   0
INCORRECT (class 0): 47
TOTAL:               47

❌ ALL words classified as INCORRECT - Model too strict!
```

---

## What Each Part Means

```
📊 Word: 'singing' | Class: 0 (INCORRECT) | Correct: 30.0% | Incorrect: 70.0%
         ↑            ↑                      ↑                  ↑
      Word Name    0=Wrong, 1=Right    Model confidence    Model confidence
```

- **Class 0** = Model says pronunciation is INCORRECT (red in app)
- **Class 1** = Model says pronunciation is CORRECT (green in app)
- **Correct %** = How confident model is that it's correct
- **Incorrect %** = How confident model is that it's incorrect

---

## More Options

See `RF_COMMANDS_CHEATSHEET.txt` for all commands

Or use the interactive tools:
- `monitor_rf_model.bat` - Menu-driven (Windows)
- `.\Monitor-RFModel.ps1 -Mode all` - Colored output (PowerShell)

---

## Files to Read

1. **START_HERE_RF_MONITORING.md** ← You are here
2. **RF_COMMANDS_CHEATSHEET.txt** - Quick command reference
3. **QUICK_RF_MONITORING.md** - Quick start guide
4. **RF_MODEL_LOGGING_GUIDE.md** - Complete documentation
5. **RF_MONITORING_SETUP_COMPLETE.md** - What was changed

---

## Next Steps

1. Run the command: `adb logcat -s RF_MODEL_OUTPUT:I`
2. Open your app and do a reading session
3. Check if you see both Class 0 and Class 1
4. If all Class 0 → Model needs retraining
5. If all Class 1 → Model needs retraining
6. If mixed → Model is working! 🎉
