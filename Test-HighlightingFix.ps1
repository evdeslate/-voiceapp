# Test Highlighting Fix - Monitor RF Analysis and Highlighting
# This script rebuilds the app and monitors the highlighting behavior

Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  HIGHLIGHTING FIX TEST SCRIPT" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clean and rebuild
Write-Host "Step 1: Cleaning and rebuilding app..." -ForegroundColor Yellow
Write-Host ""
& gradlew clean assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed! Fix errors and try again." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Build successful!" -ForegroundColor Green
Write-Host ""

# Step 2: Install APK
Write-Host "Step 2: Installing APK..." -ForegroundColor Yellow
Write-Host ""
& adb install -r app\build\outputs\apk\debug\app-debug.apk

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Installation failed! Check device connection." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Installation successful!" -ForegroundColor Green
Write-Host ""

# Step 3: Clear logs and start monitoring
Write-Host "Step 3: Starting log monitor..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Monitoring for:" -ForegroundColor Cyan
Write-Host "  • RF analysis completion" -ForegroundColor White
Write-Host "  • Word correctness updates" -ForegroundColor White
Write-Host "  • Highlighting color counts" -ForegroundColor White
Write-Host "  • UI refresh events" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Yellow
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Clear old logs
& adb logcat -c

# Monitor relevant logs
& adb logcat -v time | Select-String -Pattern "RF ANALYSIS COMPLETE|redrawHighlights|Highlighting summary|Word \d+:|passageContentView|RF Results:"
