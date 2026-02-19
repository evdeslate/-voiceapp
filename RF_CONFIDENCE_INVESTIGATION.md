# Random Forest 80% Confidence Issue - Investigation & Fix

## Problem Summary

From the logs, we can see that the ONNX Random Forest model is giving **exactly 80% confidence to ALL words**, regardless of pronunciation quality:

```
Word 1 'maria': ✅ (80% confidence)
Word 2 'up': ✅ (80% confidence)
Word 3 'early': ✅ (80% confidence)
Word 4 'and': ✅ (80% confidence)
```

This indicates the ONNX model is not actually analyzing pronunciation - it's returning a default value.

## Root Cause Analysis

Looking at `ONNXRandomForestScorer.java`, when the model outputs class labels (not probabilities), it uses hardcoded confidence:

```java
} else if (outputValue instanceof long[]) {
    // Model outputs class labels [batch_size]
    long[] output = (long[]) outputValue;
    classification = (int) output[0];
    
    // For label output, use high confidence (0.8) since model is certain
    if (classification == CORRECT_PRONUNCIATION) {
        correctProb = 0.8f;  // ← HARDCODED 80%
        incorrectProb = 0.2f;
    } else {
        correctProb = 0.2f;
        incorrectProb = 0.8f;
    }
}
```

The model is outputting class labels (0 or 1) instead of probabilities, and the code assigns a fixed 80% confidence to all predictions.

## Why This Happens

Random Forest models trained with scikit-learn can output either:
1. **Class labels** (0 or 1) - Just the prediction
2. **Probabilities** (0.0 to 1.0) - Confidence scores

The current ONNX model appears to be exporting only class labels, not probabilities.

## Impact on System

1. **All words get 80% confidence** - No differentiation between good/bad pronunciation
2. **Confidence threshold (≤80%) catches nothing** - Since all words are exactly 80%, the threshold `confidence <= 0.80f` marks ALL words as incorrect
3. **Hybrid mode compensates** - The system uses BOTH text matching AND audio analysis, so only words that pass both are marked correct

## Solutions

### Option 1: Fix ONNX Model Export (Recommended)
Re-export the Random Forest model to include probability outputs:

```python
# When exporting sklearn RandomForest to ONNX
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

initial_type = [('input', FloatTensorType([None, num_features]))]
onx = convert_sklearn(
    rf_model, 
    initial_types=initial_type,
    options={id(rf_model): {'zipmap': False, 'output_class_labels': False}}
)
```

The key is `'output_class_labels': False` which forces probability output.

### Option 2: Adjust Confidence Threshold Logic
Since all words get 80%, adjust the threshold to be more lenient:

```java
// In StudentDetail.java onRFAnalysisCompleteWithConfidence
final float CONFIDENCE_THRESHOLD = 0.79f; // Below 80% (not at or below)
```

This way, only words with confidence < 80% are marked incorrect.

### Option 3: Use Match-Based Scoring Only
Disable RF analysis and rely on text matching:

```java
// In VoskMFCCRecognizer.java calculateFinalScores
boolean onnxReady = false; // Force disable ONNX
```

### Option 4: Implement Proper MFCC Scoring
Use the existing `MFCCPronunciationScorer` which analyzes actual audio features:

```java
// In VoskMFCCRecognizer.java
if (mfccScorer != null && mfccScorer.isReady()) {
    float mfccScore = mfccScorer.scorePronunciation(wordAudio, expectedWord);
    // Use MFCC score instead of ONNX
}
```

## Recommended Fix

**Immediate**: Adjust confidence threshold to < 80% (not ≤ 80%)
**Long-term**: Re-export ONNX model with probability outputs

## Current Workaround

The hybrid approach (text + audio) is actually working correctly:
- Text matching uses strict thresholds (75%/80%)
- Audio analysis marks all as correct (80% confidence)
- Hybrid requires BOTH to agree
- Result: Only words that pass text matching are marked correct

This explains why "singing" vs "sinking" is still being caught - the text matching threshold of 75%/80% is rejecting it (71.4% similarity), even though the RF model marks it as correct.

## Action Items

1. ✅ Understand why RF gives 80% to all words (ONNX label output)
2. ⏳ Fix confidence threshold from ≤ 80% to < 80%
3. ⏳ Increase text matching thresholds if needed
4. 📋 Consider re-exporting ONNX model with probabilities
5. 📋 Test with real pronunciation data
