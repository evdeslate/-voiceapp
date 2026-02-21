# Commit TarsosDSP Model Integration Changes

Write-Host "📝 Committing changes to git..." -ForegroundColor Cyan

# Check git status
Write-Host "`n📊 Current status:" -ForegroundColor Yellow
git status --short

# Add all changes
Write-Host "`n➕ Adding files..." -ForegroundColor Cyan
git add .

# Create commit
$commitMessage = @"
feat: Implement TarsosDSP-based pronunciation scoring with batch extraction

Major Changes:
- Added BatchFeatureExtractor for extracting features from 1665 WAV files
- Added BatchExtractorActivity UI for batch processing
- Updated ONNXRandomForestScorer to use raw (unnormalized) features
- Fixed permission handling for Android 11+ (MANAGE_EXTERNAL_STORAGE)
- Removed min-max normalization (model trained on raw features)
- Added typo handling for 'mispronunced' filenames
- Disabled LOGGING_MODE after feature collection

Technical Details:
- Features: 13 MFCC means + 13 deltas + 13 delta-deltas = 39 features
- Preprocessing: Denoising → AGC → RMS normalization (0.1)
- Extracted 1664 samples from phone audio files
- Model trained using TarsosDSP features (perfect feature matching)

Files Modified:
- app/src/main/java/com/example/speak/ONNXRandomForestScorer.java
- app/src/main/java/com/example/speak/BatchFeatureExtractor.java
- app/src/main/java/com/example/speak/BatchExtractorActivity.java
- app/src/main/AndroidManifest.xml
- app/src/main/res/layout/activity_batch_extractor.xml

Scripts Added:
- train_tarsosdsp_model.py
- retrain_balanced.py
- calculate_minmax.py
- deploy-new-model.ps1
- rebuild-and-test.ps1
- Various documentation files

Issue: Model still biased toward 'correct' due to class imbalance (81% vs 19%)
Next: Retrain with SMOTE balancing using retrain_balanced.py
"@

Write-Host "`n💾 Creating commit..." -ForegroundColor Cyan
git commit -m $commitMessage

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Commit created successfully!" -ForegroundColor Green
    
    Write-Host "`n📋 Commit details:" -ForegroundColor Cyan
    git log -1 --stat
    
    Write-Host "`n🚀 To push to remote:" -ForegroundColor Yellow
    Write-Host "   git push origin main" -ForegroundColor Gray
} else {
    Write-Host "❌ Commit failed!" -ForegroundColor Red
}
