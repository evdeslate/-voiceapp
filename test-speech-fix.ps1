# Quick Test Script for Speech Recognition Fix

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Testing Speech Recognition Fix" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build and install
Write-Host "[1/3] Building and installing app..." -ForegroundColor Yellow
Write-Host ""

$buildResult = & ./gradlew installDebug 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ App installed successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Build failed" -ForegroundColor Red
    Write-Host $buildResult
    exit
}

# Step 2: Launch app
Write-Host ""
Write-Host "[2/3] Launching app..." -ForegroundColor Yellow
adb shell am start -n com.example.speak/.WelcomePage
Start-Sleep -Seconds 2
Write-Host "✅ App launched" -ForegroundColor Green

# Step 3: Monitor logs
Write-Host ""
Write-Host "[3/3] Monitoring model loading..." -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  WATCH FOR THESE MESSAGES" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Good signs:" -ForegroundColor Green
Write-Host "  ✅ VOSK MODEL LOADING STARTED" -ForegroundColor Green
Write-Host "  ✅ Model object created successfully" -ForegroundColor Green
Write-Host "  ✅ Vosk model loaded successfully" -ForegroundColor Green
Write-Host "  ✅ VOSK MODEL LOADING COMPLETE" -ForegroundColor Green
Write-Host ""
Write-Host "Bad signs:" -ForegroundColor Red
Write-Host "  ❌ VOSK MODEL LOADING FAILED" -ForegroundColor Red
Write-Host "  ❌ Failed to load Vosk model" -ForegroundColor Red
Write-Host ""
Write-Host "Monitoring for 30 seconds..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop early" -ForegroundColor Gray
Write-Host ""

# Clear old logs
adb logcat -c

# Monitor for 30 seconds
$startTime = Get-Date
$foundSuccess = $false
$foundError = $false

adb logcat -v time -s SpeakApplication:D VoskMFCCRecognizer:D | ForEach-Object {
    $line = $_
    $elapsed = ((Get-Date) - $startTime).TotalSeconds
    
    # Stop after 30 seconds
    if ($elapsed -gt 30) {
        Write-Host ""
        Write-Host "─────────────────────────────────────" -ForegroundColor Gray
        Write-Host "30 seconds elapsed" -ForegroundColor Gray
        
        if ($foundSuccess) {
            Write-Host ""
            Write-Host "✅ SUCCESS! Model loaded correctly" -ForegroundColor Green
            Write-Host ""
            Write-Host "Next steps:" -ForegroundColor Yellow
            Write-Host "1. In the app, select Teacher or Parent" -ForegroundColor White
            Write-Host "2. Login or sign up" -ForegroundColor White
            Write-Host "3. Go to a student" -ForegroundColor White
            Write-Host "4. Select a passage" -ForegroundColor White
            Write-Host "5. Click 'Start Reading'" -ForegroundColor White
            Write-Host "6. Try speaking" -ForegroundColor White
            Write-Host ""
            Write-Host "If speech still doesn't work, run:" -ForegroundColor Yellow
            Write-Host "  ./diagnose-speech-issue.ps1" -ForegroundColor Cyan
        } elseif ($foundError) {
            Write-Host ""
            Write-Host "❌ ERROR! Model failed to load" -ForegroundColor Red
            Write-Host ""
            Write-Host "Run full diagnostic:" -ForegroundColor Yellow
            Write-Host "  ./diagnose-speech-issue.ps1" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "Or see troubleshooting guide:" -ForegroundColor Yellow
            Write-Host "  SPEECH_RECOGNITION_TROUBLESHOOTING.md" -ForegroundColor Cyan
        } else {
            Write-Host ""
            Write-Host "⚠️  No model loading messages detected" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "This could mean:" -ForegroundColor Yellow
            Write-Host "- Model already loaded from previous run" -ForegroundColor White
            Write-Host "- App didn't start properly" -ForegroundColor White
            Write-Host "- Logs not showing up" -ForegroundColor White
            Write-Host ""
            Write-Host "Try:" -ForegroundColor Yellow
            Write-Host "1. Force stop the app" -ForegroundColor White
            Write-Host "2. Run this script again" -ForegroundColor White
            Write-Host ""
            Write-Host "Or run full diagnostic:" -ForegroundColor Yellow
            Write-Host "  ./diagnose-speech-issue.ps1" -ForegroundColor Cyan
        }
        
        break
    }
    
    # Check for model loading messages
    if ($line -match "VOSK MODEL LOADING STARTED") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "VOSK MODEL LOADING COMPLETE") {
        Write-Host $line -ForegroundColor Green
        Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
        Write-Host ""
        $foundSuccess = $true
    }
    elseif ($line -match "VOSK MODEL LOADING FAILED") {
        Write-Host $line -ForegroundColor Red
        Write-Host "═══════════════════════════════════════" -ForegroundColor Red
        Write-Host ""
        $foundError = $true
    }
    elseif ($line -match "Model object created successfully") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "Vosk model loaded successfully") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "ERROR|Error|Failed|failed|❌") {
        Write-Host $line -ForegroundColor Red
        $foundError = $true
    }
    elseif ($line -match "Model|Loading|Extracting") {
        Write-Host $line -ForegroundColor White
    }
}

Write-Host ""
