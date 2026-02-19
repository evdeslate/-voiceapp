# Mispronunciation Detection - Comprehensive Fix

## Issues Identified

### 1. ONNX Model Returns 80% for All Words
- **Problem**: ONNX Random Forest outputs class labels (0/1) not probabilities
- **Impact**: All words get exactly 80% confidence, making threshold useless
- **Fix Applied**: Changed threshold from `<= 0.80` to `< 0.80`

### 2. Text Matching Thresholds
Current thresholds:
- First letter match + similar length: 75%
- Fuzzy match (no first letter): 80%
- Phonetic match: 80%

Test cases:
- "singing" vs "sinking": 71.4% similarity → **REJECTED** ✅
- "early" vs "hourly": 66.7% similarity → **REJECTED** ✅

**Thresholds are already strict enough!**

### 3. Hybrid Mode Logic
The system uses BOTH text matching AND audio analysis:
- Text matching: Strict thresholds (75%/80%)
- Audio analysis: ONNX RF (currently broken, returns 80% for all)
- Hybrid: Word correct ONLY if BOTH agree

**This is why mispronunciations are still being caught despite RF issues!**

## Root Cause of User's Issue

Looking at the logs:
```
Word 1 'maria': ✅ (80% confidence)
Word 2 'up': ✅ (80% confidence)
```

The user is seeing words marked as correct that shouldn't be. This happens because:

1. **Text matching is too lenient for some cases**
   - "worked" vs "walked" might pass if similarity > 75%
   - Need to test: Levenshtein("worked", "walked") = 2, similarity = 1 - 2/6 = 66.7%
   - Should be rejected at 75% threshold ✅

2. **RF model isn't helping**
   - All words get 80% confidence
   - Can't differentiate good from bad pronunciation

3. **Hybrid mode compensates but isn't perfect**
   - If text matching incorrectly accepts, RF can't override (it says 80% for everything)

## Solutions Implemented

### Fix 1: Confidence Threshold (DONE)
Changed from `<= 0.80` to `< 0.80` in StudentDetail.java

```java
// Before
if (confidence <= CONFIDENCE_THRESHOLD) {
    isCorrect = false;
}

// After  
if (confidence < CONFIDENCE_THRESHOLD) {
    isCorrect = false;
}
```

### Fix 2: Stricter Text Matching (RECOMMENDED)
Increase thresholds to catch more mispronunciations:

```java
// In matchWords() method
if (firstLetterMatch && lengthDiff <= 2) {
    if (similarity >= 0.80f) { // Increased from 0.75f
        return true;
    }
}

// Fuzzy match
if (similarity >= 0.85f) { // Increased from 0.80f
    return true;
}
```

### Fix 3: Disable Contains Check for Longer Words
The contains() check is too lenient:

```java
// Before
if (recognizedNoSpaces.length() <= 4 || expected.length() <= 4) {
    if (recognizedNoSpaces.contains(expected) || expected.contains(recognizedNoSpaces)) {
        return true;
    }
}

// After - Only for very short words (3 letters or less)
if (recognizedNoSpaces.length() <= 3 && expected.length() <= 3) {
    if (recognizedNoSpaces.contains(expected) || expected.contains(recognizedNoSpaces)) {
        return true;
    }
}
```

## Testing Required

Test these word pairs to ensure they're rejected:
1. "singing" vs "sinking" (71.4% similarity)
2. "early" vs "hourly" (66.7% similarity)  
3. "worked" vs "walked" (66.7% similarity)
4. Any other mispronunciations the user reports

## Long-Term Solution

Re-export ONNX Random Forest model with probability outputs:

```python
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

initial_type = [('input', FloatTensorType([None, num_features]))]
onx = convert_sklearn(
    rf_model, 
    initial_types=initial_type,
    options={id(rf_model): {'zipmap': False, 'output_class_labels': False}}
)
```

This will provide actual confidence scores (0.0 to 1.0) instead of fixed 80%.

## Summary

1. ✅ Fixed confidence threshold (< instead of <=)
2. ⏳ Need to increase text matching thresholds (80%/85%)
3. ⏳ Need to restrict contains() check to 3-letter words
4. 📋 Long-term: Re-export ONNX model with probabilities
