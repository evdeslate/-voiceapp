#!/usr/bin/env pwsh
# Comprehensive RF Model Diagnostic Script
# Checks for: Scaler, Input Name, Feature Count

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RF Model Comprehensive Diagnostic" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will help diagnose why your RF model" -ForegroundColor Yellow
Write-Host "predicts Class 0 for everything." -ForegroundColor Yellow
Write-Host ""
Write-Host "Checking for:" -ForegroundColor White
Write-Host "  1. StandardScaler (is it loaded?)" -ForegroundColor White
Write-Host "  2. Input name (does it match?)" -ForegroundColor White
Write-Host "  3. Feature count (is it 39?)" -ForegroundColor White
Write-Host ""
Write-Host "----------------------------------------" -ForegroundColor Cyan
Write-Host ""

# Clear logcat
adb logcat -c

Write-Host "Starting app monitoring..." -ForegroundColor Green
Write-Host "Please open your app and read a passage." -ForegroundColor Green
Write-Host ""
Write-Host "Press Ctrl+C when done." -ForegroundColor Yellow
Write-Host ""

# Monitor all relevant logs with color coding
adb logcat -s VoskMFCCRecognizer:D StudentDetail:D ONNXRFScorer:D ONNXRFScorer:W RF_MODEL_OUTPUT:I DirectAudioPronunciationAnalyzer:D MFCCPronunciationScorer:D ReadingLevelClassifier:D DistilBERTTextAnalyzer:D AudioDenoiser:D | ForEach-Object {
    $line = $_
    
    # Color code based on content
    if ($line -match "StandardScaler loaded") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "No StandardScaler") {
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match "Input names") {
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "Using input name") {
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "features=39") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "features=") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "Class: 0") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "Class: 1") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "MFCC") {
        Write-Host $line -ForegroundColor White
    }
    else {
        Write-Host $line -ForegroundColor Gray
    }
}
