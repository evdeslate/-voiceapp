# RF Model Visual Guide

## What's Happening Now

```
┌─────────────────────────────────────────────────────────────┐
│                    YOUR APP FLOW                             │
└─────────────────────────────────────────────────────────────┘

User speaks: "The cat walked home"
      ↓
Audio captured (16000 Hz, mono)
      ↓
MFCC features extracted (39 features per word)
      ↓
RF Model (random_forest_mfcc.onnx)
      ↓
┌─────────────────────────────────────────────────────────────┐
│  ❌ PROBLEM: Model predicts Class 0 for EVERYTHING          │
│                                                              │
│  Word: "the"    → Class 0 (INCORRECT) 20% confidence        │
│  Word: "cat"    → Class 0 (INCORRECT) 20% confidence        │
│  Word: "walked" → Class 0 (INCORRECT) 20% confidence        │
│  Word: "home"   → Class 0 (INCORRECT) 20% confidence        │
└─────────────────────────────────────────────────────────────┘
      ↓
All words highlighted RED in UI
      ↓
User frustrated (everything marked wrong!)
```

## What Should Happen

```
┌─────────────────────────────────────────────────────────────┐
│                  EXPECTED BEHAVIOR                           │
└─────────────────────────────────────────────────────────────┘

User speaks: "The cat walked home" (good pronunciation)
      ↓
Audio captured (16000 Hz, mono)
      ↓
MFCC features extracted (39 features per word)
      ↓
RF Model (random_forest_mfcc.onnx)
      ↓
┌─────────────────────────────────────────────────────────────┐
│  ✅ HEALTHY: Model discriminates between good/bad            │
│                                                              │
│  Word: "the"    → Class 1 (CORRECT)   85% confidence  ✅    │
│  Word: "cat"    → Class 1 (CORRECT)   82% confidence  ✅    │
│  Word: "walked" → Class 1 (CORRECT)   88% confidence  ✅    │
│  Word: "home"   → Class 1 (CORRECT)   80% confidence  ✅    │
└─────────────────────────────────────────────────────────────┘
      ↓
Words highlighted GREEN in UI
      ↓
User happy!

───────────────────────────────────────────────────────────────

User speaks: "The cat worked home" (mispronounced "walked")
      ↓
RF Model detects mispronunciation
      ↓
┌─────────────────────────────────────────────────────────────┐
│  Word: "the"    → Class 1 (CORRECT)     85% confidence  ✅  │
│  Word: "cat"    → Class 1 (CORRECT)     82% confidence  ✅  │
│  Word: "walked" → Class 0 (INCORRECT)   35% confidence  ❌  │
│  Word: "home"   → Class 1 (CORRECT)     80% confidence  ✅  │
└─────────────────────────────────────────────────────────────┘
      ↓
"walked" highlighted RED, others GREEN
      ↓
User gets accurate feedback!
```

## Why Your Model is Broken

```
┌─────────────────────────────────────────────────────────────┐
│              TRAINING PROCESS (LIKELY ISSUE)                 │
└─────────────────────────────────────────────────────────────┘

Step 1: Collect training data
  ├─ Correct pronunciations: 500 samples
  └─ Incorrect pronunciations: 500 samples

Step 2: Train model
  ❌ WRONG: Train and test on SAME data
     model.fit(X, y)
     accuracy = model.score(X, y)  # 100% accuracy!
     
     Result: Model memorizes training data
             When it sees NEW audio → defaults to Class 0

  ✅ CORRECT: Train and test on DIFFERENT data
     X_train, X_test, y_train, y_test = train_test_split(
         X, y, test_size=0.2, stratify=y
     )
     model.fit(X_train, y_train)
     accuracy = model.score(X_test, y_test)  # 70-90% accuracy
     
     Result: Model generalizes to new audio
             Correctly predicts both Class 0 and Class 1

Step 3: Export to ONNX
  └─ If model is broken, ONNX will be broken too!
```

## How to Diagnose

```
┌─────────────────────────────────────────────────────────────┐
│                  MONITORING WORKFLOW                         │
└─────────────────────────────────────────────────────────────┘

1. Run monitoring script:
   > monitor_rf_only.bat

2. Open your app and read a passage with GOOD pronunciation

3. Watch the output:

   ❌ BROKEN MODEL (Current State):
   ┌──────────────────────────────────────────────────────────┐
   │ 📊 Word: 'the'    | Class: 0 (INCORRECT) | Correct: 20% │
   │ 📊 Word: 'cat'    | Class: 0 (INCORRECT) | Correct: 20% │
   │ 📊 Word: 'walked' | Class: 0 (INCORRECT) | Correct: 20% │
   │ 📊 Word: 'home'   | Class: 0 (INCORRECT) | Correct: 20% │
   └──────────────────────────────────────────────────────────┘
   All Class 0 → Model is broken!

   ✅ HEALTHY MODEL (Expected):
   ┌──────────────────────────────────────────────────────────┐
   │ 📊 Word: 'the'    | Class: 1 (CORRECT)   | Correct: 85% │
   │ 📊 Word: 'cat'    | Class: 1 (CORRECT)   | Correct: 82% │
   │ 📊 Word: 'walked' | Class: 1 (CORRECT)   | Correct: 88% │
   │ 📊 Word: 'home'   | Class: 1 (CORRECT)   | Correct: 80% │
   └──────────────────────────────────────────────────────────┘
   Mix of Class 0 and Class 1 → Model is working!
```

## The Fix

```
┌─────────────────────────────────────────────────────────────┐
│                  RETRAINING WORKFLOW                         │
└─────────────────────────────────────────────────────────────┘

1. Open your training notebook:
   C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb

2. Check class distribution:
   print(y.value_counts())
   
   Should be balanced:
   0    500  ← Incorrect pronunciations
   1    500  ← Correct pronunciations

3. Use proper train/test split:
   X_train, X_test, y_train, y_test = train_test_split(
       X, y, 
       test_size=0.2,    # 80% train, 20% test
       stratify=y,       # ← CRITICAL: Maintains class balance
       random_state=42
   )

4. Train on training set:
   model.fit(X_train, y_train)

5. Evaluate on TEST set (not training set!):
   test_accuracy = model.score(X_test, y_test)
   print(f"Test accuracy: {test_accuracy:.2%}")
   
   Should be 70-90%, NOT 100%

6. Check test predictions:
   y_pred = model.predict(X_test)
   print(f"Class 0: {sum(y_pred == 0)}")
   print(f"Class 1: {sum(y_pred == 1)}")
   
   Should show BOTH classes, not just one!

7. Export to ONNX:
   # Your existing export code

8. Copy to app:
   copy random_forest_mfcc.onnx app/src/main/assets/

9. Test in app:
   > monitor_rf_only.bat
   Read a passage and verify you see both Class 0 and Class 1
```

## Quick Reference

| Symptom | Cause | Fix |
|---------|-------|-----|
| All Class 0 | Model biased toward incorrect | Retrain with balanced data |
| All Class 1 | Model biased toward correct | Retrain with balanced data |
| All 50% confidence | Model not loaded/working | Check model file and logs |
| Mix of 0 and 1 | ✅ Model is healthy | No fix needed! |

## Next Steps

1. **Monitor**: Run `monitor_rf_only.bat`
2. **Test**: Read a passage in the app
3. **Report**: Tell me what you see (all Class 0? mix of 0 and 1?)
4. **Copy**: `copy "C:\Users\Elizha\Downloads\random_forest_mfcc_onnx.ipynb" .`
5. **Fix**: I'll analyze your notebook and tell you exactly what to change
