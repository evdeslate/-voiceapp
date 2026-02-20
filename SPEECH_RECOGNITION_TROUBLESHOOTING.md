# Speech Recognition Troubleshooting Guide

## Issue: "Failed to create a recognizer" Error

### Symptoms
- Error message appears: "Failed to start: Failed to create a recognizer"
- Speech is not being detected
- App shows error when trying to start reading

### Root Causes

#### 1. Vosk Model Not Loaded
The most common cause is that the Vosk speech recognition model hasn't loaded properly.

**Check:**
- Run the diagnostic script: `./diagnose-speech-issue.ps1`
- Look for "VOSK MODEL LOADING COMPLETE" in logs
- Check if model files exist on device

**Solutions:**
- Wait 10-15 seconds after opening the app (model loads in background)
- Restart the app completely (force stop and reopen)
- If model files are missing, uninstall and reinstall the app

#### 2. Model Files Incomplete
The Vosk model requires several directories: `am/`, `conf/`, `graph/`, `ivector/`

**Check:**
- Run: `./diagnose-speech-issue.ps1` (checks model files automatically)
- Or manually: `adb shell ls /data/data/com.example.speak/files/vosk-model-en-us-0.22-lgraph/`

**Solutions:**
- Uninstall app: `adb uninstall com.example.speak`
- Reinstall app: `./gradlew installDebug`
- Model will be extracted on first launch

#### 3. Model Loading Still in Progress
The model takes 5-15 seconds to load on first app launch.

**Check:**
- Look for "Model is still loading" message
- Check logs for "isVoskModelLoading: true"

**Solutions:**
- Wait 15 seconds after opening the app
- Don't try to start reading immediately after opening app
- Look for "Speech recognition ready (Vosk)" toast message

#### 4. Model Loading Failed
Model extraction or loading encountered an error.

**Check:**
- Look for "VOSK MODEL LOADING FAILED" in logs
- Check `SpeakApplication.voskModelError` value in logs

**Solutions:**
- Check device storage space (model is ~128MB)
- Uninstall and reinstall app
- Check logcat for specific error details

### Diagnostic Steps

#### Step 1: Run Diagnostic Script
```powershell
./diagnose-speech-issue.ps1
```

This will:
- Check device connection
- Verify app is installed
- Check model files exist
- Monitor logs in real-time

#### Step 2: Check Model Status
Open the app and look for these log messages:
```
SpeakApplication: === VOSK MODEL LOADING STARTED ===
SpeakApplication: ✅ Vosk model loaded successfully in X.X seconds
SpeakApplication: === VOSK MODEL LOADING COMPLETE ===
```

#### Step 3: Try Starting Recognition
1. Go to a student
2. Select a passage
3. Click "Start Reading"
4. Watch logs for:
```
VoskMFCCRecognizer: === START RECOGNITION CALLED ===
VoskMFCCRecognizer: ✅ Model check passed, voskModel is available
VoskMFCCRecognizer: ✅ Recognizer object created successfully
```

#### Step 4: Check for Errors
If you see errors, note the specific message:
- "Model is still loading" → Wait longer
- "Model failed to load: [error]" → Check error details
- "Model not initialized" → Restart app
- "Failed to create recognizer" → Check model files

### Quick Fixes

#### Fix 1: Wait for Model to Load
```
1. Open the app
2. Wait 15 seconds (don't do anything)
3. Look for "Speech recognition ready (Vosk)" toast
4. Now try starting reading
```

#### Fix 2: Restart App
```
1. Force stop the app (Settings → Apps → SPEAK → Force Stop)
2. Reopen the app
3. Wait 15 seconds
4. Try again
```

#### Fix 3: Reinstall App
```powershell
# Uninstall
adb uninstall com.example.speak

# Clean build
./gradlew clean

# Build and install
./gradlew installDebug

# Wait 15 seconds after opening app
```

#### Fix 4: Clear App Data
```powershell
# Clear app data (keeps app installed)
adb shell pm clear com.example.speak

# Reopen app - model will be extracted again
```

### Understanding the Logs

#### Good Logs (Working)
```
SpeakApplication: === VOSK MODEL LOADING STARTED ===
SpeakApplication: Model directory path: /data/data/com.example.speak/files/vosk-model-en-us-0.22-lgraph
SpeakApplication: Model directory exists: true
SpeakApplication: ✅ Model extraction verified - all files present
SpeakApplication: Creating Model object...
SpeakApplication: ✅ Model object created successfully
SpeakApplication: ✅ Vosk model loaded successfully in 8.5 seconds
SpeakApplication: === VOSK MODEL LOADING COMPLETE ===

VoskMFCCRecognizer: === START RECOGNITION CALLED ===
VoskMFCCRecognizer: ✅ Model check passed, voskModel is available
VoskMFCCRecognizer: Creating Vosk recognizer with sample rate: 16000
VoskMFCCRecognizer: ✅ Recognizer object created successfully
VoskMFCCRecognizer: ✅ Speech service created successfully
```

#### Bad Logs (Not Working)
```
SpeakApplication: === VOSK MODEL LOADING FAILED ===
SpeakApplication: ❌ Failed to load Vosk model
SpeakApplication: Error message: [specific error]

VoskMFCCRecognizer: === START RECOGNITION CALLED ===
VoskMFCCRecognizer: voskModel: NULL
VoskMFCCRecognizer: ❌ Cannot start recognition: Model not initialized
```

### Prevention

To avoid this issue in the future:

1. **Wait for Model to Load**
   - Don't start reading immediately after opening app
   - Wait for "Speech recognition ready" toast

2. **Don't Force Close During Loading**
   - Let the app finish loading the model
   - First launch takes longer (10-15 seconds)

3. **Ensure Sufficient Storage**
   - Model requires ~128MB
   - Keep at least 500MB free space

4. **Keep App Updated**
   - Model loading improvements are ongoing
   - Update to latest version

### Still Not Working?

If none of the above fixes work:

1. **Capture Full Logs**
   ```powershell
   ./diagnose-speech-issue.ps1 > speech-logs.txt
   ```

2. **Check Model Files Manually**
   ```powershell
   adb shell ls -la /data/data/com.example.speak/files/vosk-model-en-us-0.22-lgraph/
   ```

3. **Check Device Compatibility**
   - Android 7.0+ required
   - ARM or ARM64 architecture
   - At least 2GB RAM recommended

4. **Report the Issue**
   - Include the log file
   - Mention device model and Android version
   - Describe exact steps to reproduce
