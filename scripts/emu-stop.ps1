# Shuts down the running emulator.
. "$PSScriptRoot\_sdk.ps1"
& $Adb emu kill
exit $LASTEXITCODE
