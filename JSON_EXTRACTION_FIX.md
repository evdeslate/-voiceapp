# JSON Extraction Fix - Speech Not Being Detected

## Problem
The logs show "Only 0 out of 96 words were recognized" despite partial text being recognized by VoskMFCCRecognizer. This indicates that:
1. Vosk is receiving audio and generating results
2. The JSON extraction is failing to get the text from the results
3. No words are being processed, so highlighting doesn't work

## Root Cause
The JSON structure returned by Vosk may not match what the code expects. The code was trying to extract text from `alternatives[0].text`, but the actual JSON structure might be different.

## Solution Applied

### 1. Enhanced JSON Extraction in `onResult`
Added comprehensive extraction that tries multiple methods:
- **Method 1**: Direct `json.text` field (most common)
- **Method 2**: Extract from `alternatives[0].text` array
- **Method 3**: Extract from `result[]` array word-by-word
- **Method 4**: Log full JSON if all methods fail (for debugging)

### 2. Enhanced JSON Extraction in `onFinalResult`
Same comprehensive extraction as `onResult`:
- Tries multiple paths to extract text
- Logs full JSON structure if extraction fails
- Provides detailed error messages

### 3. Enhanced JSON Extraction in `onPartialResult`
- Extracts from `json.partial` field
- Logs JSON keys if extraction fails
- Better error handling

## Testing Steps

### Step 1: Rebuild the App
```cmd
cd C:\Users\Elizha\AndroidStudioProjects\SPEAK
gradlew clean assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Step 2: Monitor JSON Structure
Run the monitoring script to see what JSON Vosk is actually returning:
```powershell
.\Monitor-VoskJSON.ps1
```

### Step 3: Test Reading
1. Open the app
2. Select a student
3. Start reading a passage
4. Watch the PowerShell window for:
   - ✅ Green "Extracted text" messages (success)
   - ❌ Red "Full JSON" messages (failure - shows actual structure)

### Step 4: Analyze Results

#### If you see "Extracted text" messages:
✅ The fix worked! Text is being extracted and words should highlight.

#### If you see "Full JSON" messages:
❌ The JSON structure is different than expected. Look at the logged JSON and update the extraction code accordingly.

Example JSON structures to look for:
```json
// Structure 1: Direct text field
{"text": "hello world"}

// Structure 2: Alternatives array
{"alternatives": [{"text": "hello world"}]}

// Structure 3: Result array
{"result": [{"word": "hello"}, {"word": "world"}]}

// Structure 4: Partial field
{"partial": "hello world"}
```

## Expected Behavior After Fix

### During Reading:
1. Vosk receives audio
2. `onPartialResult` fires → shows partial text in logs
3. `onResult` fires → extracts text → processes words → highlights yellow
4. `onFinalResult` fires → completes reading
5. RF analysis runs → updates colors to red/green

### In Logs:
```
✅ Extracted text from json.text: 'hello world'
Processing intermediate result: 'hello world'
Word 0: 'hello' vs 'hello' - ✅ (85%)
Word 1: 'world' vs 'world' - ✅ (90%)
```

## Monitoring Commands

### Monitor JSON extraction:
```powershell
.\Monitor-VoskJSON.ps1
```

### Monitor word processing:
```cmd
adb logcat -v time | findstr /C:"Word" /C:"Extracted text" /C:"Processing"
```

### Monitor full recognition flow:
```cmd
adb logcat -v time | findstr /C:"VoskMFCCRecognizer" /C:"StudentDetail"
```

## Next Steps

1. **Rebuild and test** with the enhanced JSON extraction
2. **Monitor the logs** to see if text is being extracted
3. **If still failing**, check the "Full JSON" logs to see the actual structure
4. **Update extraction code** based on actual JSON structure if needed

## Files Modified
- `app/src/main/java/com/example/speak/VoskMFCCRecognizer.java`
  - Enhanced `onPartialResult` callback
  - Enhanced `onResult` callback  
  - Enhanced `onFinalResult` callback

## Related Issues
- Task 5: Yellow-only highlighting with post-completion RF analysis
- Task 6: Auto-advance for continuous reading flow (not started)
