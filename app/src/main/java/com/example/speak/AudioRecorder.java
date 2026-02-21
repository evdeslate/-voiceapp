package com.example.speak;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced audio recorder for Android
 * Provides functionality similar to Python's sounddevice + soundfile
 * 
 * Features:
 * - Live microphone input recording
 * - Save to WAV files
 * - Convert to float arrays (like numpy)
 * - Real-time audio level monitoring
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    
    // Audio configuration
    private static final int SAMPLE_RATE = 16000; // 16kHz for speech
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit = 2 bytes
    
    // Recording state
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;
    private int bufferSize;
    
    // Audio data storage
    private List<short[]> audioChunks = new ArrayList<>();
    private RecordingCallback callback;
    
    /**
     * Callback interface for recording events
     */
    public interface RecordingCallback {
        void onAudioData(short[] audioData, int length);
        void onAudioLevel(float rms, float db);
        void onRecordingComplete(float[] audioArray, int sampleRate);
        void onError(String error);
    }
    
    public AudioRecorder() {
        bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        );
        
        // Ensure buffer is large enough
        if (bufferSize < 4096) {
            bufferSize = 4096;
        }
    }
    
    /**
     * Start recording audio from microphone
     * Similar to sounddevice.InputStream()
     */
    public boolean startRecording(RecordingCallback callback) {
        this.callback = callback;
        
        try {
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 4
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                if (callback != null) {
                    callback.onError("Failed to initialize audio recording");
                }
                return false;
            }
            
            audioChunks.clear();
            audioRecord.startRecording();
            isRecording = true;
            
            recordingThread = new Thread(this::recordAudioStream);
            recordingThread.start();
            
            Log.d(TAG, "Audio recording started at " + SAMPLE_RATE + "Hz");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            if (callback != null) {
                callback.onError("Failed to start recording: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Record audio stream (runs in background thread)
     */
    private void recordAudioStream() {
        short[] buffer = new short[bufferSize];
        
        while (isRecording) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            
            if (read > 0) {
                // Store audio chunk
                short[] chunk = new short[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                audioChunks.add(chunk);
                
                // Calculate audio level (RMS and dB)
                float rms = calculateRMS(buffer, read);
                float db = 20 * (float) Math.log10(rms / 32768.0f); // Convert to dB
                
                // Notify callback
                if (callback != null) {
                    callback.onAudioData(chunk, read);
                    callback.onAudioLevel(rms, db);
                }
            }
        }
    }
    
    /**
     * Stop recording and return audio as float array
     * Similar to sounddevice.stop() + numpy array conversion
     */
    public float[] stopRecording() {
        isRecording = false;
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting for recording thread", e);
            }
        }
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }
        
        // Convert to float array (like numpy)
        float[] audioArray = convertToFloatArray();
        
        if (callback != null) {
            callback.onRecordingComplete(audioArray, SAMPLE_RATE);
        }
        
        Log.d(TAG, "Recording stopped. Total samples: " + audioArray.length);
        return audioArray;
    }
    
    /**
     * Convert recorded audio to float array (normalized -1.0 to 1.0)
     * Similar to numpy array conversion in Python
     */
    private float[] convertToFloatArray() {
        // Calculate total samples
        int totalSamples = 0;
        for (short[] chunk : audioChunks) {
            totalSamples += chunk.length;
        }
        
        // Create float array
        float[] audioArray = new float[totalSamples];
        int offset = 0;
        
        // Convert short to float (normalize to -1.0 to 1.0)
        for (short[] chunk : audioChunks) {
            for (short sample : chunk) {
                audioArray[offset++] = sample / 32768.0f;
            }
        }
        
        return audioArray;
    }
    
    /**
     * Save recorded audio to WAV file
     * Similar to soundfile.write() in Python
     */
    public boolean saveToWav(File outputFile) {
        try {
            float[] audioData = convertToFloatArray();
            return saveToWav(outputFile, audioData, SAMPLE_RATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save WAV file", e);
            return false;
        }
    }
    
    /**
     * Save float array to WAV file
     * Similar to soundfile.write(file, data, samplerate)
     */
    public static boolean saveToWav(File outputFile, float[] audioData, int sampleRate) {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            // Convert float to short
            short[] shortData = new short[audioData.length];
            for (int i = 0; i < audioData.length; i++) {
                shortData[i] = (short) (audioData[i] * 32767.0f);
            }
            
            // Write WAV header
            writeWavHeader(fos, shortData.length, sampleRate);
            
            // Write audio data
            ByteBuffer buffer = ByteBuffer.allocate(shortData.length * 2);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (short sample : shortData) {
                buffer.putShort(sample);
            }
            fos.write(buffer.array());
            
            Log.d(TAG, "Saved WAV file: " + outputFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to write WAV file", e);
            return false;
        }
    }
    
    /**
     * Read WAV file to float array
     * Similar to soundfile.read() in Python
     */
    public static float[] readWavFile(File inputFile) throws IOException {
        // TODO: Implement WAV file reading
        // For now, return empty array
        Log.w(TAG, "WAV file reading not yet implemented");
        return new float[0];
    }
    
    /**
     * Write WAV file header
     */
    private static void writeWavHeader(FileOutputStream fos, int dataSize, int sampleRate) throws IOException {
        int byteRate = sampleRate * BYTES_PER_SAMPLE;
        int totalDataLen = dataSize * BYTES_PER_SAMPLE;
        int totalSize = totalDataLen + 36;
        
        byte[] header = new byte[44];
        
        // RIFF chunk
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalSize & 0xff);
        header[5] = (byte) ((totalSize >> 8) & 0xff);
        header[6] = (byte) ((totalSize >> 16) & 0xff);
        header[7] = (byte) ((totalSize >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        
        // fmt chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // fmt chunk size
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // PCM format
        header[21] = 0;
        header[22] = 1; // Mono
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = BYTES_PER_SAMPLE; // Block align
        header[33] = 0;
        header[34] = 16; // Bits per sample
        header[35] = 0;
        
        // data chunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalDataLen & 0xff);
        header[41] = (byte) ((totalDataLen >> 8) & 0xff);
        header[42] = (byte) ((totalDataLen >> 16) & 0xff);
        header[43] = (byte) ((totalDataLen >> 24) & 0xff);
        
        fos.write(header);
    }
    
    /**
     * Calculate RMS (Root Mean Square) audio level
     */
    private float calculateRMS(short[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        return (float) Math.sqrt(sum / (double) length);
    }
    
    /**
     * Get current recording state
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Get sample rate
     */
    public int getSampleRate() {
        return SAMPLE_RATE;
    }
    
    /**
     * Release resources
     */
    public void release() {
        stopRecording();
        audioChunks.clear();
    }
}
