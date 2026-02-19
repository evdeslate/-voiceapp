# Monitor RF Analysis Results
Write-Host "=== Monitoring RF Analysis ===" -ForegroundColor Cyan
Write-Host "Waiting for RF analysis to complete..." -ForegroundColor Yellow
Write-Host ""

adb logcat -v time | Select-String -Pattern "Word \d+.*'.*'.*✅|Word \d+.*'.*'.*❌|RF Results:|Audio-based:|classification|isCorrect\(\)" | ForEach-Object {
    $line = $_.Line
    
    if ($line -match "✅") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "❌") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "RF Results") {
        Write-Host $line -ForegroundColor Cyan
    }
    else {
        Write-Host $line -ForegroundColor White
    }
}
