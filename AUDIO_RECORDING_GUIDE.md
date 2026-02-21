# Audio Recording Guide for Android

## Overview

This guide shows how to implement audio input and recording in Android, equivalent to Python's `sounddevice` and `soundfile` libraries.

## Python vs Android Comparison

| Python | Android | Purpose |
|--------|---------|---------|
| `sounddevice.InputStream()` | `AudioRecord` | Record live microphone input |
| `soundfile.write()` | `AudioRecorder.saveToWav()` | Save audio to WAV file |
| `numpy.array` | `float[]` | Audio data as array |
| `soundfile.read()` | `AudioRecorder.readWavFile()` | Read WAV file |

## AudioRecorder Class

The `AudioRecorder` class provides Python-like functionality for Android:

### Features

1. **Live microphone recording** (like `sounddevice`)
2. **WAV file saving** (like `soundfile.write`)
3. **Float array conversion** (like `numpy`)
4. **Real-time audio level monitoring**
5. **Automatic buffer management**

### Basic Usage

```java
// Create recorder
AudioRecorder recorder = new AudioRecorder();

// Start recording
recorder.startRecording(new AudioRecorder.RecordingCallback() {
    @Override
    public void onAudioData(short[] audioData, int length) {
        // Process audio chunks in real-time
        Log.d(TAG, "Received " + length + " samples");
    }
    
    @Override
    public void onAudioLevel(float rms, float db) {
        // Monitor audio level
        Log.d(TAG, "Audio level: " + db + " dB");
    }
    
    @Override
    public void onRecordingComplete(float[] audioArray, int sampleRate) {
        // Get complete recording as float array (like numpy)
        Log.d(TAG, "Recording complete: " + audioArray.length + " samples at " + sampleRate + "Hz");
    }
    
    @Override
    public void onError(String error) {
        Log.e(TAG, "Recording error: " + error);
    }
});

// Record for some time...
Thread.sleep(5000);

// Stop and get audio as float array
float[] audioData = recorder.stopRecording();

// Save to WAV file
File outputFile = new File(context.getExternalFilesDir(null), "recording.wav");
recorder.saveToWav(outputFile);

// Clean up
recorder.release();
```

### Python Equivalent

```python
import sounddevice as sd
import soundfile as sf
import numpy as np

# Record audio
duration = 5  # seconds
sample_rate = 16000
audio_data = sd.rec(int(duration * sample_rate), 
                    samplerate=sample_rate, 
                    channels=1, 
                    dtype='float32')
sd.wait()

# Save to WAV file
sf.write('recording.wav', audio_data, sample_rate)

# Audio data is already a numpy array
print(f"Recorded {len(audio_data)} samples")
```

## Advanced Usage

### 1. Record with Real-time Processing

```java
AudioRecorder recorder = new AudioRecorder();

recorder.startRecording(new AudioRecorder.RecordingCallback() {
    @Override
    public void onAudioData(short[] audioData, int length) {
        // Convert to float array for processing
        float[] floatData = new float[length];
        for (int i = 0; i < length; i++) {
            floatData[i] = audioData[i] / 32768.0f;
        }
        
        // Process audio (e.g., extract MFCC features)
        float[][] mfccFeatures = mfccExtractor.extractFeatures(floatData);
        
        // Score pronunciation
        float score = scorer.score(mfccFeatures);
    }
    
    @Override
    public void onAudioLevel(float rms, float db) {
        // Update UI with audio level
        runOnUiThread(() -> {
            audioLevelBar.setProgress((int) (db + 60)); // -60dB to 0dB range
        });
    }
    
    @Override
    public void onRecordingComplete(float[] audioArray, int sampleRate) {
        // Final processing
        processCompleteRecording(audioArray, sampleRate);
    }
    
    @Override
    public void onError(String error) {
        showError(error);
    }
});
```

### 2. Save Multiple Recordings

```java
AudioRecorder recorder = new AudioRecorder();

// Record word 1
recorder.startRecording(callback);
Thread.sleep(2000);
float[] word1Audio = recorder.stopRecording();
AudioRecorder.saveToWav(new File("word1.wav"), word1Audio, 16000);

// Record word 2
recorder.startRecording(callback);
Thread.sleep(2000);
float[] word2Audio = recorder.stopRecording();
AudioRecorder.saveToWav(new File("word2.wav"), word2Audio, 16000);

recorder.release();
```

### 3. Integration with MFCC Pipeline

```java
public class EnhancedMFCCRecognizer {
    private AudioRecorder audioRecorder;
    private MFCCExtractor mfccExtractor;
    private ONNXRandomForestScorer scorer;
    
    public void startRecognition(String[] expectedWords) {
        audioRecorder = new AudioRecorder();
        
        audioRecorder.startRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onAudioData(short[] audioData, int length) {
                // Convert to float
                float[] floatData = new float[length];
                for (int i = 0; i < length; i++) {
                    floatData[i] = audioData[i] / 32768.0f;
                }
                
                // Extract MFCC features
                float[][] mfcc = mfccExtractor.extractFeatures(floatData);
                
                // Score with Random Forest
                float score = scorer.score(mfcc);
                boolean isCorrect = score > 0.5f;
                
                // Callback with result
                callback.onWordScored(currentWordIndex, expectedWords[currentWordIndex], 
                                     score, isCorrect);
            }
            
            @Override
            public void onAudioLevel(float rms, float db) {
                // Detect silence for word boundaries
                if (db < -40) {
                    // Silence detected - end of word
                    currentWordIndex++;
                }
            }
            
            @Override
            public void onRecordingComplete(float[] audioArray, int sampleRate) {
                // Save complete session audio
                File sessionFile = new File(getExternalFilesDir(null), 
                                           "session_" + System.currentTimeMillis() + ".wav");
                AudioRecorder.saveToWav(sessionFile, audioArray, sampleRate);
            }
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
    
    public void stopRecognition() {
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
            audioRecorder.release();
        }
    }
}
```

## Audio Format Details

### Sample Rate
- **16000 Hz** - Standard for speech recognition (matches training data)
- Lower rates (8000 Hz) - Phone quality
- Higher rates (44100 Hz) - Music quality (unnecessary for speech)

### Audio Format
- **PCM 16-bit** - Standard uncompressed audio
- **Mono** - Single channel (sufficient for speech)
- **Float normalization** - Values from -1.0 to 1.0 (like numpy)

### Buffer Size
- Automatically calculated using `AudioRecord.getMinBufferSize()`
- Minimum 4096 samples for smooth recording
- Larger buffers = more latency but more stable

## WAV File Format

The `AudioRecorder` class saves files in standard WAV format:

```
WAV File Structure:
├── RIFF Header (12 bytes)
├── fmt Chunk (24 bytes)
│   ├── Audio format: PCM (1)
│   ├── Channels: Mono (1)
│   ├── Sample rate: 16000 Hz
│   ├── Bits per sample: 16
└── data Chunk (variable size)
    └── Audio samples (16-bit signed integers)
```

## Permissions

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

Request at runtime:

```java
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
        new String[]{Manifest.permission.RECORD_AUDIO},
        PERMISSION_REQUEST_CODE);
}
```

## Comparison with Current Implementation

### Current MFCCPronunciationRecognizer
- Uses `AudioRecord` directly
- Processes audio in real-time
- No WAV file saving
- No float array conversion

### Enhanced AudioRecorder
- Wraps `AudioRecord` with Python-like API
- Stores complete recording
- Saves to WAV files
- Converts to float arrays (numpy-like)
- Real-time audio level monitoring

## Migration Path

You can gradually migrate to `AudioRecorder`:

1. **Keep current implementation** - It works fine for real-time processing
2. **Add AudioRecorder for debugging** - Save recordings to WAV files for analysis
3. **Use AudioRecorder for training** - Collect audio samples for model improvement
4. **Optional: Replace MFCCPronunciationRecognizer** - Use AudioRecorder as base class

## Example: Debug Recording

```java
// In StudentDetail.java
private AudioRecorder debugRecorder;

private void startDebugRecording() {
    debugRecorder = new AudioRecorder();
    
    debugRecorder.startRecording(new AudioRecorder.RecordingCallback() {
        @Override
        public void onAudioData(short[] audioData, int length) {
            // Still use existing MFCC pipeline
            pronunciationRecognizer.processAudio(audioData, length);
        }
        
        @Override
        public void onAudioLevel(float rms, float db) {
            Log.d(TAG, "Audio level: " + db + " dB");
        }
        
        @Override
        public void onRecordingComplete(float[] audioArray, int sampleRate) {
            // Save for debugging
            File debugFile = new File(getExternalFilesDir(null), 
                                     "debug_" + System.currentTimeMillis() + ".wav");
            AudioRecorder.saveToWav(debugFile, audioArray, sampleRate);
            Log.d(TAG, "Debug recording saved: " + debugFile.getAbsolutePath());
        }
        
        @Override
        public void onError(String error) {
            Log.e(TAG, "Debug recording error: " + error);
        }
    });
}
```

## Next Steps

1. Test `AudioRecorder` class
2. Verify WAV file saving works
3. Compare audio quality with Python recordings
4. Optionally integrate with `MFCCPronunciationRecognizer`
5. Use for collecting training data
