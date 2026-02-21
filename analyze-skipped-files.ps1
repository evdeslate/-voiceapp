# Analyze which files might be skipped and why

Write-Host "🔍 Analyzing audio files for potential issues..." -ForegroundColor Cyan

# Get all WAV files
Write-Host "`n1. Total WAV files:" -ForegroundColor Yellow
$totalFiles = (adb shell "ls /sdcard/preprocessed_output_v2/*.wav 2>/dev/null | wc -l").Trim()
Write-Host "   $totalFiles files" -ForegroundColor White

# Check for typo variant
Write-Host "`n2. Files with 'mispronunced' typo (missing 'o'):" -ForegroundColor Yellow
$typoFiles = (adb shell "ls /sdcard/preprocessed_output_v2/*mispronunced.wav 2>/dev/null | wc -l").Trim()
Write-Host "   $typoFiles files" -ForegroundColor White
if ($typoFiles -gt 0) {
    Write-Host "   ✅ Now handled by the fix" -ForegroundColor Green
}

# Check for correct spelling
Write-Host "`n3. Files with 'mispronounced' (correct spelling):" -ForegroundColor Yellow
$correctFiles = (adb shell "ls /sdcard/preprocessed_output_v2/*mispronounced.wav 2>/dev/null | wc -l").Trim()
Write-Host "   $correctFiles files" -ForegroundColor White

# Check for correctly pronounced
Write-Host "`n4. Files with 'correctlypronounced':" -ForegroundColor Yellow
$correctlyFiles = (adb shell "ls /sdcard/preprocessed_output_v2/*correctlypronounced.wav 2>/dev/null | wc -l").Trim()
Write-Host "   $correctlyFiles files" -ForegroundColor White

# Check for files without standard labels
Write-Host "`n5. Files without standard labels:" -ForegroundColor Yellow
$allLabeled = [int]$typoFiles + [int]$correctFiles + [int]$correctlyFiles
$unlabeled = [int]$totalFiles - $allLabeled
Write-Host "   $unlabeled files" -ForegroundColor White

if ($unlabeled -gt 0) {
    Write-Host "   ⚠️  These files might be skipped!" -ForegroundColor Yellow
    Write-Host "   Showing first 10:" -ForegroundColor Gray
    
    # Find files that don't match standard patterns
    adb shell "ls /sdcard/preprocessed_output_v2/*.wav" | ForEach-Object {
        $file = $_
        if ($file -notmatch "mispronounced" -and $file -notmatch "mispronunced" -and $file -notmatch "correctlypronounced") {
            Write-Host "      $_" -ForegroundColor Red
        }
    } | Select-Object -First 10
}

# Check for very small files (< 3200 samples = 0.2 seconds at 16kHz)
Write-Host "`n6. Checking for very small audio files..." -ForegroundColor Yellow
$smallFiles = adb shell "find /sdcard/preprocessed_output_v2 -name '*.wav' -size -7k 2>/dev/null | wc -l"
Write-Host "   $smallFiles files smaller than 7KB (might be too short)" -ForegroundColor White

# Summary
Write-Host "`n📊 Summary:" -ForegroundColor Cyan
Write-Host "   Total files: $totalFiles" -ForegroundColor White
Write-Host "   Labeled correctly: $allLabeled" -ForegroundColor Green
Write-Host "   Potentially unlabeled: $unlabeled" -ForegroundColor $(if ($unlabeled -gt 0) { "Yellow" } else { "Green" })
Write-Host "   Too small: $smallFiles" -ForegroundColor $(if ($smallFiles -gt 0) { "Yellow" } else { "Green" })

$expectedSkipped = [Math]::Max($unlabeled, 0) + [Math]::Max([int]$smallFiles, 0)
Write-Host "`n   Expected to skip: ~$expectedSkipped files" -ForegroundColor Yellow
Write-Host "   Expected to process: ~$([int]$totalFiles - $expectedSkipped) files" -ForegroundColor Green
