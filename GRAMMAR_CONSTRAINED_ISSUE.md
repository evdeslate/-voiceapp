# Grammar-Constrained Recognition Issue - Root Cause Found

## The Real Problem

Looking at your logs, I discovered the actual issue:

**Vosk is using grammar-constrained recognition**, which means it ONLY recognizes words from the expected passage. When you mispronounce a word, Vosk "corrects" it to the closest valid word from the grammar.

### Evidence from Logs

```
Grammar: ["maria", "woke", "up", "early", "and", "looked", "outside", ...]
```

When you say:
- "sinking" → Vosk hears "singing" (closest match in grammar)
- "hourly" → Vosk hears "early" (closest match in grammar)
- "worked" → Vosk hears "walked" (closest match in grammar)

All words show **100% exact match** in the logs:
```
Word 17: 'singing' vs 'singing' - ✅ (exact match 100%)
Word 42: 'walked' vs 'walked' - ✅ (exact match 100%)
```

This means the text matching thresholds (80%/85%) are useless because Vosk always gives us the "correct" text!

## Why This Happens

Grammar-constrained recognition was implemented to improve accuracy by limiting Vosk to only recognize expected words. However, this has the unintended consequence of masking mispronunciations.

### The Flow

1. **You say:** "sinking" (mispronunciation)
2. **Vosk hears:** Audio that sounds like "sinking"
3. **Grammar constraint:** Only "singing" is valid (not "sinking")
4. **Vosk outputs:** "singing" (forced to closest valid word)
5. **Text matching:** "singing" vs "singing" = 100% match ✅
6. **Result:** Marked as correct even though you mispronounced it

## Why RF Model Isn't Helping

The ONNX Random Forest model should catch these pronunciation differences by analyzing the actual audio, but:

```
Audio-based: 100.0% (47/47 correct)  ← Marks everything correct
Text correct, Audio incorrect: 0     ← Never disagrees with text
```

The RF model is marking ALL words as correct with 80% confidence, which suggests:
1. The model isn't properly trained to detect mispronunciations
2. The model is outputting class labels (0/1) instead of probabilities
3. The MFCC features aren't capturing pronunciation differences

## Solutions

### Solution 1: Enable Hybrid Mode with DirectAudio (IMPLEMENTED)

I've enabled `useHybridMode = true` which will:

1. **Vosk** - Handles word detection and timing
2. **DirectAudio** - Analyzes actual pronunciation from raw audio
3. **Hybrid Decision** - Word correct ONLY if both agree

DirectAudio doesn't use speech-to-text, so it won't be fooled by grammar constraints. It directly compares the audio waveform of what you said vs. the expected pronunciation.

**Changes made:**
```java
// In StudentDetail.java
private boolean useHybridMode = true; // ENABLED (was false)
```

### Solution 2: Disable Grammar Constraints (Alternative)

Remove grammar constraints so Vosk can recognize any word:

```java
// In VoskMFCCRecognizer.java startRecognition()
// Instead of grammar-constrained recognizer:
Recognizer recognizer = new Recognizer(voskModel, SAMPLE_RATE);
// This allows Vosk to recognize mispronunciations
```

**Pros:**
- Vosk will output actual mispronunciations
- Text matching thresholds will work

**Cons:**
- Lower overall accuracy (more false recognitions)
- May recognize random noise as words
- Harder to align recognized words with expected words

### Solution 3: Fix ONNX Model (Long-term)

Re-train or re-export the ONNX Random Forest model to:
1. Output probabilities instead of class labels
2. Better distinguish between correct and incorrect pronunciation
3. Use more discriminative MFCC features

## Recommended Approach

**Use Hybrid Mode (Solution 1)** - Already implemented!

This combines the best of both worlds:
- Vosk provides accurate word detection and timing (with grammar constraints)
- DirectAudio provides accurate pronunciation assessment (without grammar constraints)
- Hybrid decision ensures both must agree

## Testing the Fix

After rebuilding the app, test with these mispronunciations:

1. Say "sinking" instead of "singing"
2. Say "hourly" instead of "early"
3. Say "worked" instead of "walked"

With hybrid mode enabled, DirectAudio should catch these mispronunciations even though Vosk reports them as correct.

## Expected Behavior

### Before Fix (Grammar-Constrained Only)
```
You say: "sinking"
Vosk outputs: "singing" (forced by grammar)
Text match: "singing" vs "singing" = 100% ✅
RF analysis: 80% confidence ✅
Result: CORRECT ✅ (WRONG!)
```

### After Fix (Hybrid Mode)
```
You say: "sinking"
Vosk outputs: "singing" (forced by grammar)
Text match: "singing" vs "singing" = 100% ✅
DirectAudio: Compares audio of "sinking" vs expected "singing" = MISMATCH ❌
Hybrid: Text says correct BUT audio says incorrect
Result: INCORRECT ❌ (CORRECT!)
```

## Why Hybrid Mode Was Disabled

The comment said "HYBRID MODE DISABLED: Too slow". This was because DirectAudio analyzes every word individually, which takes time. However, this is the ONLY way to catch mispronunciations when using grammar-constrained recognition.

**Trade-off:**
- Slower processing (8-10 seconds for 47 words)
- But accurate pronunciation detection

If speed is critical, consider:
1. Optimizing DirectAudio analysis
2. Using confidence-based sampling (only analyze suspicious words)
3. Disabling grammar constraints (Solution 2)

## Summary

The root cause is **grammar-constrained recognition** forcing Vosk to output valid words even when you mispronounce them. The text matching thresholds can't help because Vosk always gives us "correct" text.

The solution is **hybrid mode** which uses DirectAudio to analyze actual pronunciation from raw audio, bypassing the grammar constraint issue.

I've enabled hybrid mode in the code. Rebuild and test to see if it catches your mispronunciations!
