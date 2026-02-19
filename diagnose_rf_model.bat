@echo off
REM Comprehensive RF Model Diagnostic Script
REM Checks for: Scaler, Input Name, Feature Count

echo ========================================
echo   RF Model Comprehensive Diagnostic
echo ========================================
echo.
echo This script will help diagnose why your RF model
echo predicts Class 0 for everything.
echo.
echo Checking for:
echo   1. StandardScaler (is it loaded?)
echo   2. Input name (does it match?)
echo   3. Feature count (is it 39?)
echo.
echo ----------------------------------------
echo.

REM Clear logcat
adb logcat -c

echo Starting app monitoring...
echo Please open your app and read a passage.
echo.
echo Press Ctrl+C when done.
echo.

REM Monitor all relevant logs for RF model diagnosis
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D ReadingLevelClassifier:D DistilBERTTextAnalyzer:D AudioDenoiser:D

pause
