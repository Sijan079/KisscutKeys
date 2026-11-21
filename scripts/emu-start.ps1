# Boots the stickertest AVD and waits for it. Exits non-zero on failure.
# Usage: .\scripts\emu-start.ps1 [-Window] [-TimeoutSec 240]
param([switch]$Window, [int]$TimeoutSec = 240)
. "$PSScriptRoot\_sdk.ps1"

if (Test-DeviceBooted) { Write-Host "A device is already running."; & $Adb devices; exit 0 }

$emuArgs = "-avd stickertest -no-audio -no-boot-anim -no-snapshot-save"
if ($Window) { $emuArgs += " -gpu auto" } else { $emuArgs += " -no-window -gpu swiftshader_indirect" }

Start-Process $Emulator -ArgumentList $emuArgs -WindowStyle Hidden
& $Adb wait-for-device

$t = 0
while (((& $Adb shell getprop sys.boot_completed 2>$null) | Out-String).Trim() -ne "1") {
    Start-Sleep 3; $t += 3
    if ($t -gt $TimeoutSec) { Write-Error "Emulator did not boot in ${TimeoutSec}s"; exit 1 }
}
& $Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
& $Adb shell wm dismiss-keyguard | Out-Null
& $Adb shell input keyevent KEYCODE_HOME | Out-Null
Write-Host "Emulator booted in ~${t}s."
& $Adb devices
exit 0
