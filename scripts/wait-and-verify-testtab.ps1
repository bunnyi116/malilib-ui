# Waits for the user's running Minecraft dev client to exit (read-only polling),
# then runs the test-tab verification and restores the user's config afterwards.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$cfg = Join-Path $root 'run\client\config\malilib-ui.json'
$backup = "$cfg.user-backup"
$ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$deadline = (Get-Date).AddMinutes(45)

function Get-DevClients {
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match 'devlaunchinjector') })
}

Write-Host ("[{0:HH:mm:ss}] waiting for the user's Minecraft client to exit (poll every 30s, timeout 45 min)" -f (Get-Date))
$client = @(Get-DevClients)
while ($client.Count -gt 0 -and (Get-Date) -lt $deadline) {
    $titles = ($client | ForEach-Object { "pid=$($_.ProcessId)" }) -join ', '
    Write-Host ("[{0:HH:mm:ss}] still running: {1}" -f (Get-Date), $titles)
    Start-Sleep -Seconds 30
    $client = @(Get-DevClients)
}

if ($client.Count -gt 0) {
    Write-Host ("[{0:HH:mm:ss}] TIMEOUT: user's client still running after 45 minutes; aborting without starting anything." -f (Get-Date))
    exit 2
}

Write-Host ("[{0:HH:mm:ss}] user's client has exited; waiting 20s before starting the verification." -f (Get-Date))
Start-Sleep -Seconds 20

$exitCode = 1
try {
    # backup + temporarily enable restyling
    Copy-Item -LiteralPath $cfg -Destination $backup -Force
    $json = Get-Content -LiteralPath $cfg -Raw | ConvertFrom-Json
    $json.Generic.enabled = $true
    ($json | ConvertTo-Json -Depth 10) | Set-Content -LiteralPath $cfg -Encoding UTF8
    Write-Host ("[{0:HH:mm:ss}] config backed up and enabled=true set temporarily" -f (Get-Date))
    Get-Content -LiteralPath $cfg -Raw

    Write-Host ("[{0:HH:mm:ss}] starting test-tab RECON (S0 + T1 screenshots; full T1-T5 runs after coordinates are confirmed)..." -f (Get-Date))
    & $ps -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\verify-test-tab-recon.ps1') -Version 26.3 -TimeoutSeconds 900
    $exitCode = $LASTEXITCODE
    Write-Host ("[{0:HH:mm:ss}] verification finished, exit code {1}" -f (Get-Date), $exitCode)
}
finally {
    if (Test-Path -LiteralPath $backup) {
        Copy-Item -LiteralPath $backup -Destination $cfg -Force
        Remove-Item -LiteralPath $backup -Force
    }
    Write-Host ("[{0:HH:mm:ss}] user config restored:" -f (Get-Date))
    Get-Content -LiteralPath $cfg -Raw

    $left = @(Get-DevClients)
    if ($left.Count -gt 0) {
        foreach ($p in $left) {
            try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; Write-Host "Stopped leftover dev client pid=$($p.ProcessId)" } catch { Write-Host "Could not stop pid=$($p.ProcessId)" }
        }
    } else {
        Write-Host "No dev client left running."
    }
}

Write-Host "WAIT-AND-VERIFY DONE (verification exit code $exitCode)"
exit 0
