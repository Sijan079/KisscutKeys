# Runs JVM unit tests; if a device is booted, also instrumented tests. Prints a summary.
. "$PSScriptRoot\_sdk.ps1"
$gradlew = Join-Path $ProjectRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) { Write-Error "gradlew.bat not found in $ProjectRoot"; exit 2 }

Push-Location $ProjectRoot
try {
    & $gradlew test
    $unit = if ($LASTEXITCODE -eq 0) { "PASS" } else { "FAIL" }

    $inst = "SKIPPED (no booted device)"
    if (Test-DeviceBooted) {
        & $gradlew connectedAndroidTest
        $inst = if ($LASTEXITCODE -eq 0) { "PASS" } else { "FAIL" }
    }
} finally { Pop-Location }

Write-Host ""
Write-Host "==== Test summary ===="
Write-Host "Unit tests (test):                       $unit"
Write-Host "Instrumented (connectedAndroidTest):     $inst"
Write-Host "Reports: $ProjectRoot\ui\android\build\reports\tests"
Write-Host "         $ProjectRoot\ui\android\build\reports\androidTests"
Write-Host "         $ProjectRoot\shared\core\build\reports\tests"
Write-Host "         $ProjectRoot\shared\db\build\reports\tests"

if ($unit -eq "FAIL" -or $inst -eq "FAIL") { exit 1 }
exit 0
