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

/**
 * Test activity for audio recording
 * Tests: AudioRecord -> PCM -> WAV file (16kHz, 16-bit, mono)
 */
public class AudioRecordingTestActivity extends AppCompatActivity {
    private static final String TAG = "AudioRecordTest";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private AudioRecorder audioRecorder;
    private Button recordButton;
    private TextView statusText;
    private TextView audioLevelText;
    
    private boolean isRecording = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Use existing layout for now
        
        // Initialize
        audioRecorder = new AudioRecorder();
        
        // Setup UI (you can create a proper layout later)
        setupUI();
        
        // Check permissions
        checkAudioPermission();
    }
    
    private void setupUI() {
        // For now, just log - you can add proper UI later
        Log.d(TAG, "AudioRecordingTestActivity initialized");
    }
    
    public void testRecording() {
        if (!checkAudioPermission()) {
            return;
        }
        
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private void startRecording() {
        Log.d(TAG, "=== STARTING AUDIO RECORDING TEST ===");
        
        boolean started = audioRecorder.startRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onAudioData(short[] audioData, int length) {
                // Log first chunk only
                if (length > 0) {
                    Log.d(TAG, "Received audio chunk: " + length + " samples");
                }
            }
            
            @Override
            public void onAudioLevel(float rms, float db) {
                // Log audio level
                Log.d(TAG, String.format("Audio level: RMS=%.2f, dB=%.2f", rms, db));
            }
            
            @Override
            public void onRecordingComplete(float[] audioArray, int sampleRate) {
                Log.d(TAG, "=== RECORDING COMPLETE ===");
                Log.d(TAG, "Total samples: " + audioArray.length);
                Log.d(TAG, "Sample rate: " + sampleRate + " Hz");
                Log.d(TAG, "Duration: " + (audioArray.length / (float) sampleRate) + " seconds");
                
                // Test MFCC extraction with TarsosDSP
                Log.d(TAG, "");
                Log.d(TAG, "=== TESTING MFCC EXTRACTION ===");
                TarsosMFCCTest.testWithRecording(audioArray);
                
                // Save to WAV file
                File outputDir = getExternalFilesDir(null);
                File outputFile = new File(outputDir, "test_recording_" + System.currentTimeMillis() + ".wav");
                
                boolean saved = AudioRecorder.saveToWav(outputFile, audioArray, sampleRate);
                
                if (saved) {
                    Log.d(TAG, "✅ WAV file saved: " + outputFile.getAbsolutePath());
                    Log.d(TAG, "File size: " + outputFile.length() + " bytes");
                    
                    // Verify WAV header
                    verifyWavFile(outputFile);
                    
                    runOnUiThread(() -> {
                        Toast.makeText(AudioRecordingTestActivity.this, 
                                     "Recording saved: " + outputFile.getName(), 
                                     Toast.LENGTH_LONG).show();
                    });
                } else {
                    Log.e(TAG, "❌ Failed to save WAV file");
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Recording error: " + error);
            }
        });
        
        if (started) {
            isRecording = true;
            Log.d(TAG, "✅ Recording started");
            Toast.makeText(this, "Recording... (will stop in 5 seconds)", Toast.LENGTH_SHORT).show();
            
            // Auto-stop after 5 seconds for testing
            new android.os.Handler().postDelayed(() -> {
                if (isRecording) {
                    stopRecording();
                }
            }, 5000);
        } else {
            Log.e(TAG, "❌ Failed to start recording");
        }
    }
    
    private void stopRecording() {
        Log.d(TAG, "Stopping recording...");
        audioRecorder.stopRecording();
        isRecording = false;
    }
    
    private void verifyWavFile(File wavFile) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(wavFile);
            byte[] header = new byte[44];
            int read = fis.read(header);
            fis.close();
            
            if (read == 44) {
                Log.d(TAG, "=== WAV FILE VERIFICATION ===");
                
                // Check RIFF header
                String riff = new String(new byte[]{header[0], header[1], header[2], header[3]});
                Log.d(TAG, "RIFF header: " + riff + (riff.equals("RIFF") ? " ✅" : " ❌"));
                
                // Check WAVE format
                String wave = new String(new byte[]{header[8], header[9], header[10], header[11]});
                Log.d(TAG, "WAVE format: " + wave + (wave.equals("WAVE") ? " ✅" : " ❌"));
                
                // Check fmt chunk
                String fmt = new String(new byte[]{header[12], header[13], header[14], header[15]});
                Log.d(TAG, "fmt chunk: " + fmt + (fmt.equals("fmt ") ? " ✅" : " ❌"));
                
                // Check audio format (PCM = 1)
                int audioFormat = (header[21] << 8) | (header[20] & 0xFF);
                Log.d(TAG, "Audio format: " + audioFormat + (audioFormat == 1 ? " (PCM) ✅" : " ❌"));
                
                // Check channels (Mono = 1)
                int channels = (header[23] << 8) | (header[22] & 0xFF);
                Log.d(TAG, "Channels: " + channels + (channels == 1 ? " (Mono) ✅" : " ❌"));
                
                // Check sample rate
                int sampleRate = (header[27] << 24) | ((header[26] & 0xFF) << 16) | 
                                ((header[25] & 0xFF) << 8) | (header[24] & 0xFF);
                Log.d(TAG, "Sample rate: " + sampleRate + " Hz" + (sampleRate == 16000 ? " ✅" : " ❌"));
                
                // Check bits per sample
                int bitsPerSample = (header[35] << 8) | (header[34] & 0xFF);
                Log.d(TAG, "Bits per sample: " + bitsPerSample + (bitsPerSample == 16 ? " ✅" : " ❌"));
                
                // Check data chunk
                String data = new String(new byte[]{header[36], header[37], header[38], header[39]});
                Log.d(TAG, "data chunk: " + data + (data.equals("data") ? " ✅" : " ❌"));
                
                Log.d(TAG, "=== WAV FILE VERIFICATION COMPLETE ===");
            } else {
                Log.e(TAG, "Failed to read WAV header");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying WAV file", e);
        }
    }
    
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
                Log.d(TAG, "Audio permission granted");
                Toast.makeText(this, "Audio permission granted - ready to test", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Audio permission denied");
                Toast.makeText(this, "Audio permission required for testing", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null) {
            audioRecorder.release();
        }
    }
}
