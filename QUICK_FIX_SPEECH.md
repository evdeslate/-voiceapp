# Quick Fix: Speech Not Detecting

## Problem
- Error: "Failed to start: Failed to create a recognizer"
- Speech not being detected

## Most Likely Cause
The Vosk model is still loading or failed to load.

## Quick Solution (Try These in Order)

### 1. Wait for Model (30 seconds)
```
1. Open the app
2. Wait 15-20 seconds (do nothing)
3. Look for toast: "Speech recognition ready (Vosk)"
4. Now try starting reading
```

### 2. Restart App
```
1. Force stop the app
2. Reopen the app
3. Wait 15 seconds
4. Try again
```

### 3. Run Diagnostic
```powershell
./diagnose-speech-issue.ps1
```
This will check:
- Device connection ✓
- App installed ✓
- Model files exist ✓
- Real-time logs

### 4. Reinstall App (If Above Fails)
```powershell
adb uninstall com.example.speak
./gradlew clean installDebug
```
Then wait 15 seconds after opening.

## What to Look For

### In Logs (Good Signs)
```
✅ Vosk model loaded successfully
✅ Model check passed
✅ Recognizer object created successfully
✅ Speech service created successfully
```

### In Logs (Bad Signs)
```
❌ Failed to load Vosk model
❌ Model not initialized
❌ Recognizer creation failed
voskModel: NULL
```

## Why This Happens

The Vosk speech recognition model:
- Is ~128MB in size
- Loads in background on app startup
- Takes 5-15 seconds to load
- Must be fully loaded before speech recognition works

## Prevention

Always wait 15 seconds after opening the app before trying to start reading.

## Need More Help?

See full troubleshooting guide: `SPEECH_RECOGNITION_TROUBLESHOOTING.md`
