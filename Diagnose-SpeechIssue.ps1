# Diagnostic Script - Speech Recognition & Highlighting Issues
# This will help identify what's broken

Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SPEECH RECOGNITION DIAGNOSTIC" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "Checking device connection..." -ForegroundColor Yellow
$device = adb devices | Select-String "device$"
if (!$device) {
    Write-Host "❌ No device connected!" -ForegroundColor Red
    Write-Host "Please connect your device and try again." -ForegroundColor Yellow
    exit 1
}
Write-Host "✅ Device connected" -ForegroundColor Green
Write-Host ""

Write-Host "Starting diagnostic monitoring..." -ForegroundColor Yellow
Write-Host "Please:" -ForegroundColor Cyan
Write-Host "  1. Open the app" -ForegroundColor White
Write-Host "  2. Select a student" -ForegroundColor White
Write-Host "  3. Click 'Start Fluency Reading'" -ForegroundColor White
Write-Host "  4. Try to read a few words" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C when done" -ForegroundColor Yellow
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Clear old logs
adb logcat -c

# Monitor for key events
adb logcat -v time | Select-String -Pattern "StudentDetail|VoskMFCCRecognizer|Vosk model|Speech recognition|onPartialResult|onWordRecognized|redrawHighlights|wordFinished|ERROR|FATAL"
