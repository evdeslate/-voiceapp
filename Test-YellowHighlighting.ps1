# Test Yellow Highlighting - Verify Real-Time Feedback Restored

Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  REAL-TIME YELLOW HIGHLIGHTING TEST" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Step 1: Rebuild
Write-Host "Step 1: Rebuilding app..." -ForegroundColor Yellow
& gradlew clean assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Build successful!" -ForegroundColor Green
Write-Host ""

# Step 2: Install
Write-Host "Step 2: Installing..." -ForegroundColor Yellow
& adb install -r app\build\outputs\apk\debug\app-debug.apk

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Installed!" -ForegroundColor Green
Write-Host ""

# Step 3: Instructions
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  TEST INSTRUCTIONS" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Open the app on your device" -ForegroundColor White
Write-Host "2. Select a student" -ForegroundColor White
Write-Host "3. Click 'Start Fluency Reading'" -ForegroundColor White
Write-Host "4. START SPEAKING the passage" -ForegroundColor Yellow
Write-Host ""
Write-Host "EXPECTED BEHAVIOR:" -ForegroundColor Cyan
Write-Host "  ✅ Words turn YELLOW as you speak them" -ForegroundColor Yellow
Write-Host "  ✅ Yellow appears IMMEDIATELY (real-time)" -ForegroundColor Yellow
Write-Host "  ✅ After reading, words turn GREEN/RED" -ForegroundColor White
Write-Host ""
Write-Host "WRONG BEHAVIOR (if still broken):" -ForegroundColor Red
Write-Host "  ❌ Words turn GREEN/RED immediately" -ForegroundColor Red
Write-Host "  ❌ No yellow highlighting during reading" -ForegroundColor Red
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "Monitoring logs (Press Ctrl+C to stop)..." -ForegroundColor Yellow
Write-Host ""

# Clear logs
& adb logcat -c

# Monitor
& adb logcat -v time | Select-String -Pattern "Highlighting summary|Word \d+ '|redrawHighlights called"
