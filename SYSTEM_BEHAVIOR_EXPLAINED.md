# System Behavior - Complete Explanation

## How Word Scoring Works

The system uses a **hybrid approach** with two independent methods that must both agree:

### Method 1: Text Matching (Vosk Speech Recognition)
1. Vosk recognizes spoken words and converts them to text
2. System compares recognized text with expected text
3. Uses strict similarity thresholds:
   - First letter match + similar length: 80%
   - Fuzzy match (no first letter): 85%
   - Phonetic match: 85%
4. Marks word as correct/incorrect based on text similarity

### Method 2: Audio Analysis (ONNX Random Forest)
1. Captures raw audio for each word
2. Extracts MFCC features from audio
3. Runs ONNX Random Forest model to analyze pronunciation
4. Returns classification (correct/incorrect) and confidence
5. **Current limitation:** Returns 80% confidence for all words

### Hybrid Decision
A word is marked correct ONLY if:
- Text matching says correct AND
- Audio analysis says correct

If either method says incorrect, the word is marked incorrect.

## Why You See "80% confidence" for Everything

The ONNX Random Forest model was exported with class label output instead of probability output. This means:

```
Input: Audio features
Output: 0 (incorrect) or 1 (correct)
```

When the code receives a class label, it assigns a fixed confidence:
```java
if (classification == CORRECT_PRONUNCIATION) {
    correctProb = 0.8f;  // ← Always 80%
    incorrectProb = 0.2f;
} else {
    correctProb = 0.2f;
    incorrectProb = 0.8f;  // ← Always 80%
}
```

This is why every word shows exactly 80% confidence in the logs.

## Why the System Still Works

Even though the RF model returns 80% for everything, the system still catches mispronunciations because:

1. **Text matching is strict** (80%/85% thresholds)
   - "singing" vs "sinking" = 71.4% → REJECTED by text matching
   - "early" vs "hourly" = 66.7% → REJECTED by text matching

2. **Hybrid mode requires both to agree**
   - If text matching rejects, word is incorrect (regardless of RF)
   - If RF rejects, word is incorrect (regardless of text matching)

3. **RF still provides classification**
   - Even though confidence is always 80%, the classification (0 or 1) is still useful
   - RF can mark a word as incorrect even if text matching accepts it

## Understanding the Logs

### Intermediate Results (Can Be Ignored)
```
D  Intermediate result: 'maria way up early and the'
D  Processing recognized text: 'maria way up early and the'
D  ⚠️  Unmatched word: 'maria' (possible insertion/noise)
D  ⚠️  Unmatched word: 'way' (possible insertion/noise)
```

These are from Vosk's intermediate recognition results. The system reprocesses the same text multiple times as Vosk refines its recognition. The "unmatched word" warnings don't affect final scoring.

### Word Matching Decisions (Important)
```
D  Word 7: 'the' vs 'the' - match-based: ✅ (87%)
D  Word 7: 'the' vs 'the' - ✅ (exact match 100%, instant score: 87%)
```

This shows the text matching decision. The word "the" was recognized correctly with 87% match score.

### RF Analysis Results (Important)
```
D    Word 1 'maria': ✅ (80% confidence)
D    Word 2 'up': ✅ (80% confidence)
D    Word 3 'early': ✅ (80% confidence)
```

This shows the RF analysis results. All words get 80% confidence due to the model limitation.

### Final Scoring (Most Important)
```
D  ✅ Updated 47 words with RF results
D     Correct: 41, Incorrect: 6
D     Low confidence (< 0.80): 0
```

This shows the final word counts after combining text matching and RF analysis.

## Why Some Words Are Marked Incorrect

A word can be marked incorrect for several reasons:

### 1. Text Matching Rejected It
- Similarity below threshold (80% or 85%)
- Example: "singing" vs "sinking" = 71.4% < 80%

### 2. RF Analysis Rejected It
- ONNX model classified it as incorrect (class 0)
- Even though confidence shows 80%, the classification is still used

### 3. Word Was Skipped
- Vosk didn't recognize the word at all
- Automatically marked as incorrect with 20% score

### 4. Low Confidence (Currently Not Triggered)
- If confidence < 80%, mark as incorrect
- Currently doesn't trigger because all words get exactly 80%

## Confidence Threshold Behavior

```java
final float CONFIDENCE_THRESHOLD = 0.80f;

if (confidence < CONFIDENCE_THRESHOLD) {
    isCorrect = false;  // Mark as incorrect
}
```

**Current behavior:**
- All words get 80% confidence from RF
- Threshold is `< 0.80` (strictly less than)
- No words are marked incorrect by this check (80% is not < 80%)

**Previous behavior (bug):**
- Threshold was `<= 0.80` (less than or equal)
- ALL words were marked incorrect (80% <= 80%)
- This was causing the discrepancy

## Expected Results

With the current fix, you should see:

### Correct Words
- Clear pronunciation matching expected text
- Similarity ≥ 80% (first letter match) or ≥ 85% (fuzzy match)
- RF classifies as correct (class 1)

### Incorrect Words
- Mispronunciations like "singing"→"sinking"
- Similarity < 80% or < 85%
- RF classifies as incorrect (class 0)
- Skipped words (not recognized by Vosk)

## Monitoring Commands

### See Word Matching Decisions
```bash
adb logcat -s VoskMFCCRecognizer:D | findstr "Word.*vs"
```

### See RF Analysis
```bash
adb logcat -s VoskMFCCRecognizer:D | findstr "confidence"
```

### See Final Counts
```bash
adb logcat -s StudentDetail:D | findstr "Updated.*words"
```

### See All Speech Processing
```bash
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D
```

## Summary

The system uses a hybrid approach (text + audio) to catch mispronunciations. Even though the RF model returns 80% confidence for all words, the system still works because:

1. Text matching uses strict thresholds (80%/85%)
2. RF still provides useful classification (correct/incorrect)
3. Hybrid mode requires both to agree
4. Confidence threshold is now `< 0.80` (not `<= 0.80`)

The "unmatched word" warnings in logs are from intermediate processing and don't affect final scoring. The system should now correctly reject mispronunciations like "singing"→"sinking" and "early"→"hourly".
