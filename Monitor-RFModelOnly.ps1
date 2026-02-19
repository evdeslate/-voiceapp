#!/usr/bin/env pwsh
# Monitor ONLY RF Model Predictions
# Shows clean output of Class 0 (INCORRECT) vs Class 1 (CORRECT)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RF Model Prediction Monitor" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Monitoring RF model predictions..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""
Write-Host "Legend:" -ForegroundColor White
Write-Host "  Class 0 = INCORRECT pronunciation" -ForegroundColor Red
Write-Host "  Class 1 = CORRECT pronunciation" -ForegroundColor Green
Write-Host ""
Write-Host "----------------------------------------" -ForegroundColor Cyan
Write-Host ""

# Clear logcat buffer
adb logcat -c

# Monitor RF model and related components
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D | ForEach-Object {
    $line = $_
    
    # Color code based on class
    if ($line -match "Class: 0") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "Class: 1") {
        Write-Host $line -ForegroundColor Green
    }
    else {
        Write-Host $line -ForegroundColor White
    }
}
