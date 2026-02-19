# Sequential Highlighting - Follows Reader's Progress

## Change Made

Simplified partial highlighting to follow the reader sequentially instead of searching for words.

### How It Works

1. **Counts words in partial result** - "i want to" = 3 words
2. **Highlights words sequentially** - Word 0, 1, 2 in order
3. **Stable words get yellow** - All except last word
4. **Current word gets gold** - Last word (being refined)

### Example

```
Reader at word 10 (lastFinishedIndex = 9)

Partial: "i"
  → 1 word total
  → 0 stable words (1-1=0)
  → Word 10 gets GOLD (current)

Partial: "i want"
  → 2 words total
  → 1 stable word (2-1=1)
  → Word 10 gets YELLOW (stable)
  → Word 11 gets GOLD (current)

Partial: "i want to"
  → 3 words total
  → 2 stable words (3-1=2)
  → Words 10, 11 get YELLOW (stable)
  → Word 12 gets GOLD (current)

Partial: "i want to have"
  → 4 words total
  → 3 stable words (4-1=3)
  → Words 10, 11, 12 get YELLOW (stable)
  → Word 13 gets GOLD (current)
```

## Benefits

✅ **Follows reading order** - Highlights move forward sequentially
✅ **No jumping** - Words highlighted in order
✅ **Simple logic** - Just count words, no searching
✅ **Smooth progression** - Highlights accumulate as reader progresses

## Visual Flow

```
"A little snail told his father, I want to have..."
                                  ↓    ↓    ↓
                               YELLOW YELLOW GOLD
                               (stable)(stable)(current)
```

As reader continues:
```
"A little snail told his father, I want to have the biggest..."
                                  ↓    ↓    ↓    ↓   ↓
                               YELLOW YELLOW YELLOW YELLOW GOLD
```

## Key Points

1. **Sequential only** - Doesn't search for words, just highlights in order
2. **Assumes correct reading** - Trusts reader is reading the passage in order
3. **Stable = Yellow** - All words except last get locked yellow highlight
4. **Current = Gold** - Last word gets temporary gold highlight
5. **Final = Green/Red** - After recognition completes, colors update

## Files Modified

- `app/src/main/java/com/example/speak/StudentDetail.java` (line ~2693-2735)
  - Simplified `onPartialUpdate()` to sequential highlighting

## Testing

Rebuild and test - highlighting should now:
1. Start at beginning of passage
2. Move forward word by word as you read
3. Not jump around or search
4. Follow your reading pace

## Summary

Simplified partial highlighting to follow the reader's progress sequentially. Words are highlighted in order based on the number of words in the partial result, without searching for specific words in the passage. This creates a smooth, predictable highlighting experience that follows the reader's pace.
