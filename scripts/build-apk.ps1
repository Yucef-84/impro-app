param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
Push-Location $taskProject
try {
    $taskArguments = @('--console=plain', ':app:assembleDebug', ':app:lintDebug', ':app:testDebugUnitTest')
    if ($Offline) { $taskArguments = @('--offline') + $taskArguments }
    & ./gradlew.bat @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'APK build or validation failed.' }
    New-Item -ItemType Directory -Force -Path (Join-Path $taskProject 'apk') | Out-Null
    $taskApk = Join-Path $taskProject 'apk/impro-app-debug.apk'
    Copy-Item -LiteralPath (Join-Path $taskProject 'app/build/outputs/apk/debug/app-debug.apk') -Destination $taskApk -Force
    $taskHash = (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$taskHash  impro-app-debug.apk" | Set-Content -LiteralPath (Join-Path $taskProject 'apk/SHA256SUMS.txt') -Encoding ascii
    Write-Output "APK: $taskApk"
}
finally { Pop-Location }
