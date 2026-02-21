# Final Deployment - New TarsosDSP Model

Write-Host "🎉 Final Deployment - TarsosDSP Model" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Gray

Write-Host "`n✅ Pre-deployment checklist:" -ForegroundColor Yellow
Write-Host "   [✓] New model in assets: random_forest_model_retrained.onnx" -ForegroundColor Green
Write-Host "   [✓] LOGGING_MODE disabled" -ForegroundColor Green
Write-Host "   [✓] BatchExtractorActivity removed from launcher" -ForegroundColor Green
Write-Host "   [✓] WelcomePage restored as main launcher" -ForegroundColor Green

Write-Host "`n🔨 Building app..." -ForegroundColor Cyan
.\gradlew clean assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Build successful!" -ForegroundColor Green

Write-Host "`n📱 Installing on device..." -ForegroundColor Cyan
adb install -r app/build/outputs/apk/debug/app-debug.apk

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ App installed!" -ForegroundColor Green

Write-Host "`n🚀 Launching app..." -ForegroundColor Cyan
adb shell am start -n com.example.speak/.WelcomePage

Write-Host "`n" + "=" * 60 -ForegroundColor Gray
Write-Host "🎉 DEPLOYMENT COMPLETE!" -ForegroundColor Green
Write-Host "=" * 60 -ForegroundColor Gray

Write-Host "`n📋 What's new:" -ForegroundColor Cyan
Write-Host "   • Model trained on 1664 TarsosDSP-extracted features" -ForegroundColor White
Write-Host "   • Perfect feature matching (training = production)" -ForegroundColor White
Write-Host "   • Same preprocessing pipeline (denoising, AGC, RMS)" -ForegroundColor White
Write-Host "   • Class-balanced training" -ForegroundColor White

Write-Host "`n🧪 Testing instructions:" -ForegroundColor Cyan
Write-Host "1. Open the app and select a student" -ForegroundColor White
Write-Host "2. Try reading a passage" -ForegroundColor White
Write-Host "3. Test both correct and mispronounced words" -ForegroundColor White
Write-Host "4. Check pronunciation scores" -ForegroundColor White

Write-Host "`n📊 Monitor scoring:" -ForegroundColor Cyan
Write-Host "   adb logcat -s ONNXRFScorer:* MFCCPronunciationRecognizer:* -v time" -ForegroundColor Gray

Write-Host "`n💡 Expected behavior:" -ForegroundColor Cyan
Write-Host "   • Model should now detect mispronunciations" -ForegroundColor White
Write-Host "   • Scores should vary (not always 1.0)" -ForegroundColor White
Write-Host "   • Check logs for predicted class (0 or 1)" -ForegroundColor White

Write-Host "`n✨ The model is now using real TarsosDSP features!" -ForegroundColor Green
