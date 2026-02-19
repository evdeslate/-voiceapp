# Pronunciation Scoring Update

## Problem
The pronunciation detection had two issues:
1. Too strict for small words (1-3 letters) - legitimate pronunciations were marked incorrect
2. Too lenient for longer words - noticeable mispronunciations were still marked correct

## Solution: Conservative Instant Feedback + ML Verification

### Key Insight
Speech recognition (Vosk) normalizes speech - "sing-ging" becomes "singing" in text. This means text matching alone cannot detect:
- Stuttering (sing-ging)
- Hesitation (um... singing)
- Pronunciation issues that produce the right word

### Three-Layer Approach

#### 1. Conservative Instant Feedback (Visual Highlighting)
More conservative scoring to avoid false positives:

**Match Score → Instant Score → Color:**
- ≥98% match → 75-85% score → Green (correct ✓)
- ≥90% match → 65-75% score → Green (correct ✓)  
- ≥82% match → 55-63% score → Red (incorrect ✗)
- ≥70% match → 45-53% score → Red (incorrect ✗)
- <70% match → 20-40% score → Red (incorrect ✗)

**Why more conservative?**
- When text matches perfectly but pronunciation might be off (stuttering, hesitation)
- Better to show red/yellow and let ML correct upward than show green for bad pronunciation
- Students get immediate feedback that encourages clear pronunciation

#### 2. Word-Length-Aware Acceptance
#### 2. Word-Length-Aware Acceptance
Different word lengths have different acceptance thresholds:

- **1-2 letter words**: 85% match required (very strict)
- **3 letter words**: 75% match required (strict)
- **4-5 letter words**: 68% match required (moderate)
- **6+ letter words**: 65% match required (more lenient)

This ensures small words like "a", "I", "is" aren't penalized unfairly while longer words still require accurate pronunciation.

#### 3. ONNX Random Forest + MFCC Analysis (Final Scoring)
After reading completes, analyzes actual audio features:
- Detects stuttering, hesitation, pronunciation quality
- Uses 39 MFCC features (mean, std, delta)
- Provides accurate final pronunciation score
- Updates overall score (but not individual word colors)

This is why final scores may differ from instant visual feedback.

### Enhanced Match Scoring Algorithm

#### Small Words (1-3 letters):
- Heavy penalty (40% reduction) for mismatches with edit distance > 1
- Bonus only given if BOTH first letter AND length match
- Prevents false positives on short words

#### Longer Words (4+ letters):
- Standard first-letter matching bonus
- Additional penalty for significant length differences (>2 characters)
- More balanced scoring for complex words

### Stricter Phonetic Matching
- Increased phonetic similarity threshold from 75% to 80%
- Added length difference check - words with >2 character length difference are not considered phonetically similar
- Prevents accepting completely different words as "sounding similar"

## Correctness Thresholds
Updated to be more conservative:
- ≥98% match → 75-85% score (correct ✓)
- ≥90% match → 65-75% score (correct ✓)
- ≥82% match → 55-63% score (incorrect ✗)
- ≥70% match → 45-53% score (incorrect ✗)
- <70% match → 20-40% score (incorrect ✗)

## Specific Case: "Singing" vs "Sing-ging"

### The Problem
- User says: "sing-ging" (stuttering/mispronunciation)
- Vosk recognizes: "singing" (normalized text)
- Text match: 100% (perfect!)
- But pronunciation was actually wrong

### The Solution
1. **Conservative instant scoring**: Even with 100% text match, give moderate score (75-85%) not high score (85-95%)
2. **Visual feedback**: Show green but with lower confidence
3. **ONNX Random Forest**: Analyzes actual audio (MFCC features) and detects the stuttering/hesitation
4. **Final score**: Random Forest provides accurate assessment in final results

### Why This Works
- Instant feedback is conservative - doesn't over-reward perfect text matches
- ML model (Random Forest) analyzes actual pronunciation quality from audio
- Final scores reflect true pronunciation ability
- Students see immediate feedback but final assessment is accurate

## Examples

### Small Words (More Lenient)
- "a" pronounced as "uh" → Accepted (common speech variation)
- "I" pronounced as "eye" → Accepted (phonetically identical)
- "is" pronounced as "iz" → Accepted (natural pronunciation)

### Longer Words (More Strict)
- "beautiful" mispronounced as "bootiful" → Rejected (noticeable error)
- "through" mispronounced as "threw" → Rejected (different word)
- "character" mispronounced as "charater" → Likely rejected (missing syllable)

## Impact

### Balanced Accuracy
- Small words: More forgiving of natural speech variations
- Long words: Stricter detection of actual mispronunciations
- Overall: More realistic and fair assessment

### Better User Experience
- Students won't be frustrated by small words being marked wrong
- Teachers get accurate feedback on actual pronunciation issues
- Focus on meaningful pronunciation errors, not speech recognition quirks

## Technical Details

### Adaptive Algorithm
The system now considers:
1. Word length (1-2, 3, 4-5, 6+ characters)
2. Edit distance (Levenshtein)
3. Length difference between recognized and expected
4. First letter matching
5. Phonetic similarity with length constraints

### Two-Stage Scoring
1. **Instant Feedback**: Word-length-aware match-based scoring
2. **Final Scoring**: ONNX Random Forest with MFCC features

## Testing Recommendations

Test with various word lengths:
1. **Short words** (a, I, is, to, be): Should accept natural pronunciations
2. **Medium words** (read, book, story): Should require clear pronunciation
3. **Long words** (beautiful, character, wonderful): Should catch mispronunciations

Verify:
- Small words aren't overly penalized
- Noticeable mispronunciations are caught
- Overall scores reflect actual reading ability
