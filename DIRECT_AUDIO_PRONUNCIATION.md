# Direct Audio Pronunciation Analysis

## Overview

A new, simpler approach for pronunciation assessment that captures and analyzes audio directly without speech recognition.

## Why This Approach?

### Problems with Speech Recognition (Vosk)
- **Normalization**: Speech-to-text normalizes pronunciation (e.g., "sing-ging" → "singing")
- **Complexity**: Multiple components (Vosk model, speech recognition, text matching)
- **Size**: Large model files (~50MB)
- **Indirect**: Analyzes transcribed text, not actual pronunciation

### Benefits of Direct Audio Analysis
✅ **No normalization** - Analyzes raw pronunciation exactly as spoken
✅ **Simpler** - Single component, fewer moving parts
✅ **Smaller** - No large speech recognition models needed
✅ **Direct** - Analyzes audio directly, not transcribed text
✅ **Offline** - Fully offline, no internet needed
✅ **Focused** - Purpose-built for pronunciation assessment

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    User Reads Passage                        │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│          DirectAudioPronunciationAnalyzer                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  1. Capture Audio (AudioRecord)                       │  │
│  │     - 16kHz sample rate                               │  │
│  │     - Mono channel                                    │  │
│  │     - Continuous recording                            │  │
│  └───────────────────────────────────────────────────────┘  │
│                      │                                       │
│                      ▼                                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  2. Denoise Audio (AudioDenoiser)                     │  │
│  │     - Remove background noise                         │  │
│  │     - Improve signal quality                          │  │
│  └───────────────────────────────────────────────────────┘  │
│                      │                                       │
│                      ▼                                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  3. Segment Audio                                     │  │
│  │     - Divide by expected word count                   │  │
│  │     - Equal time per word                             │  │
│  └───────────────────────────────────────────────────────┘  │
│                      │                                       │
│                      ▼                                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  4. Analyze Each Segment (ONNX Random Forest)        │  │
│  │     - Extract MFCC features                           │  │
│  │     - Run pronunciation model                         │  │
│  │     - Get correctness + confidence                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                      │                                       │
│                      ▼                                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  5. Real-time Callback                                │  │
│  │     - onWordAnalyzed(index, correct, confidence)      │  │
│  │     - Update UI immediately                           │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              Final Results & UI Update                       │
│  - Overall pronunciation score                               │
│  - Per-word correctness                                      │
│  - Green/red highlighting                                    │
└─────────────────────────────────────────────────────────────┘
```

## Implementation

### 1. Class: DirectAudioPronunciationAnalyzer

**Location**: `app/src/main/java/com/example/speak/DirectAudioPronunciationAnalyzer.java`

**Key Methods**:
- `setExpectedPassage(String passageText)` - Set the passage to analyze
- `startRecording(AnalysisCallback callback)` - Start capturing audio
- `stopRecordingAndAnalyze()` - Stop recording and analyze all audio
- `release()` - Clean up resources

**Callback Interface**:
```java
public interface AnalysisCallback {
    void onWordAnalyzed(int wordIndex, boolean isCorrect, float confidence);
    void onComplete(List<Boolean> wordCorrectness, float overallScore, long durationMs);
    void onError(String error);
}
```

### 2. Usage Example

```java
// Initialize (requires Context)
DirectAudioPronunciationAnalyzer audioAnalyzer = new DirectAudioPronunciationAnalyzer(this);

// Check if ready
if (!audioAnalyzer.isReady()) {
    Toast.makeText(this, "Pronunciation model not ready", Toast.LENGTH_SHORT).show();
    return;
}

// Set expected passage
String passageText = "The quick brown fox jumps over the lazy dog";
audioAnalyzer.setExpectedPassage(passageText);

// Start recording with callback
audioAnalyzer.startRecording(new DirectAudioPronunciationAnalyzer.AnalysisCallback() {
    @Override
    public void onWordAnalyzed(int wordIndex, boolean isCorrect, float confidence) {
        // Real-time feedback: Update UI as each word is analyzed
        runOnUiThread(() -> {
            Log.d("Pronunciation", String.format("Word %d: %s (%.0f%% confidence)", 
                wordIndex, isCorrect ? "✅" : "❌", confidence * 100));
            
            // Update word highlighting
            highlightWord(wordIndex, isCorrect);
        });
    }
    
    @Override
    public void onComplete(List<Boolean> wordCorrectness, float overallScore, long durationMs) {
        // All words analyzed: Show final results
        runOnUiThread(() -> {
            Log.d("Pronunciation", String.format("Overall score: %.1f%%", overallScore * 100));
            
            // Show results modal
            showResultsModal(overallScore, wordCorrectness, durationMs);
        });
    }
    
    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(StudentDetail.this, "Error: " + error, Toast.LENGTH_SHORT).show();
        });
    }
});

// When user clicks "Stop" button
stopButton.setOnClickListener(v -> {
    audioAnalyzer.stopRecordingAndAnalyze();
});

// Clean up when activity is destroyed
@Override
protected void onDestroy() {
    super.onDestroy();
    if (audioAnalyzer != null) {
        audioAnalyzer.release();
    }
}
```

## How It Works

### Audio Capture
- Uses Android's `AudioRecord` API
- 16kHz sample rate (standard for speech)
- Mono channel (single microphone)
- Continuous recording into buffers

### Audio Segmentation
- Total audio duration = recording time
- Expected words = passage word count
- Time per word = total duration / word count
- Each word gets equal audio segment

Example:
```
Passage: "The quick brown fox" (4 words)
Recording: 8 seconds
Time per word: 2 seconds

Segments:
- Word 0 "The":   0.0s - 2.0s
- Word 1 "quick": 2.0s - 4.0s
- Word 2 "brown": 4.0s - 6.0s
- Word 3 "fox":   6.0s - 8.0s
```

### Pronunciation Analysis
For each audio segment:
1. Extract MFCC features (39 features: 13 mean + 13 std + 13 delta)
2. Run ONNX Random Forest model
3. Get prediction: correct/incorrect + confidence
4. Callback to UI for real-time update

### Real-time UI Updates
- `onWordAnalyzed()` called for each word
- Update highlighting immediately
- Show progress to user
- No waiting until end

## Comparison: Vosk vs Direct Audio

| Feature | Vosk (Current) | Direct Audio (New) |
|---------|----------------|-------------------|
| **Approach** | Speech-to-text → Text matching | Direct audio analysis |
| **Normalization** | ❌ Yes (loses pronunciation) | ✅ No (preserves pronunciation) |
| **Complexity** | High (multiple components) | Low (single component) |
| **Model Size** | ~50MB (Vosk model) | ~2MB (ONNX model only) |
| **Offline** | ✅ Yes | ✅ Yes |
| **Accuracy** | Limited (text-based) | Better (audio-based) |
| **Real-time** | Partial results | Per-word callbacks |
| **Dependencies** | Vosk library + model | ONNX runtime only |

## Migration Path

### Phase 1: Add Direct Audio (Current)
- ✅ Create `DirectAudioPronunciationAnalyzer.java`
- ✅ Keep Vosk for backward compatibility
- ✅ Add usage documentation

### Phase 2: Test & Validate
- Test direct audio approach
- Compare accuracy with Vosk
- Gather user feedback

### Phase 3: Switch Default (Future)
- Make direct audio the default
- Keep Vosk as fallback option
- Update UI to use new analyzer

### Phase 4: Optional Cleanup (Future)
- Remove Vosk dependency if not needed
- Delete Vosk model files
- Reduce app size by ~50MB

## Current Status

✅ **DirectAudioPronunciationAnalyzer** created
✅ **Documentation** complete
✅ **Usage example** in StudentDetail.java
⏳ **Integration** - Ready to implement
⏳ **Testing** - Needs validation
⏳ **UI updates** - Needs implementation

## Next Steps

1. **Test the analyzer**:
   - Create a simple test activity
   - Record sample audio
   - Verify analysis results

2. **Integrate into StudentDetail**:
   - Add analyzer initialization
   - Update button handlers
   - Implement callbacks

3. **Compare with Vosk**:
   - Run both analyzers on same audio
   - Compare accuracy
   - Measure performance

4. **Decide on default**:
   - If direct audio is better → Make it default
   - If Vosk is better → Keep as hybrid
   - If both useful → Offer user choice

## Technical Details

### Audio Format
- **Sample Rate**: 16000 Hz (16kHz)
- **Channels**: 1 (Mono)
- **Encoding**: PCM 16-bit
- **Buffer Size**: Minimum buffer size × 4

### MFCC Features
- **Coefficients**: 13 MFCC coefficients
- **Statistics**: Mean, Standard Deviation, Delta
- **Total Features**: 39 (13 × 3)

### ONNX Model
- **Input**: 39 MFCC features
- **Output**: Class label (0 = incorrect, 1 = correct)
- **Model**: Random Forest classifier
- **File**: `randomforest.onnx` (~2MB)

## Troubleshooting

### No Audio Captured
- Check microphone permissions
- Verify AudioRecord initialization
- Check buffer size

### Low Accuracy
- Ensure audio is denoised
- Check MFCC extraction
- Verify model is loaded correctly

### Slow Analysis
- Reduce audio quality if needed
- Optimize MFCC extraction
- Use background thread (already implemented)

## References

- **AudioRecord**: Android audio capture API
- **MFCC**: Mel-frequency cepstral coefficients
- **ONNX**: Open Neural Network Exchange format
- **Random Forest**: Machine learning classifier
