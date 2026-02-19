# ADB Logcat Monitoring Commands for Reading Session

## Complete Monitoring (All Components)

This command monitors all aspects of the reading session including audio capture, speech processing, word matching, RF analysis, and scoring:

```bash
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRandomForestScorer:D DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D ReadingLevelClassifier:D DistilBERTTextAnalyzer:D AudioDenoiser:D ReadingSessionRepo:D
```

## Focused Monitoring Commands

### 1. Audio Capture & Speech Recognition Only
Monitor audio recording and Vosk speech recognition:

```bash
adb logcat -s VoskMFCCRecognizer:D AudioDenoiser:D
```

**What you'll see:**
- Audio recording start/stop
- Audio buffer sizes
- Partial recognition results
- Final recognition results
- Word timestamps and confidence scores

### 2. Word Matching & Scoring Only
Monitor word-by-word matching and pronunciation scoring:

```bash
adb logcat -s VoskMFCCRecognizer:D | grep -E "Word [0-9]+:|Skipped word|Unmatched word|match-based"
```

**What you'll see:**
- Each word match attempt
- Match scores and similarity percentages
- Skipped words
- Unmatched words (insertions/noise)
- Match-based correctness (✅/❌)

### 3. RF Analysis & Confidence Scoring
Monitor Random Forest pronunciation analysis:

```bash
adb logcat -s VoskMFCCRecognizer:D ONNXRandomForestScorer:D | grep -E "AUDIO-ONLY|RF ANALYSIS|confidence|Word [0-9]+"
```

**What you'll see:**
- RF analysis start/completion
- Per-word RF results with confidence scores
- Hybrid analysis (text + audio)
- Low confidence auto-incorrect marking

### 4. Final Scoring & Classification
Monitor final score calculation and reading level classification:

```bash
adb logcat -s VoskMFCCRecognizer:D ReadingLevelClassifier:D ReadingSessionRepo:D | grep -E "Final scores|SESSION SAVED|Reading level|Accuracy|Pronunciation|WPM"
```

**What you'll see:**
- Final accuracy, pronunciation, comprehension scores
- WPM calculation
- Reading level classification
- Session save to database

### 5. UI Updates & Highlighting
Monitor UI updates and word highlighting:

```bash
adb logcat -s StudentDetail:D | grep -E "Word [0-9]+:|RF ANALYSIS|Updated.*words|redraw|highlight"
```

**What you'll see:**
- Word-by-word UI updates
- RF analysis callback
- Highlight color updates (green/red)
- Final UI refresh

## Detailed Monitoring with Timestamps

Add timestamps and filter for specific patterns:

```bash
adb logcat -v time -s VoskMFCCRecognizer:D StudentDetail:D ONNXRandomForestScorer:D
```

## Save Logs to File

Save all logs to a file for later analysis:

```bash
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRandomForestScorer:D DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D ReadingLevelClassifier:D DistilBERTTextAnalyzer:D AudioDenoiser:D ReadingSessionRepo:D > reading_session_log.txt
```

## Real-Time Monitoring with Color (Linux/Mac)

Use `grep` with color to highlight important events:

```bash
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRandomForestScorer:D | grep --color=always -E "✅|❌|⚠️|🎉|📊|🔍|💾|Word [0-9]+:"
```

## Windows PowerShell Monitoring

For Windows, use this command:

```powershell
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRandomForestScorer:D DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D ReadingLevelClassifier:D DistilBERTTextAnalyzer:D AudioDenoiser:D ReadingSessionRepo:D
```

## Key Log Patterns to Watch For

### Audio Capture
```
VoskMFCCRecognizer: Audio recording started
VoskMFCCRecognizer: Audio buffer: XXXX samples
```

### Speech Recognition
```
VoskMFCCRecognizer: Partial result: 'word word word'
VoskMFCCRecognizer: Final result: 'word word word'
```

### Word Matching
```
VoskMFCCRecognizer: Word 0: 'recognized' vs 'expected' - ✅ (exact match 100%, instant score: 87%)
VoskMFCCRecognizer: ⏭️  Skipped word 5: 'word' (not recognized)
VoskMFCCRecognizer: ⚠️  Unmatched word: 'word' (possible insertion/noise)
```

### RF Analysis
```
VoskMFCCRecognizer: 📊 AUDIO-ONLY PRONUNCIATION ANALYSIS
VoskMFCCRecognizer: Word 0 'word': ✅ (95% confidence)
VoskMFCCRecognizer: Word 1 'word': ❌ (45% confidence)
VoskMFCCRecognizer: ✅ AUDIO-ONLY ANALYSIS COMPLETE!
```

### Low Confidence Auto-Incorrect
```
StudentDetail: Word 5: Low confidence (0.72) - marking as INCORRECT
```

### Final Scoring
```
VoskMFCCRecognizer: 📊 Final scores - Accuracy: 85.1%, Pronunciation: 91.5% (RF), Comprehension: 75.0%, WPM: 77
ReadingLevelClassifier: Reading level: Instructional Level
```

### Session Save
```
VoskMFCCRecognizer: ✅✅✅ SESSION SAVED SUCCESSFULLY!
VoskMFCCRecognizer: ✅ Session ID: -OlklJ_7xiGk5sW6hWsq
VoskMFCCRecognizer: ✅ Accuracy: 85.1%
VoskMFCCRecognizer: ✅ Pronunciation: 91.5%
```

## Troubleshooting

### No logs appearing?
1. Check if device is connected: `adb devices`
2. Check if app is running: `adb shell ps | grep com.example.speak`
3. Try clearing logcat buffer first: `adb logcat -c`

### Too many logs?
Use more specific filters:
```bash
adb logcat -s VoskMFCCRecognizer:D | grep "Word [0-9]"
```

### Want to see errors only?
```bash
adb logcat -s VoskMFCCRecognizer:E StudentDetail:E ONNXRandomForestScorer:E
```

## Complete Session Flow in Logs

Here's what a typical reading session looks like in the logs:

```
1. Session Start
   StudentDetail: === STARTING VOSK + MFCC READING ===
   StudentDetail: Passage: Test
   StudentDetail: ✅ Computed 47 word spans

2. Audio Capture
   VoskMFCCRecognizer: Audio recording started
   VoskMFCCRecognizer: Audio buffer: 3200 samples

3. Speech Recognition (Real-time)
   VoskMFCCRecognizer: Partial result: 'maria'
   VoskMFCCRecognizer: Partial result: 'maria woke'
   VoskMFCCRecognizer: Final result: 'maria woke up early'

4. Word Matching (Real-time)
   VoskMFCCRecognizer: Word 0: 'maria' vs 'Maria' - ✅ (exact match 100%, instant score: 87%)
   VoskMFCCRecognizer: Word 1: 'woke' vs 'woke' - ✅ (exact match 100%, instant score: 85%)
   VoskMFCCRecognizer: Word 2: 'up' vs 'up' - ✅ (exact match 100%, instant score: 88%)
   VoskMFCCRecognizer: Word 3: 'early' vs 'early' - ✅ (exact match 100%, instant score: 86%)

5. RF Analysis (After completion)
   VoskMFCCRecognizer: 📊 AUDIO-ONLY PRONUNCIATION ANALYSIS
   VoskMFCCRecognizer: Word 0 'Maria': ✅ (95% confidence)
   VoskMFCCRecognizer: Word 1 'woke': ✅ (92% confidence)
   VoskMFCCRecognizer: Word 2 'up': ✅ (88% confidence)
   VoskMFCCRecognizer: Word 3 'early': ❌ (75% confidence)
   VoskMFCCRecognizer: ✅ AUDIO-ONLY ANALYSIS COMPLETE!

6. Low Confidence Check
   StudentDetail: Word 3: Low confidence (0.75) - marking as INCORRECT

7. Final Scoring
   VoskMFCCRecognizer: 📊 Final scores - Accuracy: 75.0%, Pronunciation: 85.0%, WPM: 80

8. Session Save
   VoskMFCCRecognizer: ✅✅✅ SESSION SAVED SUCCESSFULLY!
   ReadingSessionRepo: ✅ Session saved successfully: -OlklJ_7xiGk5sW6hWsq
```

## Quick Reference

| What to Monitor | Command |
|----------------|---------|
| Everything | `adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRandomForestScorer:D` |
| Audio only | `adb logcat -s VoskMFCCRecognizer:D AudioDenoiser:D` |
| Word matching | `adb logcat -s VoskMFCCRecognizer:D \| grep "Word [0-9]"` |
| RF analysis | `adb logcat -s ONNXRandomForestScorer:D \| grep confidence` |
| Final scores | `adb logcat -s VoskMFCCRecognizer:D \| grep "Final scores"` |
| Errors only | `adb logcat -s VoskMFCCRecognizer:E StudentDetail:E` |
