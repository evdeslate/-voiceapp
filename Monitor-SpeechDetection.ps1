# Monitor Speech Detection and Word Matching
# Shows real-time speech detection, word matching, and accuracy

Write-Host "=== Speech Detection Monitor ===" -ForegroundColor Cyan
Write-Host "Monitoring: Speech detection, word matching, accuracy" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

# Clear logcat
adb logcat -c

# Monitor with color coding
adb logcat | Select-String -Pattern "VoskMFCC|StudentDetail" | ForEach-Object {
    $line = $_.Line
    
    # Speech detection
    if ($line -match "📝 Extracted text from result array") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "📝 Intermediate text") {
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "Processing recognized text") {
        Write-Host $line -ForegroundColor Yellow
    }
    
    # Word matching - Correct
    elseif ($line -match "Word \d+.*✅.*perfect match") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "Word \d+.*✅.*excellent match") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "Word \d+.*✅.*good match") {
        Write-Host $line -ForegroundColor Cyan
    }
    
    # Word matching - Incorrect
    elseif ($line -match "Word \d+.*❌") {
        Write-Host $line -ForegroundColor Red
    }
    
    # Warnings
    elseif ($line -match "⚠️.*Unmatched word") {
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match "⏭️.*Skipped word") {
        Write-Host $line -ForegroundColor Magenta
    }
    elseif ($line -match "Empty intermediate result") {
        Write-Host $line -ForegroundColor Red
    }
    
    # Errors
    elseif ($line -match "Error parsing") {
        Write-Host $line -ForegroundColor Red
    }
    
    # Other important logs
    elseif ($line -match "🎉 All words recognized") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "Audio level.*SPEECH") {
        Write-Host $line -ForegroundColor Cyan
    }
    
    # Default
    else {
        Write-Host $line -ForegroundColor Gray
    }
}
