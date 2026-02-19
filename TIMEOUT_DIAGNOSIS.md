# Timeout Diagnosis - Words Highlighting Red

## Problem
All words are highlighting RED because they're timing out. The watchdog is working correctly, but words aren't being recognized.

## Root Cause Analysis

### From the Logs:

1. **Vosk IS recognizing speech**:
   ```
   "text" : "the father"
   "text" : "the want to have the biggest house"
   ```

2. **But word matching fails**:
   ```
   ⚠️ Unmatched word: 'the' (best match: 3%, threshold: 68%)
   ⚠️ Unmatched word: 'father' (best match: 2%, threshold: 68%)
   ⚠️ Unmatched word: 'want' (best match: 13%, threshold: 90%)
   ```

3. **Words timeout because currentWordIndex = 0**:
   ```
   ⏰ Word 3 'told' timed out
   ⏰ Word 4 'his' timed out
   ⏰ Word 5 'father' timed out
   ```

### The Issue:
The expected words at index 0-5 are: `[?, ?, ?, told, his, father, ...]`

But you're reading: `"the father ... the want to have the biggest house"`

**The passage text doesn't match what you're reading!**

## Why This Happens

The word matching algorithm looks for the recognized word at the current position (currentWordIndex). If it doesn't match, it looks ahead by 2 words max. But if the recognized words are completely different from the expected words, no match is found.

Example:
- Expected word 0: "once" (or something else)
- Recognized: "the"
- Match score: 3% (way below 68% threshold)
- Result: Unmatched, currentWordIndex stays at 0
- After 3 seconds: Word 0 times out, advances to word 1
- But you're still saying "the father", which doesn't match word 1 either
- Cycle repeats...

## Solution

### Option 1: Verify Passage Content (RECOMMENDED)
Check what passage you selected vs what you're reading:

1. In the app, note the passage title
2. Check the passage content in the database
3. Make sure you're reading the correct passage

### Option 2: Increase Look-Ahead Window
If the passage is correct but words are out of order, increase the look-ahead:

In `VoskMFCCRecognizer.java`, find:
```java
int lookAheadWindow = Math.min(2, expectedWords.length - currentWordIndex);
```

Change to:
```java
int lookAheadWindow = Math.min(5, expectedWords.length - currentWordIndex);
```

This allows matching words up to 5 positions ahead.

### Option 3: Lower Match Thresholds (NOT RECOMMENDED)
This would allow weaker matches but could cause false positives.

## Testing Steps

### 1. Verify Passage Content
```cmd
adb logcat -v time | findstr /C:"expectedWords" /C:"currentPassageText"
```

Look for the log that shows what words are expected.

### 2. Check What You're Reading
The logs show you said:
- "the father"
- "the want to have the biggest house"

Is this the actual passage text?

### 3. Test with Correct Passage
1. Select a passage
2. Read EXACTLY what's shown on screen
3. Words should match and highlight correctly

## Expected Behavior After Fix

### If passage matches:
```
📝 Word 0 'the' → heard 'the' | vosk=true | phonetic=... | final=true
⏱ Watching word 1 'father' (timeout: 3000ms)
📝 Word 1 'father' → heard 'father' | vosk=true | phonetic=... | final=true
```

### If passage doesn't match:
```
⚠️ Unmatched word: 'the' (best match: 3%, threshold: 68%)
⏰ Word 0 'once' timed out — marking as mispronounced
⏱ Watching word 1 'upon' (timeout: 3000ms)
```

## Quick Fix Commands

### See expected words:
```cmd
adb logcat -v time | findstr /C:"Split passage into words"
```

### See recognized words:
```cmd
adb logcat -v time | findstr /C:"Processing recognized text"
```

### See match attempts:
```cmd
adb logcat -v time | findstr /C:"Unmatched word" /C:"Word.*vs"
```

## Next Steps

1. **Verify you're reading the correct passage** - this is most likely the issue
2. If passage is correct, increase look-ahead window
3. Test again with exact passage text

The watchdog and timeout system are working correctly - they're doing exactly what they should when words don't match. The issue is the word matching itself.
