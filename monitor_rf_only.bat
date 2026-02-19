@echo off
REM Monitor ONLY RF Model Predictions
REM Shows clean output of Class 0 (INCORRECT) vs Class 1 (CORRECT)

echo ========================================
echo   RF Model Prediction Monitor
echo ========================================
echo.
echo Monitoring RF model predictions...
echo Press Ctrl+C to stop
echo.
echo Legend:
echo   Class 0 = INCORRECT pronunciation
echo   Class 1 = CORRECT pronunciation
echo.
echo ----------------------------------------
echo.

REM Clear logcat buffer
adb logcat -c

REM Monitor RF model and related components
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D
