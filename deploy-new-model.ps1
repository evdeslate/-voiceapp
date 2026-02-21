# Deploy New TarsosDSP-trained Model

Write-Host "🚀 Deploying new model to app..." -ForegroundColor Cyan

# Check if new model exists
$newModel = "random_forest_tarsosdsp.onnx"
if (-not (Test-Path $newModel)) {
    Write-Host "❌ Model file not found: $newModel" -ForegroundColor Red
    Write-Host "   Make sure you've trained the model first!" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Found new model: $newModel" -ForegroundColor Green

# Backup old model
$oldModel = "app/src/main/assets/random_forest_model_retrained.onnx"
if (Test-Path $oldModel) {
    $backup = "app/src/main/assets/random_forest_model_retrained.onnx.backup"
    Write-Host "📦 Backing up old model..." -ForegroundColor Yellow
    Copy-Item $oldModel $backup -Force
    Write-Host "   Backup saved: $backup" -ForegroundColor Gray
}

# Copy new model
Write-Host "📋 Copying new model to assets..." -ForegroundColor Cyan
Copy-Item $newModel $oldModel -Force
Write-Host "✅ Model deployed!" -ForegroundColor Green

# Show file info
$fileInfo = Get-Item $oldModel
Write-Host "`n📊 Model info:" -ForegroundColor Cyan
Write-Host "   Path: $oldModel" -ForegroundColor White
Write-Host "   Size: $([math]::Round($fileInfo.Length / 1KB, 2)) KB" -ForegroundColor White
Write-Host "   Modified: $($fileInfo.LastWriteTime)" -ForegroundColor White

Write-Host "`n🔨 Rebuilding app..." -ForegroundColor Cyan
.\gradlew assembleDebug

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

Write-Host "✅ App installed with new model!" -ForegroundColor Green

Write-Host "`n🎉 Deployment complete!" -ForegroundColor Green
Write-Host "`n📋 Next steps:" -ForegroundColor Cyan
Write-Host "1. Open the app and test pronunciation scoring" -ForegroundColor White
Write-Host "2. Try both correct and mispronounced words" -ForegroundColor White
Write-Host "3. Check logcat for scoring details:" -ForegroundColor White
Write-Host "   adb logcat -s ONNXRandomForestScorer:* MFCCPronunciationRecognizer:*" -ForegroundColor Gray
