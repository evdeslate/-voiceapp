# Low Confidence Auto-Incorrect Feature

## Feature
Words with confidence score below 85% are automatically marked as incorrect/mispronounced, regardless of what the RF model predicted.

## Rationale
- Low confidence indicates the speech recognition system is uncertain about what was said
- This typically happens when:
  - Word was skipped entirely
  - Word was mumbled or poorly pronounced
  - Background noise interfered with recognition
  - Student hesitated or paused mid-word
- These cases should be penalized to encourage clear, confident reading

## Implementation
In `StudentDetail.java`, `onRFAnalysisCompleteWithConfidence()` callback:

```java
// Before: Low confidence words could still be marked as correct
boolean isCorrect = wordCorrectness.get(i);
float confidence = (i < wordConfidences.size()) ? wordConfidences.get(i) : 1.0f;
wordCorrect[i] = isCorrect;

// After: Low confidence words are automatically marked as incorrect
boolean isCorrect = wordCorrectness.get(i);
float confidence = (i < wordConfidences.size()) ? wordConfidences.get(i) : 1.0f;

// AUTO-MARK LOW CONFIDENCE AS INCORRECT
if (confidence < CONFIDENCE_THRESHOLD) {  // 0.85
    isCorrect = false;
    lowConfidenceCount++;
    android.util.Log.d("StudentDetail", String.format("  Word %d: Low confidence (%.2f) - marking as INCORRECT", i, confidence));
}

wordCorrect[i] = isCorrect;
```

## Confidence Threshold
- **Threshold**: 85% (0.85)
- **Rationale**: This is a standard threshold in speech recognition systems
  - Above 85%: High confidence, trust the recognition
  - Below 85%: Low confidence, likely skipped or mispronounced

## Impact on Scoring
- **Accuracy Score**: Will decrease if words are skipped or poorly pronounced
- **Pronunciation Score**: Will decrease for low confidence words
- **Reading Level**: May be affected if too many words have low confidence
- **UI Highlighting**: Low confidence words will show as RED (incorrect) in the passage view

## Example Scenarios

### Scenario 1: Word Skipped
- Student reads: "Maria woke up early and looked the window"
- Expected: "Maria woke up early and looked **outside** the window"
- Recognition confidence for "outside": 0.45 (45%)
- **Result**: Word marked as INCORRECT ❌

### Scenario 2: Word Mumbled
- Student reads: "Maria woke up early and looked out...side the window"
- Expected: "Maria woke up early and looked **outside** the window"
- Recognition confidence for "outside": 0.72 (72%)
- **Result**: Word marked as INCORRECT ❌

### Scenario 3: Word Clear
- Student reads: "Maria woke up early and looked outside the window"
- Expected: "Maria woke up early and looked **outside** the window"
- Recognition confidence for "outside": 0.95 (95%)
- **Result**: Word marked as CORRECT ✅ (if RF model agrees)

## Logging
The system logs when words are auto-marked as incorrect:
```
Word 15: Low confidence (0.72) - marking as INCORRECT
```

This helps teachers understand why certain words were marked incorrect even if the student attempted to read them.

## Files Modified
- `app/src/main/java/com/example/speak/StudentDetail.java`
  - Modified `onRFAnalysisCompleteWithConfidence()` to auto-mark low confidence words as incorrect

## Testing
1. Read a passage and intentionally skip a word
2. Check the logs for "Low confidence" messages
3. Verify the skipped word is highlighted in RED
4. Check the accuracy score reflects the skipped word as incorrect
5. Try mumbling a word and verify it's also marked incorrect

## Future Enhancements
- Make the confidence threshold configurable (e.g., 80%, 85%, 90%)
- Provide visual feedback during reading when confidence is low
- Allow teachers to review low confidence words separately
- Add a "retry word" feature for low confidence words
