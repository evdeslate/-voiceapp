# Comprehensive Speech Recognition Diagnostic Script
# This script helps diagnose why speech recognition is not working

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Speech Recognition Diagnostics" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check if device is connected
Write-Host "[1/5] Checking device connection..." -ForegroundColor Yellow
$devices = adb devices
if ($devices -match "device$") {
    Write-Host "✅ Device connected" -ForegroundColor Green
} else {
    Write-Host "❌ No device connected" -ForegroundColor Red
    Write-Host "Please connect your device and try again" -ForegroundColor Yellow
    exit
}

# Step 2: Check if app is installed
Write-Host ""
Write-Host "[2/5] Checking if app is installed..." -ForegroundColor Yellow
$packages = adb shell pm list packages | Select-String "com.example.speak"
if ($packages) {
    Write-Host "✅ App is installed" -ForegroundColor Green
} else {
    Write-Host "❌ App not installed" -ForegroundColor Red
    Write-Host "Please install the app first" -ForegroundColor Yellow
    exit
}

# Step 3: Check Vosk model files on device
Write-Host ""
Write-Host "[3/5] Checking Vosk model files..." -ForegroundColor Yellow
$modelPath = "/data/data/com.example.speak/files/vosk-model-en-us-0.22-lgraph"

$modelExists = adb shell "test -d $modelPath && echo 'exists' || echo 'missing'"
if ($modelExists -match "exists") {
    Write-Host "✅ Model directory exists" -ForegroundColor Green
    
    # Check for required subdirectories
    $amExists = adb shell "test -d $modelPath/am && echo 'exists' || echo 'missing'"
    $confExists = adb shell "test -d $modelPath/conf && echo 'exists' || echo 'missing'"
    $graphExists = adb shell "test -d $modelPath/graph && echo 'exists' || echo 'missing'"
    $ivectorExists = adb shell "test -d $modelPath/ivector && echo 'exists' || echo 'missing'"
    
    if ($amExists -match "exists") {
        Write-Host "  ✅ am/ directory exists" -ForegroundColor Green
    } else {
        Write-Host "  ❌ am/ directory missing" -ForegroundColor Red
    }
    
    if ($confExists -match "exists") {
        Write-Host "  ✅ conf/ directory exists" -ForegroundColor Green
    } else {
        Write-Host "  ❌ conf/ directory missing" -ForegroundColor Red
    }
    
    if ($graphExists -match "exists") {
        Write-Host "  ✅ graph/ directory exists" -ForegroundColor Green
    } else {
        Write-Host "  ❌ graph/ directory missing" -ForegroundColor Red
    }
    
    if ($ivectorExists -match "exists") {
        Write-Host "  ✅ ivector/ directory exists" -ForegroundColor Green
    } else {
        Write-Host "  ❌ ivector/ directory missing" -ForegroundColor Red
    }
    
    # If any directory is missing, suggest fix
    if ($amExists -notmatch "exists" -or $confExists -notmatch "exists" -or 
        $graphExists -notmatch "exists" -or $ivectorExists -notmatch "exists") {
        Write-Host ""
        Write-Host "⚠️  Model files incomplete!" -ForegroundColor Yellow
        Write-Host "Solution: Uninstall and reinstall the app to extract model files" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Model directory does not exist" -ForegroundColor Red
    Write-Host "Solution: Restart the app to trigger model extraction" -ForegroundColor Yellow
}

# Step 4: Clear logs and prepare for monitoring
Write-Host ""
Write-Host "[4/5] Clearing old logs..." -ForegroundColor Yellow
adb logcat -c
Write-Host "✅ Logs cleared" -ForegroundColor Green

# Step 5: Monitor logs
Write-Host ""
Write-Host "[5/5] Monitoring speech recognition..." -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  INSTRUCTIONS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. Open the SPEAK app" -ForegroundColor White
Write-Host "2. Go to a student" -ForegroundColor White
Write-Host "3. Select a passage" -ForegroundColor White
Write-Host "4. Click 'Start Reading'" -ForegroundColor White
Write-Host "5. Try speaking" -ForegroundColor White
Write-Host ""
Write-Host "Watch the logs below for errors..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Gray
Write-Host ""

# Monitor relevant tags with color coding
adb logcat -v time -s SpeakApplication:D VoskMFCCRecognizer:D StudentDetail:D AudioRecord:E AndroidRuntime:E | ForEach-Object {
    $line = $_
    
    # Color code based on content
    if ($line -match "VOSK MODEL LOADING STARTED") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match "VOSK MODEL LOADING COMPLETE") {
        Write-Host $line -ForegroundColor Green
        Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
        Write-Host ""
    }
    elseif ($line -match "VOSK MODEL LOADING FAILED") {
        Write-Host $line -ForegroundColor Red
        Write-Host "═══════════════════════════════════════" -ForegroundColor Red
        Write-Host ""
    }
    elseif ($line -match "START RECOGNITION CALLED") {
        Write-Host ""
        Write-Host "───────────────────────────────────────" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match "ERROR|Error|Failed|failed|❌") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match "SUCCESS|Success|Ready|ready|loaded|✅") {
        Write-Host $line -ForegroundColor Green
    }
    elseif ($line -match "WARN|Warning|⚠️") {
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match "Model|Recognizer|Audio|Speech|Recording") {
        Write-Host $line -ForegroundColor White
    }
}
