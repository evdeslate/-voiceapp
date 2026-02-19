@echo off
REM RF Model Output Monitor Script
REM Quick access to RF model classification logs

echo ========================================
echo RF Model Output Monitor
echo ========================================
echo.
echo Choose an option:
echo 1. Monitor ALL RF outputs (class 0 and 1)
echo 2. Monitor ONLY CORRECT (class 1)
echo 3. Monitor ONLY INCORRECT (class 0)
echo 4. Clear logs and monitor
echo 5. Save outputs to file
echo 6. Count CORRECT vs INCORRECT
echo 7. Exit
echo.

set /p choice="Enter choice (1-7): "

if "%choice%"=="1" (
    echo.
    echo Monitoring ALL RF model outputs...
    echo Press Ctrl+C to stop
    echo.
    adb logcat -s RF_MODEL_OUTPUT:I
)

if "%choice%"=="2" (
    echo.
    echo Monitoring ONLY CORRECT pronunciations...
    echo Press Ctrl+C to stop
    echo.
    adb logcat -s RF_MODEL_OUTPUT:I | findstr "CORRECT"
)

if "%choice%"=="3" (
    echo.
    echo Monitoring ONLY INCORRECT pronunciations...
    echo Press Ctrl+C to stop
    echo.
    adb logcat -s RF_MODEL_OUTPUT:I | findstr "INCORRECT"
)

if "%choice%"=="4" (
    echo.
    echo Clearing logs...
    adb logcat -c
    echo Logs cleared! Now monitoring...
    echo Press Ctrl+C to stop
    echo.
    adb logcat -s RF_MODEL_OUTPUT:I
)

if "%choice%"=="5" (
    set filename=rf_outputs_%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%.txt
    set filename=%filename: =0%
    echo.
    echo Saving RF outputs to: %filename%
    echo Press Ctrl+C to stop
    echo.
    adb logcat -s RF_MODEL_OUTPUT:I > %filename%
)

if "%choice%"=="6" (
    echo.
    echo Counting classifications...
    adb logcat -d -s RF_MODEL_OUTPUT:I > temp_rf.txt
    
    for /f %%a in ('findstr /C:"Class: 1" temp_rf.txt ^| find /C /V ""') do set correct_count=%%a
    for /f %%a in ('findstr /C:"Class: 0" temp_rf.txt ^| find /C /V ""') do set incorrect_count=%%a
    
    echo.
    echo ========================================
    echo Classification Summary:
    echo ========================================
    echo CORRECT (class 1):   %correct_count%
    echo INCORRECT (class 0): %incorrect_count%
    echo ========================================
    
    del temp_rf.txt
    echo.
    pause
)

if "%choice%"=="7" (
    exit
)

echo.
pause
