# Test Robust Word Detection
# Monitors the new components in action

Write-Host "=== ROBUST WORD DETECTION TEST MONITOR ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "This monitors:" -ForegroundColor Yellow
Write-Host "  • WordTimeoutWatchdog - auto-advance on mumbles/skips" -ForegroundColor White
Write-Host "  • PhoneticMatcher - validates Vosk results" -ForegroundColor White
Write-Host "  • AudioPreProcessor - noise filtering" -ForegroundColor White
Write-Host ""

# Clear logcat
adb logcat -c

Write-Host "Starting monitoring... Press Ctrl+C to stop" -ForegroundColor Green
Write-Host ""

# Monitor for robust detection events
adb logcat -v time | Select-String -Pattern "(⏱ Watching word|⏰ Timeout|PhoneticMatch|AudioPreProcessor|🚫 Vosk false-positive|Partial \(display-only\))" | ForEach-Object {
    $line = $_.Line
    
    # Color code different types of output
    if ($line -match "⏱ Watching word") {
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "⏰ Timeout") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "PhoneticMatch") {
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match "🚫 Vosk false-positive") {
        Write-Host $line -ForegroundColor Magenta
    }
    elseif ($line -match "AudioPreProcessor") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "Partial \(display-only\)") {
        Write-Host $line -ForegroundColor DarkGray
    }
    else {
        Write-Host $line
    }
}
