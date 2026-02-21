# Install dependencies and train model

Write-Host "📦 Installing required packages..." -ForegroundColor Cyan

pip install -r requirements_training.txt

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Packages installed!" -ForegroundColor Green

Write-Host "`n🤖 Training model..." -ForegroundColor Cyan
python train_tarsosdsp_model.py

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Training failed!" -ForegroundColor Red
    exit 1
}

Write-Host "`n✅ Training complete!" -ForegroundColor Green
Write-Host "`n📋 Next steps:" -ForegroundColor Cyan
Write-Host "1. Copy random_forest_tarsosdsp.onnx to app/src/main/assets/" -ForegroundColor White
Write-Host "2. Rename to: random_forest_model_retrained.onnx" -ForegroundColor White
Write-Host "3. Rebuild and test the app" -ForegroundColor White
