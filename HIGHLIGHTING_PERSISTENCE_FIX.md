# Highlighting Persistence After Results

## Current Behavior
After RF analysis completes and results modal is shown, the word highlighting in the reading modal (underneath) should persist with the correct red/green colors based on the RF analysis results.

## How It Works

### 1. During Reading
- Words are highlighted as they're recognized
- `wordFinished[i] = true` when word is processed
- `wordScored[i] = false` initially (pending RF analysis)
- `wordCorrect[i]` set based on phonetic validation

### 2. After RF Analysis
- `onRFAnalysisComplete()` callback fires
- Updates `wordCorrect[i]` with RF results
- Sets `wordScored[i] = true` for all analyzed words
- Calls `redrawHighlights()` to update colors

### 3. Color Logic in `redrawHighlights()`
```java
if (wordScored[i]) {
    if (wordCorrect[i]) {
        color = GREEN;  // Correct
    } else {
        color = RED;    // Incorrect
    }
} else {
    color = YELLOW;     // Pending RF analysis
}
```

## Current Issue

From your logs, ALL words are showing RED because:
1. No words were recognized (currentWordIndex = 0)
2. All words timed out (watchdog fired for each word)
3. Timed-out words are marked as `wordCorrect[i] = false`
4. RF analysis has no recognized words to analyze
5. RF fills missing words with `false` (incorrect)
6. Result: All 96 words are red

## Root Cause

The words you're reading don't match the expected passage words:
- You said: "the father ... the want to have the biggest house"
- Expected: Something else (words 0-5 are: ?, ?, ?, told, his, father)
- Match fails → words timeout → all marked incorrect

## Solution

### Option 1: Read the Correct Passage (RECOMMENDED)
1. Check what passage is selected
2. Read EXACTLY what's shown on screen
3. Words will match and highlight correctly

### Option 2: Increase Look-Ahead Window
Allow matching words further ahead in the passage.

In `VoskMFCCRecognizer.java`:
```java
// Change from:
int lookAheadWindow = Math.min(2, expectedWords.length - currentWordIndex);

// To:
int lookAheadWindow = Math.min(10, expectedWords.length - currentWordIndex);
```

This allows the system to find matches up to 10 words ahead.

### Option 3: Lower Match Thresholds (NOT RECOMMENDED)
This would allow weaker matches but increases false positives.

## Testing the Fix

### 1. Rebuild with Enhanced Logging
```cmd
cd C:\Users\Elizha\AndroidStudioProjects\SPEAK
gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 2. Monitor Expected Words
```cmd
adb logcat -v time | findstr /C:"Expected words"
```

Look for:
```
📖 Expected words (first 10): once, upon, a, time, there, was, ...
```

### 3. Read the Exact Passage
Read the words shown on screen, not from memory.

### 4. Check Highlighting
After RF analysis completes:
- Correct words should be GREEN
- Incorrect words should be RED
- Colors should persist when results modal opens

## Expected Logs After Fix

### During Reading:
```
📖 Expected words (first 10): once, upon, a, time, there, was, ...
📝 Word 0 'once' → heard 'once' | vosk=true | phonetic=... | final=true
📝 Word 1 'upon' → heard 'upon' | vosk=true | phonetic=... | final=true
```

### After RF Analysis:
```
🎨 Updating 96 words with RF results
   Word 0: ✅ (finished=true, scored=true)
   Word 1: ✅ (finished=true, scored=true)
   Word 2: ❌ (finished=true, scored=true)
   ...
   RF Results: 85 correct, 11 incorrect (88.5% accuracy)
✅ Passage updated with accurate RF colors
```

### In the UI:
- Most words should be GREEN (if read correctly)
- Some words RED (if mispronounced or skipped)
- Colors persist when results modal opens
- Colors remain when results modal closes

## Verification

The highlighting persistence is already implemented correctly. The issue is that no words are being recognized, so all words timeout and are marked incorrect.

Once words are recognized correctly:
1. They'll be marked correct/incorrect based on phonetic validation
2. RF analysis will refine the scores
3. Colors will update to accurate red/green
4. Colors will persist in the reading modal

The watchdog system is working perfectly - it's doing exactly what it should when words don't match.
