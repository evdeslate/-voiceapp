# RF Model Output Monitor - PowerShell Script
# Provides colored output and better filtering

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("all", "correct", "incorrect", "count", "clear")]
    [string]$Mode = "all",
    
    [Parameter(Mandatory=$false)]
    [string]$Word = ""
)

function Show-Menu {
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "RF Model Output Monitor" -ForegroundColor Cyan
    Write-Host "========================================`n" -ForegroundColor Cyan
    
    Write-Host "Usage:" -ForegroundColor Yellow
    Write-Host "  .\Monitor-RFModel.ps1 -Mode all          # Monitor all outputs"
    Write-Host "  .\Monitor-RFModel.ps1 -Mode correct      # Only CORRECT (class 1)"
    Write-Host "  .\Monitor-RFModel.ps1 -Mode incorrect    # Only INCORRECT (class 0)"
    Write-Host "  .\Monitor-RFModel.ps1 -Mode count        # Count classifications"
    Write-Host "  .\Monitor-RFModel.ps1 -Mode clear        # Clear logs and monitor"
    Write-Host "  .\Monitor-RFModel.ps1 -Mode all -Word singing  # Filter specific word`n"
}

function Monitor-All {
    Write-Host "`nMonitoring ALL RF model outputs..." -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop`n" -ForegroundColor Yellow
    
    if ($Word) {
        adb logcat -s RF_MODEL_OUTPUT:I | Select-String -Pattern $Word
    } else {
        adb logcat -s RF_MODEL_OUTPUT:I | ForEach-Object {
            if ($_ -match "Class: 1.*CORRECT") {
                Write-Host $_ -ForegroundColor Green
            } elseif ($_ -match "Class: 0.*INCORRECT") {
                Write-Host $_ -ForegroundColor Red
            } else {
                Write-Host $_
            }
        }
    }
}

function Monitor-Correct {
    Write-Host "`nMonitoring ONLY CORRECT pronunciations (Class 1)..." -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop`n" -ForegroundColor Yellow
    
    adb logcat -s RF_MODEL_OUTPUT:I | Select-String -Pattern "Class: 1" | ForEach-Object {
        Write-Host $_ -ForegroundColor Green
    }
}

function Monitor-Incorrect {
    Write-Host "`nMonitoring ONLY INCORRECT pronunciations (Class 0)..." -ForegroundColor Red
    Write-Host "Press Ctrl+C to stop`n" -ForegroundColor Yellow
    
    adb logcat -s RF_MODEL_OUTPUT:I | Select-String -Pattern "Class: 0" | ForEach-Object {
        Write-Host $_ -ForegroundColor Red
    }
}

function Count-Classifications {
    Write-Host "`nCounting classifications..." -ForegroundColor Cyan
    
    $output = adb logcat -d -s RF_MODEL_OUTPUT:I
    $correctCount = ($output | Select-String -Pattern "Class: 1").Count
    $incorrectCount = ($output | Select-String -Pattern "Class: 0").Count
    $total = $correctCount + $incorrectCount
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "Classification Summary" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "CORRECT (class 1):   " -NoNewline
    Write-Host "$correctCount" -ForegroundColor Green
    Write-Host "INCORRECT (class 0): " -NoNewline
    Write-Host "$incorrectCount" -ForegroundColor Red
    Write-Host "TOTAL:               $total" -ForegroundColor Cyan
    
    if ($total -gt 0) {
        $correctPercent = [math]::Round(($correctCount / $total) * 100, 1)
        $incorrectPercent = [math]::Round(($incorrectCount / $total) * 100, 1)
        Write-Host "`nPercentages:" -ForegroundColor Yellow
        Write-Host "  Correct:   $correctPercent%" -ForegroundColor Green
        Write-Host "  Incorrect: $incorrectPercent%" -ForegroundColor Red
    }
    
    Write-Host "========================================`n" -ForegroundColor Cyan
    
    # Diagnosis
    if ($total -eq 0) {
        Write-Host "⚠️  No RF outputs found. Make sure app is running." -ForegroundColor Yellow
    } elseif ($incorrectCount -eq $total) {
        Write-Host "❌ ALL words classified as INCORRECT - Model too strict!" -ForegroundColor Red
        Write-Host "   → Model needs retraining with balanced data" -ForegroundColor Yellow
    } elseif ($correctCount -eq $total) {
        Write-Host "❌ ALL words classified as CORRECT - Model too lenient!" -ForegroundColor Red
        Write-Host "   → Model needs retraining with more incorrect samples" -ForegroundColor Yellow
    } else {
        Write-Host "✅ Model showing mixed classifications - Good!" -ForegroundColor Green
    }
}

function Clear-AndMonitor {
    Write-Host "`nClearing logs..." -ForegroundColor Yellow
    adb logcat -c
    Write-Host "Logs cleared!`n" -ForegroundColor Green
    Monitor-All
}

# Main execution
switch ($Mode) {
    "all" { Monitor-All }
    "correct" { Monitor-Correct }
    "incorrect" { Monitor-Incorrect }
    "count" { Count-Classifications }
    "clear" { Clear-AndMonitor }
    default { Show-Menu }
}
