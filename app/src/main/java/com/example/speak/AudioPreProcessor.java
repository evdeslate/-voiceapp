package com.example.speak;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * AudioPreProcessor
 *
 * Applies two real-time filters to raw PCM audio frames before they
 * reach Vosk or MFCC extraction:
 *
 *   1. Noise Gate   — silences frames below an energy threshold.
 *                     Eliminates classroom hum, fan noise, brief crosstalk.
 *
 *   2. Bandpass     — attenuates frequencies outside 80–3400 Hz.
 *                     Child speech energy sits in 100–3000 Hz.
 *                     This removes low-frequency rumble and high-frequency hiss.
 *
 * Usage:
 *   AudioPreProcessor pre = new AudioPreProcessor(sampleRate);
 *   byte[] cleanFrame = pre.process(rawPcmFrame);
 *   // feed cleanFrame to Vosk / MFCC instead of rawPcmFrame
 */
public class AudioPreProcessor {

    private static final String TAG = "AudioPreProcessor";

    // ── RMS Normalization ─────────────────────────────────────────────────────
    // Target RMS level for normalization (matches training data preprocessing)
    // This ensures consistent signal intensity across all recordings
    private static final float TARGET_RMS = 0.1f;
    
    // ── Noise gate ────────────────────────────────────────────────────────────
    // RMS energy below this threshold = silence (background noise)
    // Tune: 0.01f = sensitive (quiet room), 0.03f = aggressive (loud classroom)
    private static final float NOISE_GATE_THRESHOLD = 0.02f;

    // How many consecutive silent frames before gate closes
    private static final int GATE_HOLD_FRAMES = 3;
    private int silentFrameCount = 0;

    // ── Bandpass (simple IIR) ─────────────────────────────────────────────────
    // Two first-order IIR filters cascaded:
    //   High-pass at ~80 Hz  (removes rumble)
    //   Low-pass  at ~3400 Hz (removes hiss)
    private final float hpAlpha;   // high-pass coefficient
    private final float lpAlpha;   // low-pass coefficient
    private float hpPrev = 0f;     // high-pass state
    private float lpPrev = 0f;     // low-pass state

    private final int sampleRate;

    public AudioPreProcessor(int sampleRate) {
        this.sampleRate = sampleRate;

        // IIR alpha = RC / (RC + dt) where dt = 1/sampleRate
        // High-pass: cutoff = 80 Hz
        float rcHp = 1.0f / (2.0f * (float) Math.PI * 80f);
        float dt   = 1.0f / sampleRate;
        hpAlpha    = rcHp / (rcHp + dt);

        // Low-pass: cutoff = 3400 Hz
        float rcLp = 1.0f / (2.0f * (float) Math.PI * 3400f);
        lpAlpha    = dt   / (rcLp + dt);

        android.util.Log.d(TAG, String.format(
            "AudioPreProcessor init: sr=%d, hpAlpha=%.4f, lpAlpha=%.4f",
            sampleRate, hpAlpha, lpAlpha));
    }

    /**
     * Process one audio frame.
     *
     * @param rawPcm  16-bit PCM bytes (little-endian), any length
     * @return        filtered PCM bytes, same length.
     *                If gate is closed, returns a zeroed buffer (silence).
     */
    public byte[] process(byte[] rawPcm) {
        if (rawPcm == null || rawPcm.length < 2) return rawPcm;

        int numSamples = rawPcm.length / 2;
        float[] samples = new float[numSamples];

        // ── Decode bytes → float [-1, 1] ──────────────────────────────────────
        ByteBuffer buf = ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples; i++) {
            samples[i] = buf.getShort() / 32768f;
        }

        // ── Compute RMS energy ────────────────────────────────────────────────
        float sumSq = 0f;
        for (float s : samples) sumSq += s * s;
        float rms = (float) Math.sqrt(sumSq / numSamples);

        // ── Noise gate ────────────────────────────────────────────────────────
        if (rms < NOISE_GATE_THRESHOLD) {
            silentFrameCount++;
            if (silentFrameCount >= GATE_HOLD_FRAMES) {
                // Frame is background noise — return silence
                return new byte[rawPcm.length]; // zeroed array
            }
        } else {
            silentFrameCount = 0;
        }

        // ── Bandpass filter ───────────────────────────────────────────────────
        for (int i = 0; i < numSamples; i++) {
            // High-pass: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
            float hp = hpAlpha * (hpPrev + samples[i] - (i > 0 ? samples[i-1] : 0f));
            hpPrev = hp;

            // Low-pass: y[n] = y[n-1] + alpha * (x[n] - y[n-1])
            lpPrev = lpPrev + lpAlpha * (hp - lpPrev);
            samples[i] = lpPrev;
        }

        // ── Re-encode float → bytes ───────────────────────────────────────────
        ByteBuffer out = ByteBuffer.allocate(rawPcm.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float s : samples) {
            short val = (short) Math.max(-32768, Math.min(32767, s * 32768f));
            out.putShort(val);
        }
        return out.array();
    }

    /**
     * Apply RMS normalization to audio samples
     * Normalizes the signal to a target RMS level for consistent intensity
     * 
     * This is CRITICAL for matching training data preprocessing:
     * - Training data was RMS-normalized to TARGET_RMS
     * - Production audio must use the same normalization
     * - Without this, MFCC features will have different scales
     * 
     * @param samples Audio samples (float array, -1.0 to 1.0)
     * @return RMS-normalized samples
     */
    public float[] rmsNormalize(float[] samples) {
        if (samples == null || samples.length == 0) {
            return samples;
        }
        
        // Calculate current RMS
        float sumSq = 0f;
        for (float s : samples) {
            sumSq += s * s;
        }
        float currentRms = (float) Math.sqrt(sumSq / samples.length);
        
        // If signal is silent, don't normalize (avoid division by zero)
        if (currentRms < 1e-6f) {
            android.util.Log.d(TAG, "RMS normalization skipped: signal is silent");
            return samples;
        }
        
        // Calculate scaling factor to reach target RMS
        float scale = TARGET_RMS / currentRms;
        
        // Apply scaling
        float[] normalized = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            normalized[i] = samples[i] * scale;
            
            // Clamp to [-1, 1] to prevent clipping
            normalized[i] = Math.max(-1.0f, Math.min(1.0f, normalized[i]));
        }
        
        android.util.Log.d(TAG, String.format(
            "RMS normalization: %.6f → %.6f (scale: %.3f)", 
            currentRms, TARGET_RMS, scale));
        
        return normalized;
    }
    
    /**
     * Apply RMS normalization to short audio samples
     * Converts short to float, normalizes, then converts back
     * 
     * @param samples Audio samples (short array, -32768 to 32767)
     * @return RMS-normalized samples
     */
    public short[] rmsNormalize(short[] samples) {
        if (samples == null || samples.length == 0) {
            return samples;
        }
        
        // Convert short to float
        float[] floatSamples = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            floatSamples[i] = samples[i] / 32768.0f;
        }
        
        // Apply RMS normalization
        floatSamples = rmsNormalize(floatSamples);
        
        // Convert back to short
        short[] normalized = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            normalized[i] = (short) Math.max(-32768, Math.min(32767, floatSamples[i] * 32768.0f));
        }
        
        return normalized;
    }

    /** Reset filter state (call when recording stops/starts) */
    public void reset() {
        hpPrev = 0f;
        lpPrev = 0f;
        silentFrameCount = 0;
    }
}
