@echo off
echo === Speech Detection Monitor ===
echo Monitoring: Speech detection, word matching, accuracy
echo Press Ctrl+C to stop
echo.

REM Clear logcat
adb logcat -c

REM Monitor logs
adb logcat | findstr /i "VoskMFCC StudentDetail"
