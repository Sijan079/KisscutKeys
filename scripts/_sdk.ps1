# Shared paths. Dot-source from other scripts: . "$PSScriptRoot\_sdk.ps1"
$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Android\Sdk" }
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$Emulator = Join-Path $Sdk "emulator\emulator.exe"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ArtifactsDir = Join-Path $ProjectRoot "artifacts"

function Test-DeviceBooted {
    $devices = & $Adb devices | Select-String "^\S+\s+device$"
    return [bool]$devices
}
