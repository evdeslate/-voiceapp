# Diagnose Latency - Find what's slowing down word detection

Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  LATENCY DIAGNOSTIC" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "This will show timing for each component:" -ForegroundColor Yellow
Write-Host "  • Vosk recognition time" -ForegroundColor White
Write-Host "  • PhoneticMatcher time" -ForegroundColor White
Write-Host "  • AudioPreProcessor time" -ForegroundColor White
Write-Host "  • Highlighting time" -ForegroundColor White
Write-Host ""
Write-Host "Please start reading and watch for slow components" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""

# Clear logs
adb logcat -c

# Monitor timing
adb logcat -v time | Select-String -Pattern "Word \d+ '|PhoneticMatch|AudioPreProcessor|took|ms|latency|slow"
