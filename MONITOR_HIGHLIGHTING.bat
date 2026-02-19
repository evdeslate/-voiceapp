@echo off
echo ========================================
echo Speech Highlighting Monitor
echo ========================================
echo.
echo This will show you if:
echo 1. Vosk is receiving audio (partial results)
echo 2. Words are being matched
echo 3. Highlighting is being triggered
echo.
echo Press Ctrl+C to stop
echo ========================================
echo.

adb logcat -c
adb logcat | findstr /C:"onPartialResult" /C:"Word" /C:"tracked" /C:"redrawHighlights" /C:"Vosk model" /C:"recognition ready"
