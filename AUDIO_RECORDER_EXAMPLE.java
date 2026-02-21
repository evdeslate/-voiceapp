// Example usage of AudioRecorder class
// This demonstrates how to use Android's audio recording similar to Python's sounddevice + soundfile

package com.example.speak;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;

public class AudioRecorderExample extends AppCompatActivity {
    private static final String TAG = "AudioRecorderExample";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private AudioRecorder audioRecorder;
    private Button recordButton;
    private Button saveButton;
    private TextView statusText;
    private TextView audioLevelText;
    
    private float[] recordedAudio;
    private boolean isRecording = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize UI
        recordButton = findViewById(R.id.recordButton);
        saveButton = findViewById(R.id.saveButton);
        statusText = findViewById(R.id.statusText);
        audioLevelText = findViewById(R.id.audioLevelText);
        
        // Initialize recorder
        audioRecorder = new AudioRecorder();
        
        // Setup buttons
        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        
        saveButton.setOnClickListener(v -> saveToWav());
        saveButton.setEnabled(false);
        
        // Check permissions
        checkAudioPermission();
    }
    
    /**
     * Start recording audio
     * Similar to: audio_data = sd.rec(duration * sample_rate, samplerate=sample_rate)
     */
    private void startRecording() {
        if (!checkAudioPermission()) {
            return;
        }
        
        boolean started = audioRecorder.startRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onAudioData(short[] audioData, int length) {
                // Real-time audio processing
                // This is called continuously while recording
                Log.d(TAG, "Received " + length + " audio samples");
                
                // Example: Convert to float for processing
                float[] floatData = new float[length];
                for (int i = 0; i < length; i++) {
                    floatData[i] = audioData[i] / 32768.0f;
                }
                
                // Process audio (e.g., MFCC extraction, pronunciation scoring)
                // processAudioChunk(floatData);
            }
            
            @Override
            public void onAudioLevel(float rms, float db) {
                // Update UI with audio level
                runOnUiThread(() -> {
                    audioLevelText.setText(String.format("Audio Level: %.1f dB", db));
                });
            }
            
            @Override
            public void onRecordingComplete(float[] audioArray, int sampleRate) {
                // Recording complete - audio available as float array (like numpy)
                recordedAudio = audioArray;
                
                runOnUiThread(() -> {
                    statusText.setText(String.format("Recording complete: %d samples at %d Hz", 
                                                    audioArray.length, sampleRate));
                    saveButton.setEnabled(true);
                    Toast.makeText(AudioRecorderExample.this, 
                                 "Recording complete!", 
                                 Toast.LENGTH_SHORT).show();
                });
                
                Log.d(TAG, "Recording complete: " + audioArray.length + " samples");
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    statusText.setText("Error: " + error);
                    Toast.makeText(AudioRecorderExample.this, 
                                 "Recording error: " + error, 
                                 Toast.LENGTH_LONG).show();
                });
                
                Log.e(TAG, "Recording error: " + error);
            }
        });
        
        if (started) {
            isRecording = true;
            recordButton.setText("Stop Recording");
            statusText.setText("Recording...");
            saveButton.setEnabled(false);
        }
    }
    
    /**
     * Stop recording
     * Similar to: sd.wait()
     */
    private void stopRecording() {
        recordedAudio = audioRecorder.stopRecording();
        isRecording = false;
        recordButton.setText("Start Recording");
    }
    
    /**
     * Save recording to WAV file
     * Similar to: sf.write('output.wav', audio_data, sample_rate)
     */
    private void saveToWav() {
        if (recordedAudio == null || recordedAudio.length == 0) {
            Toast.makeText(this, "No recording to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create output file
        File outputDir = getExternalFilesDir(null);
        File outputFile = new File(outputDir, "recording_" + System.currentTimeMillis() + ".wav");
        
        // Save to WAV
        boolean success = AudioRecorder.saveToWav(outputFile, recordedAudio, audioRecorder.getSampleRate());
        
        if (success) {
            Toast.makeText(this, "Saved: " + outputFile.getName(), Toast.LENGTH_LONG).show();
            statusText.setText("Saved to: " + outputFile.getAbsolutePath());
            Log.d(TAG, "WAV file saved: " + outputFile.getAbsolutePath());
        } else {
            Toast.makeText(this, "Failed to save WAV file", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to save WAV file");
        }
    }
    
    /**
     * Check and request audio permission
     */
    private boolean checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Audio permission required", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up
        if (audioRecorder != null) {
            audioRecorder.release();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop recording when activity is paused
        if (isRecording) {
            stopRecording();
        }
    }
}

// ============================================================================
// PYTHON EQUIVALENT CODE
// ============================================================================
/*

import sounddevice as sd
import soundfile as sf
import numpy as np

# Configuration
sample_rate = 16000
duration = 5  # seconds

# Start recording (similar to startRecording())
print("Recording...")
audio_data = sd.rec(int(duration * sample_rate), 
                    samplerate=sample_rate, 
                    channels=1, 
                    dtype='float32')

# Wait for recording to complete (similar to stopRecording())
sd.wait()
print(f"Recording complete: {len(audio_data)} samples")

# Save to WAV file (similar to saveToWav())
output_file = f'recording_{int(time.time())}.wav'
sf.write(output_file, audio_data, sample_rate)
print(f"Saved to: {output_file}")

# Audio data is already a numpy array
print(f"Audio shape: {audio_data.shape}")
print(f"Audio dtype: {audio_data.dtype}")
print(f"Audio range: [{audio_data.min():.3f}, {audio_data.max():.3f}]")

*/
