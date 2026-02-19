package com.example.speak;

import java.util.HashMap;
import java.util.Map;

/**
 * MispronunciationOverride - Lightweight Filipino L1 Interference Detector
 * 
 * Catches common mispronunciations that Vosk normalizes incorrectly.
 * Optimized for speed - uses simple HashMap lookup (O(1) performance).
 * 
 * Common Filipino pronunciation patterns:
 * - /f/ → /p/ (no /f/ in Filipino: "father" → "pader")
 * - /v/ → /b/ (no /v/ in Filipino: "have" → "hab")
 * - /th/ → /d/ or /t/ (no /th/ in Filipino: "the" → "de", "with" → "wit")
 */
public class MispronunciationOverride {
    private static final String TAG = "MispronunciationOverride";
    
    /**
     * Map of mispronounced → correct word
     * Key: what child actually said (mispronounced)
     * Value: what the passage word should be (correct)
     */
    private static final Map<String, String> OVERRIDES = new HashMap<>();
    
    static {
        // ═══════════════════════════════════════════════════════════════
        // CRITICAL: /f/ → /p/ (No /f/ phoneme in Filipino)
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("pader", "father");      // Most common
        OVERRIDES.put("pather", "father");
        OVERRIDES.put("fader", "father");
        OVERRIDES.put("pater", "father");
        
        OVERRIDES.put("parm", "farm");
        OVERRIDES.put("pharm", "farm");
        
        OVERRIDES.put("apter", "after");
        OVERRIDES.put("apther", "after");
        
        // ═══════════════════════════════════════════════════════════════
        // CRITICAL: /v/ → /b/ (No /v/ phoneme in Filipino)
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("hab", "have");
        OVERRIDES.put("habe", "have");
        
        OVERRIDES.put("moob", "move");
        OVERRIDES.put("mob", "move");
        OVERRIDES.put("mobe", "move");
        
        OVERRIDES.put("heaby", "heavy");
        OVERRIDES.put("heby", "heavy");
        OVERRIDES.put("hebby", "heavy");
        
        // ═══════════════════════════════════════════════════════════════
        // CRITICAL: /th/ → /d/ or /t/ (No /th/ sounds in Filipino)
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("de", "the");            // Very common
        OVERRIDES.put("da", "the");
        
        OVERRIDES.put("dey", "they");
        OVERRIDES.put("tey", "they");
        
        OVERRIDES.put("wit", "with");
        OVERRIDES.put("wid", "with");
        
        OVERRIDES.put("anoder", "another");
        OVERRIDES.put("anuder", "another");
        OVERRIDES.put("anoter", "another");
        
        // ═══════════════════════════════════════════════════════════════
        // COMMON: Diphthong simplification
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("snel", "snail");
        OVERRIDES.put("snal", "snail");
        
        OVERRIDES.put("wont", "want");         // /a/ → /o/
        
        OVERRIDES.put("sayd", "said");         // Spelling pronunciation
        OVERRIDES.put("sayed", "said");
        
        // ═══════════════════════════════════════════════════════════════
        // COMMON: Final consonant dropping
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("tol", "told");
        OVERRIDES.put("wan", "want");
        OVERRIDES.put("lef", "left");
        
        // ═══════════════════════════════════════════════════════════════
        // COMMON: Past tense confusion
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("eat", "ate");           // Tense confusion
        OVERRIDES.put("eated", "eaten");       // False regular past
        OVERRIDES.put("tryed", "tried");
        
        // ═══════════════════════════════════════════════════════════════
        // COMMON: Vowel shifts
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("litle", "little");
        OVERRIDES.put("litol", "little");
        OVERRIDES.put("liddle", "little");
        
        OVERRIDES.put("enormus", "enormous");
        OVERRIDES.put("enourmous", "enormous");
        
        // ═══════════════════════════════════════════════════════════════
        // COMMON: Consonant cluster simplification
        // ═══════════════════════════════════════════════════════════════
        OVERRIDES.put("gras", "grass");
        OVERRIDES.put("gress", "grass");
    }
    
    /**
     * Check if Vosk's decision should be overridden.
     * 
     * FAST: O(1) HashMap lookup - no expensive calculations!
     * 
     * @param spokenWord   What Vosk heard
     * @param expectedWord The passage word
     * @param voskDecision Vosk's original decision (true = correct)
     * @return Final decision (true = correct, false = incorrect)
     */
    public static boolean evaluate(String spokenWord, String expectedWord, boolean voskDecision) {
        if (spokenWord == null || expectedWord == null) {
            return voskDecision;
        }
        
        // Normalize to lowercase, remove punctuation
        String spoken = spokenWord.toLowerCase().replaceAll("[^a-z]", "");
        String expected = expectedWord.toLowerCase().replaceAll("[^a-z]", "");
        
        // Check if this mispronunciation is in our override map
        String correctWord = OVERRIDES.get(spoken);
        
        if (correctWord != null && correctWord.equals(expected)) {
            // This is a known mispronunciation - force INCORRECT
            android.util.Log.d(TAG, String.format(
                "🚫 OVERRIDE: '%s' → '%s' forced INCORRECT (Vosk said: %b)",
                spoken, expected, voskDecision));
            return false;
        }
        
        // No override - trust Vosk's decision
        return voskDecision;
    }
    
    /**
     * Add a runtime override for passage-specific words.
     * Useful for dynamically loaded passages.
     */
    public static void addOverride(String spokenForm, String expectedWord) {
        String spoken = spokenForm.toLowerCase().replaceAll("[^a-z]", "");
        String expected = expectedWord.toLowerCase().replaceAll("[^a-z]", "");
        OVERRIDES.put(spoken, expected);
        android.util.Log.d(TAG, String.format(
            "➕ Runtime override added: '%s' → '%s'", spoken, expected));
    }
}
