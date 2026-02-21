# Check Audio Files Location
# Verifies that the 1665 WAV files are accessible on the device

Write-Host "🔍 Checking audio files on device..." -ForegroundColor Cyan

# Check if directory exists
Write-Host "`n1. Checking directory existence..." -ForegroundColor Yellow
$dirExists = adb shell "test -d /sdcard/preprocessed_output_v2 && echo 'EXISTS' || echo 'NOT_FOUND'"

if ($dirExists -match "EXISTS") {
    Write-Host "   ✅ Directory found: /sdcard/preprocessed_output_v2" -ForegroundColor Green
} else {
    Write-Host "   ❌ Directory not found: /sdcard/preprocessed_output_v2" -ForegroundColor Red
    Write-Host "`n   Searching for alternative locations..." -ForegroundColor Yellow
    adb shell "find /sdcard -name 'preprocessed_output_v2' -type d 2>/dev/null"
    exit 1
}

# Count WAV files
Write-Host "`n2. Counting WAV files..." -ForegroundColor Yellow
$wavCount = (adb shell "ls /sdcard/preprocessed_output_v2/*.wav 2>/dev/null | wc -l").Trim()

if ($wavCount -gt 0) {
    Write-Host "   ✅ Found $wavCount WAV files" -ForegroundColor Green
    
    if ($wavCount -eq 1665) {
        Write-Host "   ✅ Correct count (expected 1665)" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Expected 1665 files, found $wavCount" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ❌ No WAV files found" -ForegroundColor Red
    exit 1
}

# Show sample filenames
Write-Host "`n3. Sample filenames:" -ForegroundColor Yellow
$samples = adb shell "ls /sdcard/preprocessed_output_v2/*.wav 2>/dev/null | head -5"
$samples -split "`n" | ForEach-Object {
    if ($_ -match "(\d+\w+_(mispronounced|correctlypronounced)\.wav)") {
        $filename = $matches[1]
        Write-Host "   📄 $filename" -ForegroundColor Gray
    }
}

# Check file permissions
Write-Host "`n4. Checking directory permissions..." -ForegroundColor Yellow
$perms = adb shell "ls -ld /sdcard/preprocessed_output_v2"
Write-Host "   $perms" -ForegroundColor Gray

# Check available space
Write-Host "`n5. Checking available space..." -ForegroundColor Yellow
$space = adb shell "df -h /sdcard | tail -1"
Write-Host "   $space" -ForegroundColor Gray

Write-Host "`n✅ All checks passed! Ready for batch extraction." -ForegroundColor Green
Write-Host "`nNext step: Run .\rebuild-and-test.ps1" -ForegroundColor Cyan
