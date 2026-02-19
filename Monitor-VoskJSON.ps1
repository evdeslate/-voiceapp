# Monitor Vosk JSON Structure
# This script helps diagnose why text extraction is failing

Write-Host "=== VOSK JSON STRUCTURE MONITOR ===" -ForegroundColor Cyan
Write-Host "This will show the actual JSON being returned by Vosk" -ForegroundColor Yellow
Write-Host ""

# Clear logcat
adb logcat -c

Write-Host "Starting monitoring... Press Ctrl+C to stop" -ForegroundColor Green
Write-Host ""

# Monitor for JSON structures
adb logcat -v time | Select-String -Pattern "(onPartialResult called:|onResult called:|onFinalResult called:|Full JSON:|JSON keys:|Extracted text)" | ForEach-Object {
    $line = $_.Line
    
    # Color code different types of output
    if ($line -match "onPartialResult called:") {
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "onResult called:") {
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match "onFinalResult called:") {
        Write-Host $line -ForegroundColor Magenta
    }
    elseif ($line -match "Full JSON:") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "JSON keys:") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "Extracted text") {
        Write-Host $line -ForegroundColor Green
    }
    else {
        Write-Host $line
    }
}
