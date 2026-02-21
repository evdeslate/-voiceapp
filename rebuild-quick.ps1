# Quick rebuild and install with typo fix

Write-Host "🔨 Rebuilding with typo fix (mispronunced → mispronounced)..." -ForegroundColor Cyan

.\gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Build successful!" -ForegroundColor Green
Write-Host "📱 Installing..." -ForegroundColor Cyan

adb install -r app/build/outputs/apk/debug/app-debug.apk

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ App installed!" -ForegroundColor Green
Write-Host "`n🚀 Launching app..." -ForegroundColor Cyan

adb shell am start -n com.example.speak/.BatchExtractorActivity

Write-Host "`n📋 What was fixed:" -ForegroundColor Cyan
Write-Host "   - Now handles 'mispronunced' typo (missing 'o')" -ForegroundColor White
Write-Host "   - 21 files with typo will now be processed" -ForegroundColor White
Write-Host "`n📊 Expected result:" -ForegroundColor Cyan
Write-Host "   - Processed: ~1663-1665 (instead of 1642)" -ForegroundColor White
Write-Host "   - Skipped: 0-2 (instead of 23)" -ForegroundColor White

Write-Host "`n👉 Tap 'Start Extraction' in the app" -ForegroundColor Yellow
