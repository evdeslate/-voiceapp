# Stable Partial Highlighting - No More Jumping

## Problem

Highlights were jumping around because:
- Vosk refines partial results in real-time
- "i" → "i want" → "i want to" → "i want to have"
- Each change caused highlights to move/change
- Very confusing user experience

## Solution

Implemented **stable highlighting** that only highlights words that are unlikely to change:

### Key Concept: Stable vs Unstable Words

**Stable words:** All words except the last one
- "i want **to**" → "i" and "want" are stable, "to" is unstable
- Stable words get yellow highlight (permanent until final color)

**Unstable word:** The last word in partial result
- Still being refined by Vosk
- Gets gold highlight (temporary, will move)

### How It Works

```
Partial: "i want to"
         ↓     ↓    ↓
       stable stable unstable
         ↓     ↓      ↓
      YELLOW YELLOW GOLD
```

1. **Stable words (all but last)** → Yellow highlight, won't move
2. **Unstable word (last)** → Gold highlight, may move
3. **Next partial adds word** → Previous unstable becomes stable

### Example Flow

```
Partial: "i"
  → "i" = unstable → GOLD highlight

Partial: "i want"  
  → "i" = stable → YELLOW highlight (locked)
  → "want" = unstable → GOLD highlight

Partial: "i want to"
  → "i" = stable → YELLOW (locked)
  → "want" = stable → YELLOW (locked)
  → "to" = unstable → GOLD highlight

Partial: "i want to have"
  → "i", "want", "to" = stable → YELLOW (locked)
  → "have" = unstable → GOLD highlight
```

## Benefits

✅ **No jumping** - Stable words stay highlighted
✅ **Clear feedback** - Yellow = confirmed, Gold = being processed
✅ **Progressive** - Highlights accumulate as you read
✅ **Accurate** - Only highlights words that match passage

## Visual Indicators

- **YELLOW (#FFEB3B)** - Stable word, confirmed by Vosk
- **GOLD (#FFD54F)** - Current word, still being refined
- **GREEN** - Final result, pronounced correctly
- **RED** - Final result, mispronounced or skipped

## Additional Improvements

1. **Sequential search** - Starts from last highlighted word
2. **Fuzzy matching** - Handles partial word recognition
3. **Skip processed words** - Won't re-highlight finished words
4. **Lookahead limit** - Only searches next 5 words (performance)

## Files Modified

- `app/src/main/java/com/example/speak/StudentDetail.java` (line ~2693-2760)
  - Rewrote `onPartialUpdate()` with stable/unstable word logic

## Testing

Rebuild and test - you should see:
1. Words turn GOLD as you speak them
2. GOLD turns to YELLOW when next word starts
3. YELLOW stays in place (no jumping)
4. Final colors (GREEN/RED) replace YELLOW after recognition

## Summary

Implemented stable highlighting that only locks in words that are unlikely to change, while showing a temporary gold highlight for the word currently being refined. This eliminates the jumping behavior while still providing real-time feedback.
