# Saves a screenshot of the device to artifacts\<name>.png
# Usage: .\scripts\screenshot.ps1 <name>
param([Parameter(Mandatory = $true)][string]$Name)
. "$PSScriptRoot\_sdk.ps1"

New-Item -ItemType Directory -Force $ArtifactsDir | Out-Null
$out = Join-Path $ArtifactsDir "$Name.png"
# cmd /c avoids PowerShell re-encoding the binary stream
cmd /c "`"$Adb`" exec-out screencap -p > `"$out`""
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $out) -or (Get-Item $out).Length -eq 0) {
    Write-Error "Screenshot failed"; exit 1
}
Write-Host $out
exit 0
