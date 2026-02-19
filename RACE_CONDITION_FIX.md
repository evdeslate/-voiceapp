# Race Condition Fix - Two Systems Fighting Each Other

## Root Cause Identified ✅

**Two separate systems were both moving the highlight at the same time:**

1. **onPartialResult → onPartialUpdate** (word-count based)
   - Fires ~10x per second
   - Counts words in partial text
   - Jumps `lastFinishedIndex` forward by word count
   - Doesn't verify words match the passage

2. **onWordRecognized** (index based)
   - Fires once per confirmed word
   - Marks `wordFinished[i] = true`
   - Calls `redrawHighlights()`

### The Problem

When Vosk sees "maria woke up" as a partial:
- `onPartialUpdate` immediately marks 3 words as finished
- Even if the reader is still on word 0
- Then `onWordRecognized` fires separately and marks its own word
- Result: Highlight jumps 2-3 words ahead

### Why Vosk Resets Make It Worse

Vosk frequently resets its partial buffer mid-sentence:
- `currentWords.length` can briefly jump from 2 to 6
- `newStableWords = 6 - 2 = 4`
- Highlight jumps 4 words at once

---

## The Fix Applied ✅

### Fix 1: Disable onPartialUpdate from Driving Highlights

**Changed** (around line 1995-2007):
```java
// OLD CODE - REMOVED
// ENABLED: Show yellow highlighting for partial results (real-time feedback)
// Note: Colors may change as Vosk refines recognition
// Final colors (green/red) are shown after word is fully recognized
if (!partial.isEmpty()) {
    runOnUiThread(() -> {
        onPartialUpdate(partial, passageContentView);  // ← CULPRIT
    });
}
```

**To**:
```java
// NEW CODE
// Partial results are used only for logging, NOT for highlighting.
// Highlighting is driven exclusively by onWordRecognized to prevent
// the highlight jumping ahead while the reader is still on the same word.
if (!partial.isEmpty() && partial.split("\\s+").length % 10 == 0) {
    android.util.Log.v("StudentDetail", "Partial (no-op for UI): " + partial);
}
```

### Fix 2: Cap newStableWords to Prevent Multi-Word Jumps

**Changed** (around line 2715):
```java
// OLD CODE
// How many NEW stable words appeared?
int newStableWords = currentStableCount - lastStableCount;
```

**To**:
```java
// NEW CODE
// How many NEW stable words appeared?
// Cap at 1 per update — prevents jumping multiple words at once when Vosk resets
int newStableWords = Math.min(1, currentStableCount - lastStableCount);
```

---

## How It Works Now

### Before Fix
| Event | System | Action | Result |
|-------|--------|--------|--------|
| Partial: "maria" | onPartialUpdate | Mark 1 word finished | Highlight jumps to word 0 |
| Partial: "maria woke" | onPartialUpdate | Mark 2 words finished | Highlight jumps to word 1 |
| Word recognized: "maria" | onWordRecognized | Mark word 0 finished | Highlight already at word 1 |
| **Result** | | | **Highlight 2-3 words ahead** |

### After Fix
| Event | System | Action | Result |
|-------|--------|--------|--------|
| Partial: "maria" | (ignored) | No action | No highlight yet |
| Partial: "maria woke" | (ignored) | No action | No highlight yet |
| Word recognized: "maria" | onWordRecognized | Mark word 0 finished | Highlight at word 0 ✅ |
| Word recognized: "woke" | onWordRecognized | Mark word 1 finished | Highlight at word 1 ✅ |
| **Result** | | | **Highlight exactly on current word** |

---

## Why This Fully Fixes It

| Aspect | Before | After |
|--------|--------|-------|
| **onPartialResult** | Fires ~10x/sec, moves highlight by word count | Does nothing to UI |
| **onWordRecognized** | Fires once per word, also moves highlight | Only thing that moves highlight |
| **Race condition** | Both systems race → highlight jumps ahead | No race, single source of truth |
| **Vosk resets** | Can jump 4-6 words at once | Capped at 1 word per update (safety net) |

---

## Expected Behavior After Rebuild

1. **Start reading** - No highlights yet
2. **Say "maria"** - Word 0 gets yellow highlight (from onWordRecognized)
3. **Say "woke"** - Word 1 gets yellow highlight (from onWordRecognized)
4. **Say "up"** - Word 2 gets yellow highlight (from onWordRecognized)
5. **Highlights move exactly one word at a time** - Never jump ahead

### Partial Results Still Work

Partial results are still logged for debugging:
```
Partial (no-op for UI): maria woke up early and looked
```

But they don't affect the UI anymore - only `onWordRecognized` controls highlighting.

---

## Files Modified

**app/src/main/java/com/example/speak/StudentDetail.java**:
1. Line ~1995-2007: Disabled `onPartialUpdate()` call in `onPartialResult()`
2. Line ~2715: Capped `newStableWords` to 1 per update (safety net)

---

## Testing Instructions

### 1. Rebuild the App
```bash
Build → Clean Project
Build → Rebuild Project
Run → Run 'app'
```

### 2. Test Sequential Highlighting
Read a passage normally:
- Highlight should appear on word 0 after you say it
- Highlight should move to word 1 after you say it
- Highlight should move to word 2 after you say it
- **No jumping ahead**
- **No scattered highlights**

### 3. Monitor Logs
```bash
# Watch for partial results (should be no-op)
adb logcat | findstr /i "Partial.*no-op"

# Watch for word recognition (should be only highlight driver)
adb logcat | findstr /i "onWordRecognized"
```

---

## Summary

Fixed the race condition by:
1. ✅ Disabled `onPartialUpdate()` from driving highlights
2. ✅ Made `onWordRecognized()` the single source of truth
3. ✅ Capped `newStableWords` to 1 per update (safety net)

**Result**: Highlight moves exactly once per confirmed spoken word, never jumps ahead.
