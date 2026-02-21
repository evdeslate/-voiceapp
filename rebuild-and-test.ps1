# Rebuild and Test Batch Extractor
# This script rebuilds the app with permission fixes and installs it

Write-Host "🔨 Rebuilding app with permission fixes..." -ForegroundColor Cyan

# Clean and build
.\gradlew clean assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Build successful!" -ForegroundColor Green

# Install on device
Write-Host "📱 Installing on device..." -ForegroundColor Cyan
adb install -r app/build/outputs/apk/debug/app-debug.apk

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ App installed!" -ForegroundColor Green

# Check if directory exists
Write-Host "`n📂 Checking audio directory..." -ForegroundColor Cyan
$dirCheck = adb shell "ls -d /sdcard/preprocessed_output_v2 2>/dev/null"

if ($dirCheck) {
    Write-Host "✅ Directory found: /sdcard/preprocessed_output_v2" -ForegroundColor Green
    
    # Count WAV files
    $wavCount = (adb shell "ls /sdcard/preprocessed_output_v2/*.wav 2>/dev/null | wc -l").Trim()
    Write-Host "📊 WAV files found: $wavCount" -ForegroundColor Yellow
} else {
    Write-Host "⚠️  Directory not found: /sdcard/preprocessed_output_v2" -ForegroundColor Yellow
    Write-Host "   Checking alternative locations..." -ForegroundColor Yellow
    adb shell "find /sdcard -name 'preprocessed_output_v2' -type d 2>/dev/null"
}

# Launch the app
Write-Host "`n🚀 Launching BatchExtractorActivity..." -ForegroundColor Cyan
adb shell am start -n com.example.speak/.BatchExtractorActivity

Write-Host "`n📋 Next steps:" -ForegroundColor Cyan
Write-Host "1. Tap 'Start Extraction' in the app" -ForegroundColor White
Write-Host "2. Grant 'All Files Access' permission when prompted" -ForegroundColor White
Write-Host "3. Wait for extraction to complete" -ForegroundColor White
Write-Host "4. Run: adb pull /sdcard/mfcc_features.csv" -ForegroundColor White

Write-Host "`n📊 Monitor progress with:" -ForegroundColor Cyan
Write-Host "   adb logcat -s BatchExtractor:* -v time" -ForegroundColor Gray
