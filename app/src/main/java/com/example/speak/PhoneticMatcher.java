package com.example.speak;

import java.util.HashMap;
import java.util.Map;

/**
 * PhoneticMatcher
 *
 * When Vosk returns nothing (mumble, stall), this class tries to find
 * which passage word the child was attempting, using two strategies:
 *
 *   1. Soundex  — phonetic encoding. "singin" and "singing" share the same
 *                 Soundex code S520. Good for vowel substitutions and
 *                 dropped syllables (Filipino, Spanish L1 errors).
 *
 *   2. Levenshtein edit distance — catches small spelling differences.
 *                 "walked" vs "walkt" = distance 1 = very close match.
 *
 * The matcher always returns a result, so the watchdog can always
 * decide whether the attempt was CLOSE (possibly correct) or FAR (wrong).
 *
 * Usage:
 *   PhoneticMatcher pm = new PhoneticMatcher();
 *   PhoneticMatcher.Result r = pm.match("singin", "singing");
 *   if (r.isCloseEnough) { // treat as mispronounced but attempted }
 *   else                  { // treat as completely wrong / skipped  }
 */
public class PhoneticMatcher {

    public static class Result {
        public final String  spokenWord;
        public final String  expectedWord;
        public final String  spokenSoundex;
        public final String  expectedSoundex;
        public final int     editDistance;
        public final boolean soundexMatch;
        public final boolean isCloseEnough;   // true = child attempted the word
        public final float   similarity;      // 0.0 to 1.0

        Result(String spoken, String expected, String sSoundex, String eSoundex,
               int editDist, boolean soundexMatch, float similarity) {
            this.spokenWord      = spoken;
            this.expectedWord    = expected;
            this.spokenSoundex   = sSoundex;
            this.expectedSoundex = eSoundex;
            this.editDistance    = editDist;
            this.soundexMatch    = soundexMatch;
            this.similarity      = similarity;
            // Close enough if soundex matches OR normalized edit distance < 0.4
            this.isCloseEnough   = soundexMatch || similarity >= 0.6f;
        }

        @Override
        public String toString() {
            return String.format(
                "PhoneticMatch('%s' vs '%s': soundex=%s/%s match=%b edit=%d sim=%.2f close=%b)",
                spokenWord, expectedWord, spokenSoundex, expectedSoundex,
                soundexMatch, editDistance, similarity, isCloseEnough);
        }
    }

    /**
     * Match a spoken word against an expected word.
     * Both inputs should be lowercase, stripped of punctuation.
     */
    public Result match(String spoken, String expected) {
        if (spoken  == null) spoken  = "";
        if (expected == null) expected = "";

        spoken   = spoken.toLowerCase().replaceAll("[^a-z]", "");
        expected = expected.toLowerCase().replaceAll("[^a-z]", "");

        String spokenSoundex   = soundex(spoken);
        String expectedSoundex = soundex(expected);
        boolean soundexMatch   = spokenSoundex.equals(expectedSoundex)
                                  && !spokenSoundex.equals("0000");

        int editDist = levenshtein(spoken, expected);

        // Normalized similarity: 1 - (editDist / maxLen)
        int maxLen = Math.max(Math.max(spoken.length(), expected.length()), 1);
        float similarity = 1.0f - ((float) editDist / maxLen);

        return new Result(spoken, expected, spokenSoundex, expectedSoundex,
                          editDist, soundexMatch, similarity);
    }

    // ── Soundex implementation ────────────────────────────────────────────────
    private static final Map<Character, Character> SOUNDEX_MAP = new HashMap<>();
    static {
        for (char c : "bfpv".toCharArray())    SOUNDEX_MAP.put(c, '1');
        for (char c : "cgjkqsxyz".toCharArray()) SOUNDEX_MAP.put(c, '2');
        for (char c : "dt".toCharArray())      SOUNDEX_MAP.put(c, '3');
        SOUNDEX_MAP.put('l', '4');
        for (char c : "mn".toCharArray())      SOUNDEX_MAP.put(c, '5');
        SOUNDEX_MAP.put('r', '6');
    }

    private String soundex(String word) {
        if (word == null || word.isEmpty()) return "0000";

        char first = Character.toUpperCase(word.charAt(0));
        StringBuilder code = new StringBuilder();
        code.append(first);

        char prevCode = SOUNDEX_MAP.getOrDefault(Character.toLowerCase(first), '0');

        for (int i = 1; i < word.length() && code.length() < 4; i++) {
            char c = word.charAt(i);
            Character mapped = SOUNDEX_MAP.get(c);
            if (mapped == null) {
                prevCode = '0'; // vowel separator
            } else if (!mapped.equals(prevCode)) {
                code.append(mapped);
                prevCode = mapped;
            }
        }

        while (code.length() < 4) code.append('0');
        return code.toString();
    }

    // ── Levenshtein edit distance ─────────────────────────────────────────────
    private int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i-1) == b.charAt(j-1)) {
                    dp[i][j] = dp[i-1][j-1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i-1][j-1],
                                   Math.min(dp[i-1][j], dp[i][j-1]));
                }
            }
        }
        return dp[la][lb];
    }
}
