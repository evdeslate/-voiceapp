# Auto-Advance for Continuous Reading Flow

## Problem
When a child mumbles or speaks unclearly, Vosk cannot recognize the word, and the app gets stuck waiting for recognition. This breaks the reading flow.

## Solution
Implement an auto-advance mechanism that:
1. Tracks time since last word was recognized
2. After 3-5 seconds of no recognition, automatically advances to next word
3. Marks the skipped word as incorrect (RED)
4. Allows continuous reading flow

## Implementation Approach

### Option 1: Timeout-Based Auto-Advance (Recommended)
- Start a timer when a word is expected
- If no recognition within 3 seconds, mark word as skipped/incorrect
- Advance to next word automatically
- Continue reading flow

### Option 2: Manual Skip Button
- Add a "Skip Word" button
- Teacher/student can manually skip difficult words
- Marked as incorrect but allows progress

### Option 3: Hybrid Approach
- Auto-advance after timeout
- Also provide manual skip button for immediate control

## Current Behavior
- Grammar-constrained recognition waits indefinitely
- No timeout mechanism
- Reading gets stuck on unrecognized words

## Desired Behavior
- Continuous reading flow
- Auto-advance after 3-5 seconds
- Skipped words marked as incorrect
- RF model still analyzes all audio (including skipped words)

## Technical Implementation
1. Add timer in `onPartialResult` callback
2. Track last recognition timestamp
3. If `currentTime - lastRecognitionTime > TIMEOUT`, trigger skip
4. Call `onWordRecognized` with empty recognized word and `isCorrect = false`
5. Increment word index to advance

This maintains assessment integrity while providing better UX for struggling readers.
