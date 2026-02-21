# Current App Pipeline - Complete Flow

## 📱 High-Level Architecture

```
User Login → Student Selection → Passage Selection → Reading Session → Results
```

---

## 🔐 Authentication Flow

```
WelcomePage
    ↓
LoginPage (Firebase Auth)
    ↓
    ├─→ Teacher → TeacherDashboard
    └─→ Parent → (Future: ParentDashboard)
```

**Components:**
- `WelcomePage.java` - Entry point
- `LoginPage.java` - Firebase Authentication (Google Sign-In)
- `SignUpActivity.java` - New user registration
- `SecurePreferences.java` - Encrypted local storage
- `UserRole.java` - Role management (Teacher/Parent)

---

## 👨‍🏫 Teacher Dashboard Flow

```
TeacherDashboard
    ↓
    ├─→ Student Management → StudentManagementActivity
    │       ↓
    │       ├─→ Add Student (AddStudentDialog)
    │       ├─→ Edit Student (EditStudentDialog)
    │       └─→ View Student Details → StudentDetail
    │
    └─→ Passage Management → PassageManagementActivity
            ↓
            ├─→ Add Passage (AddPassageDialog)
            └─→ Edit Passage (EditPassageDialog)
```

**Components:**
- `TeacherDashboard.java` - Main teacher interface
- `StudentManagementActivity.java` - Manage students
- `PassageManagementActivity.java` - Manage reading passages
- `StudentRepository.java` - Firebase student data
- `PassageRepository.java` - Firebase passage data

---

## 📖 Reading Session Flow (CURRENT FOCUS)

### 1. Session Initialization

```
StudentDetail.java
    ↓
User clicks "Start Reading"
    ↓
Select Passage
    ↓
Initialize MFCCPronunciationRecognizer
    ↓
Display passage text with word highlighting
```

### 2. Audio Recording Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│ 1. USER SPEAKS                                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. AUDIO CAPTURE                                            │
│    Component: MFCCPronunciationRecognizer                   │
│    - Starts AudioRecord (16kHz, 16-bit, Mono)              │
│    - Detects speech using energy threshold                  │
│    - Buffers audio per word                                 │
│    - Detects silence to segment words                       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. AUDIO PREPROCESSING                                      │
│    Component: AudioDenoiser                                 │
│    - Lightweight denoising (noise gate)                     │
│    - AGC (Automatic Gain Control)                           │
│    - Normalizes volume levels                               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. FEATURE EXTRACTION                                       │
│    Component: TarsosMFCCExtractor                           │
│    - Extracts MFCC features (13 coefficients)               │
│    - Processes audio in frames (512 samples, 50% overlap)   │
│    - Returns: [numFrames x 13] MFCC matrix                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. FEATURE STATISTICS                                       │
│    Component: ONNXRandomForestScorer                        │
│    - Calculates 13 means (avg per coefficient)              │
│    - Calculates 13 stds (variability per coefficient)       │
│    - Calculates 13 maxs (peak per coefficient)              │
│    - Creates feature vector: [39 features]                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. ONNX MODEL INFERENCE                                     │
│    Component: ONNXRandomForestScorer                        │
│    - Loads rf_pipeline.onnx model                           │
│    - Input: [1, 39] feature vector                          │
│    - Output: Class label (0=incorrect, 1=correct)           │
│    - Confidence: Hardcoded 80% (model outputs labels only)  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. WORD SCORING                                             │
│    Component: MFCCPronunciationRecognizer                   │
│    - Receives classification result                         │
│    - Stores score for current word                          │
│    - Triggers UI update (word highlighting)                 │
│    - Moves to next word                                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 8. UI UPDATE                                                │
│    Component: StudentDetail.java                            │
│    - Highlights word: GREEN (correct) or RED (incorrect)    │
│    - Updates progress indicator                             │
│    - Continues until all words processed                    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 9. SESSION COMPLETION                                       │
│    - Calculates overall score                               │
│    - Saves to Firebase (ReadingSessionRepository)           │
│    - Displays results                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 Key Components Breakdown

### Audio Processing Stack

```
AudioRecord (Android API)
    ↓
MFCCPronunciationRecognizer (Orchestrator)
    ↓
    ├─→ AudioDenoiser (Preprocessing)
    ├─→ TarsosMFCCExtractor (Feature Extraction)
    └─→ ONNXRandomForestScorer (ML Inference)
```

### Data Flow

```
Raw Audio (short[])
    ↓ [AudioDenoiser]
Preprocessed Audio (short[])
    ↓ [TarsosMFCCExtractor]
MFCC Features (float[][])  // [frames x 13]
    ↓ [ONNXRandomForestScorer.calculateMFCCStatistics]
Feature Vector (float[])   // [39]
    ↓ [ONNX Runtime]
Classification (int)       // 0 or 1
    ↓ [MFCCPronunciationRecognizer]
Score (float)              // 0.0 to 1.0
    ↓ [StudentDetail]
UI Update (word highlighting)
```

---

## 📊 Data Models

### Student
```java
class Student {
    String id;
    String name;
    int age;
    String gender;
    String teacherId;
}
```

### Passage
```java
class Passage {
    String id;
    String title;
    String text;
    String difficulty;
    String teacherId;
}
```

### ReadingSession
```java
class ReadingSession {
    String id;
    String studentId;
    String passageId;
    float overallScore;
    List<Float> wordScores;
    long timestamp;
}
```

---

## 🗄️ Data Storage

### Firebase Realtime Database Structure
```
/users/{userId}
    /role: "teacher" | "parent"
    /email: "user@example.com"

/students/{studentId}
    /name: "John Doe"
    /age: 8
    /gender: "male"
    /teacherId: "{userId}"

/passages/{passageId}
    /title: "The Little Red Hen"
    /text: "Once upon a time..."
    /difficulty: "beginner"
    /teacherId: "{userId}"

/reading_sessions/{sessionId}
    /studentId: "{studentId}"
    /passageId: "{passageId}"
    /overallScore: 0.85
    /wordScores: [0.9, 0.8, 1.0, ...]
    /timestamp: 1234567890
```

### Local Storage (Encrypted)
```
SecurePreferences
    - User credentials
    - Session tokens
    - Cached data
```

---

## 🎯 Current Issues & Limitations

### ❌ Critical Issues

1. **No Word Recognition**
   - Model cannot detect if wrong word was spoken
   - "litol" marked as correct when expecting "little"
   - "her" marked as correct when expecting "his"

2. **Hardcoded Confidence**
   - Model outputs class labels (0/1), not probabilities
   - Confidence always shows 80% (hardcoded fallback)
   - No real confidence measurement

3. **Model Bias**
   - Model classifies all clear audio as "correct"
   - Trained to detect audio quality, not pronunciation accuracy
   - Cannot distinguish between different words

### ⚠️ Technical Limitations

1. **MFCC Features Too Generic**
   - Cannot capture phoneme-level differences
   - Loses temporal information after averaging
   - Similar words have similar MFCC statistics

2. **No Speech Recognition**
   - Vosk was removed (size concerns)
   - No alternative speech recognition integrated
   - Cannot verify correct word was spoken

3. **Training Data Issues**
   - Model likely trained on audio quality, not pronunciation
   - Missing mispronunciation examples
   - No word-specific training

---

## ✅ What's Working

1. **Audio Recording** ✅
   - Captures audio at 16kHz, 16-bit, mono
   - Speech detection via energy threshold
   - Word segmentation via silence detection

2. **Audio Preprocessing** ✅
   - Noise reduction working
   - AGC normalizing volume levels
   - Clean audio output

3. **MFCC Extraction** ✅
   - TarsosDSP extracting 13 coefficients
   - Frame-based processing (512 samples, 50% overlap)
   - Proper audio format handling

4. **ONNX Inference** ✅
   - Model loading successfully
   - 39-feature input working
   - Classification output received

5. **UI/UX** ✅
   - Word highlighting working
   - Real-time feedback
   - Progress tracking
   - Firebase integration

---

## 🔮 What's Needed

### Immediate Fixes

1. **Add Speech Recognition**
   - Re-integrate Vosk (or alternative)
   - Verify correct word spoken
   - Only then score pronunciation quality

2. **Re-export ONNX Model**
   - Export with probabilities (not just labels)
   - Use `zipmap=False` in sklearn2onnx
   - Get real confidence scores

3. **Retrain Model**
   - Include mispronunciation examples
   - Add wrong word examples
   - Train on pronunciation accuracy, not just audio quality

### Architecture Improvements

```
Proposed Pipeline:

User Speaks
    ↓
Audio Capture
    ↓
Speech Recognition (Vosk/Whisper)
    ↓
Word Verification
    ├─→ Wrong word? → Mark INCORRECT
    └─→ Correct word? → Continue
            ↓
        MFCC Extraction
            ↓
        Pronunciation Quality Scoring
            ↓
        Final Score (word accuracy + quality)
```

---

## 📝 Summary

**Current State:**
- ✅ Full app infrastructure working
- ✅ Audio pipeline functional
- ✅ MFCC extraction working
- ✅ ONNX model running
- ❌ Cannot detect wrong words
- ❌ Cannot detect mispronunciations
- ❌ Model always says "correct"

**Root Cause:**
- Missing speech recognition component
- Model trained for wrong task (audio quality vs pronunciation)
- MFCC features alone cannot recognize words

**Solution:**
- Add speech recognition for word verification
- Retrain model with proper pronunciation data
- Use hybrid approach (recognition + quality scoring)
