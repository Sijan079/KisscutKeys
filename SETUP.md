# SETUP.md: Android dev and test environment without Android Studio

Audience: Claude Code, running on a Windows 11 machine (PowerShell).
Goal: build, install, run and test the sticker app on an Android emulator using only command-line tools.
Do the steps in order. After each step run its **Verify** line. If a verify fails, stop and report the exact error instead of working around it.

Version numbers below (API 35, etc.) are defaults. Run `sdkmanager --list` to confirm what exists before installing.

---

## 0. Preconditions (report, don't fix silently)

1. **Virtualization must be on.** Without it the emulator is unusably slow.
   - Check: `systeminfo | findstr /i "Hyper-V"` shows "A hypervisor has been detected" or the Hyper-V requirements as Yes.
   - If not: the user must enable virtualization in BIOS and turn on the "Windows Hypervisor Platform" optional feature (admin PowerShell: `Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform`, then reboot). Tell the user; do not run admin commands without asking.
2. **Disk:** at least 15 GB free (SDK + system image + AVD + Gradle cache).
3. **Network:** downloads need access to `dl.google.com`, `maven.google.com`, Maven Central and `services.gradle.org`. If any is blocked, say which.

Verify: report the three results above in one short message before continuing.

---

## 1. JDK

Install a JDK 17 or 21 (Temurin recommended). Gradle and the Android Gradle Plugin need it; no separate Gradle install is needed because the project uses the Gradle wrapper.

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Open a new shell, then set `JAVA_HOME` if it is not set automatically.

Verify: `java -version` prints 17 or 21, and `echo $env:JAVA_HOME` is non-empty.

---

## 2. Android SDK command-line tools

1. Download "Command line tools only" for Windows from https://developer.android.com/studio (scroll to "Command line tools only"). Unzip it.
2. Create the SDK root and place the tools exactly like this. **The `latest` folder name is required**, or `sdkmanager` will fail.

```
C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
C:\Android\Sdk\cmdline-tools\latest\lib\...
```

(The zip unpacks to a `cmdline-tools` folder; move its contents into `latest`.)

3. Set environment variables (user level, then open a new shell):

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "C:\Android\Sdk", "User")
$p = [Environment]::GetEnvironmentVariable("Path", "User")
$add = "C:\Android\Sdk\cmdline-tools\latest\bin;C:\Android\Sdk\platform-tools;C:\Android\Sdk\emulator"
[Environment]::SetEnvironmentVariable("Path", "$p;$add", "User")
```

Verify: in a new shell, `sdkmanager --version` prints a version number.

---

## 3. Install SDK packages and accept licenses

```powershell
sdkmanager --licenses            # answer y to all
sdkmanager "platform-tools" "emulator" "platforms;android-35" "build-tools;35.0.0" "system-images;android-35;google_apis;x86_64"
```

Notes:
- `google_apis` is enough for this app. Do not use `google_apis_playstore` unless testing Play features.
- Use `x86_64` images on this machine. ARM images are far slower without Apple silicon.
- If the NDK is ever needed: `sdkmanager "ndk;<version>"` (not needed now).

Verify: `adb version` and `emulator -version` both print versions; `sdkmanager --list_installed` lists the packages above.

---

## 4. Create the test emulator (AVD)

```powershell
avdmanager create avd -n stickertest -k "system-images;android-35;google_apis;x86_64" -d pixel_6
```

If it asks about a custom hardware profile, answer `no`.

Verify: `avdmanager list avd` shows `stickertest`.

Optional tuning: edit `%USERPROFILE%\.android\avd\stickertest.avd\config.ini` and set `hw.ramSize=3072` and `hw.keyboard=yes`.

---

## 5. Boot the emulator

Headless (for Claude Code to run unattended):

```powershell
Start-Process emulator -ArgumentList "-avd stickertest -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-save" -WindowStyle Hidden
```

With a visible window (so the user can watch): drop `-no-window` and use `-gpu auto`.

Wait for boot, with a timeout:

```powershell
adb wait-for-device
$t = 0
while ((adb shell getprop sys.boot_completed 2>$null).Trim() -ne "1") {
  Start-Sleep 3; $t += 3
  if ($t -gt 240) { throw "Emulator did not boot in 240s" }
}
adb shell input keyevent 82   # unlock
```

Verify: `adb devices` lists `emulator-5554  device`.

Shut down when done: `adb emu kill`.

---

## 6. Project build and test (Gradle wrapper)

The Gradle project and its wrapper live in `app/`. Run every Gradle command from there. Never install Gradle globally.

```powershell
cd app
.\gradlew.bat assembleDebug            # build the APK
.\gradlew.bat test                     # JVM unit tests (shared core, Room DAOs via in-memory DB where possible)
.\gradlew.bat connectedAndroidTest     # instrumented tests on the running emulator
.\gradlew.bat installDebug             # install on the emulator
```

Rules:
- Run `test` first; it needs no emulator and is the cheapest signal.
- Only run `connectedAndroidTest` when `adb devices` shows a booted device.
- Test reports: `app\build\reports\tests` and `app\build\reports\androidTests`.
- First build downloads a lot; allow several minutes.

Verify: `assembleDebug` ends with BUILD SUCCESSFUL and an APK exists under `app\build\outputs\apk\debug\`.

---

## 7. Driving the app without an MCP (plain adb)

These are enough for Claude Code to look at and operate the app.

```powershell
adb exec-out screencap -p > screen.png                 # screenshot (read the PNG)
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml   # UI tree
adb shell input tap X Y
adb shell input swipe X1 Y1 X2 Y2 300
adb shell input text "hello"
adb shell monkey -p <package.id> -c android.intent.category.LAUNCHER 1   # launch app
adb logcat -d -s <TAG>                                  # read logs
adb logcat -c                                           # clear logs
```

Prefer the UI tree over screenshots for finding tap targets (cheaper and exact). Use screenshots to judge visuals.

On Windows PowerShell, `>` may re-encode binary output. If `screen.png` is corrupt, use `cmd /c "adb exec-out screencap -p > screen.png"` instead.

---

## 8. Testing the keyboard (IME)

The keyboard is `app.kisscut.keys/.keyboard.StickerImeService`. After `installDebug`:

```powershell
adb shell ime enable app.kisscut.keys/.keyboard.StickerImeService
adb shell ime set app.kisscut.keys/.keyboard.StickerImeService
adb shell am start -n app.kisscut.keys/.debug.ImeTestActivity   # debug-only test fields
```

Tap a field in `ImeTestActivity`; the keyboard opens on letters (or on stickers if they were used last). Fill the library first with `adb shell am broadcast -n app.kisscut.keys/.debug.SeedReceiver --ez clear true`.

Reset to the default keyboard afterwards: `adb shell ime reset`.

Things that bite:
- `connectedAndroidTest` leaves the app and its library installed, because `app/gradle.properties` sets `android.injected.androidTest.leaveApksInstalledAfterRun=true` (AGP uninstalls by default). The test helper `app.kisscut.keys.test` stays installed too.
- Force-stopping the app makes Android fall back to the previous keyboard.
- The emulator is slow on its first cold start: thumbnails and the keyboard can take a few seconds. Tap the field again if the keyboard does not appear.
- Which apps accept stickers directly is checked on a real phone (KEYBOARD.md, Known unknowns).

## 8b. Installing on your own phone

1. On the phone: Settings, About phone, tap Build number 7 times. Then in Developer options turn on USB debugging. (Xiaomi: also turn on "Install via USB".)
2. Plug it in with a data cable and tap Allow on the "Allow USB debugging?" prompt. `adb devices` must say `device`, not `unauthorized`.
3. Build and install only on the phone (replace the serial with yours from `adb devices`):

```powershell
cd app
.\gradlew.bat assembleDebug
adb -s <phone-serial> install -r ui/android/build/outputs/apk/debug/android-debug.apk
```

`install -r` keeps the phone's library and settings. Do not run `connectedAndroidTest` with the phone plugged in unless `ANDROID_SERIAL` points at the emulator: the library survives, but the tests install a test helper app and switch the phone's keyboard. Uninstalling the app (`adb uninstall`, or from the phone) always wipes the library.

---

## 9. Optional: emulator MCP for Claude Code

Gradle plus plain adb already covers most testing. Add an MCP only if screenshots and UI-tree calls through the shell become awkward.

Candidates (community projects, unaudited): `adb-mcp` (iksnerd/adb-mcp), `AgenTest`, `Android-MCP` (CursorTouch). Before installing one:
1. Read its README for the exact install and `claude mcp add` command. Do not guess flags.
2. Skim the source or tool list. An MCP that can run arbitrary shell is as powerful as `adb shell`.
3. Prefer servers that talk only to `adb` locally and use stdio.

Register it with `claude mcp add` per its README, then run `/mcp` to confirm it connects.

---

## 10. Helper scripts to create

Create these in `scripts/` so later tasks are one command each:

- `scripts/emu-start.ps1`: boots `stickertest` (step 5), waits with timeout, exits non-zero on failure.
- `scripts/emu-stop.ps1`: `adb emu kill`.
- `scripts/test-all.ps1`: runs `gradlew test`; if a device is present, also `connectedAndroidTest`; prints a pass/fail summary and the report paths.
- `scripts/screenshot.ps1 <name>`: saves `artifacts/<name>.png` using the `cmd /c` form from step 7.

Keep artifacts (screenshots, UI dumps) in an `artifacts/` folder that is git-ignored.

---

## 11. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `sdkmanager` says it can't find the SDK root or "Could not determine SDK root" | `cmdline-tools` not under `latest`, or `ANDROID_HOME` wrong |
| Emulator errors about HAXM, WHPX or hypervisor | Virtualization off or Hyper-V/WHPX not enabled (step 0) |
| Emulator stays offline or never boots | Run once with a window to see errors; delete `%USERPROFILE%\.android\avd\stickertest.avd\*.lock`; retry with `-wipe-data` |
| `adb devices` shows `unauthorized` or empty | `adb kill-server; adb start-server`; restart emulator |
| Gradle: "Unsupported class file major version" | JDK too new or too old for the Gradle/AGP in use; switch `JAVA_HOME` to 17 |
| `INSTALL_FAILED_*` | `adb uninstall <package.id>` then `installDebug` again (wipes that device's library) |
| Tests hang | Emulator not fully booted; recheck `sys.boot_completed` |

---

## 12. Done criteria

Report back with:
1. Output of `java -version`, `adb version`, `emulator -version`.
2. `adb devices` showing the booted emulator.
3. `assembleDebug` result and APK path.
4. One screenshot of the app's first screen.
5. Anything skipped, with the reason.
