# Script to diagnose speech recognition issues

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Speech Recognition Diagnostics" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Clearing old logs..." -ForegroundColor Yellow
adb logcat -c

Write-Host ""
Write-Host "Monitoring speech recognition..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Instructions:" -ForegroundColor Yellow
Write-Host "1. Open the app and go to a student" -ForegroundColor White
Write-Host "2. Select a passage" -ForegroundColor White
Write-Host "3. Click Start Reading" -ForegroundColor White
Write-Host "4. Try speaking" -ForegroundColor White
Write-Host "5. Watch the logs below" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

# Monitor relevant tags
adb logcat -v time -s VoskMFCCRecognizer:D StudentDetail:D SpeakApplication:D AudioRecord:E AndroidRuntime:E | ForEach-Object {
    $line = $_
    
    if ($line -match "Model|Recognizer|Audio|Speech|Recording|Error|Exception") {
        if ($line -match "ERROR|Error|Failed|failed") {
            Write-Host $line -ForegroundColor Red
        }
        elseif ($line -match "SUCCESS|Success|Ready|ready|loaded") {
            Write-Host $line -ForegroundColor Green
        }
        elseif ($line -match "WARN|Warning") {
            Write-Host $line -ForegroundColor Yellow
        }
        else {
            Write-Host $line -ForegroundColor White
        }
    }
}
