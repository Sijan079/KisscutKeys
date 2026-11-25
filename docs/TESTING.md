# TESTING.md

Goal: Claude Code can verify its own work without a human. Cheapest checks first.

## Layers

| Layer | Where | Runs with | What it covers |
|---|---|---|---|
| Unit (JVM) | `app/shared/*/src/commonTest`, `app/ui/android/src/test` | `gradlew test` | repositories with fakes, nesting rules, import/export logic, ViewModels |
| Database | `app/shared/db/src/androidInstrumentedTest` (or JVM with Room KMP in-memory where supported) | `gradlew test` or `connectedAndroidTest` | DAOs, recursive CTE, FK behavior, migrations |
| Instrumented UI | `app/ui/android/src/androidTest` | `gradlew connectedAndroidTest` | Compose screens, navigation, share-sheet intent |
| Keyboard | same, with `ImeTestActivity` | `connectedAndroidTest` + adb | panel shows, insertion commits, fallback |
| Visual check | emulator + screenshot | adb | compare against the artifact designs |

## Order of work

1. Write or update unit tests first for logic changes.
2. Run `.\gradlew.bat test` from `app/`. Fix before moving on.
3. For UI or keyboard changes, boot the emulator, run `installDebug`, then the instrumented tests, then take screenshots.

## What must be tested (minimum)

### Database and repositories
- Insert duplicate hash returns existing sticker, no second row.
- Delete collection "only": children re-parented, stickers moved up, nothing lost.
- Delete collection "everything": descendants and stickers gone, files removed.
- Depth cap 3 enforced on create and move; cycle rejected.
- `totalStickerCount` counts descendants.
- Unsorted / favorites / recents queries and ordering.
- Migration tests for every schema version bump.

### Import / export
- Packs (`.stickerpack`): JVM tests in `shared/core` `pack` (hostile zips, manifest rules, collection planning, round trip, rollback on failure and cancel, limits); device tests in `AndroidPackBackendTest` (real zip, converters and content uris) and `SettingsUiTest` (screen and dialogs).
- Stored format: a large still image is stored at 512px on the longest side as lossless WebP (PNG on API 26-28), and its decoded pixels equal the source scaled to 512 (lossless check, not "looks close"; semi-transparent pixels may differ by the premultiplied-alpha rounding, about 255/alpha per channel).
- Small images are never scaled up; a small JPEG or lossy WebP whose original is smaller is kept as-is.
- JPEG is accepted (opaque tile, no cut line); a JPEG with an EXIF rotation is stored upright; animated WebP and GIFs at 512px or smaller are stored unchanged.
- GIFs over 512px are stored as a 512px GIF with the same frames, delays and loop count; replaying it matches the scaled source; kept as received when shrinking would not make it smaller or the GIF is broken or over the limits; progress is reported per frame and cancelling stores nothing. The `gif` package is tested against hand-built GIFs (LZW edge cases, disposal, interlace, damaged and hostile files) and against ImageIO in both directions.
- Word list: `WordListTest` (parsing, case, malformed lines) and `BundledWordListTest` (the shipped asset: 50,000 words, most common first, fix pairs); `SpellerTest` (costs, ranking, every "never" case, two lists at once) and `BundledSpellerTest` (everyday typos fixed and real words left alone on the shipped list); `ComposerTest` and `CursorTrackerTest` (the word being typed, autocorrect and undo, cursor moves told apart from our edits), `InputFieldsTest` (which fields get suggestions); `KeyboardTest` on a phone covers autocorrect on space, Backspace undo, off by default and in passwords, no undo after the cursor moved, and no doubled word when the app drops the underline; `SuggestionStripTest` (slot arrangement); `KeyboardTest` on a phone also covers the strip (tap the middle word, keep the typed word); `SpellerTimingTest` on a phone (one keystroke under a frame at the 95th percentile); `LicensesViewModelTest` checks every listed license file ships, `LicensesUiTest` opens one from Settings.
- Saving the same file twice is a no-op (hash of received bytes); restoring an exported pack into the same library adds no duplicates.
See `docs/IMPORT_EXPORT.md` Tests. Use small fixture files (a few KB) in `src/test/resources`; generate zips in test code rather than committing binaries where possible.

### UI
- Library shows stickers, filter chips filter, select mode counts selection.
- Save sheet: nested picker selects the right collection, "New collection" works, multiple images save.
- Theme: render key screens in dark and light.
- Font scale 2.0: no clipped text on Library and Save.

### Keyboard
- IME enabled and selected via adb; `ImeTestActivity` field focused; panel visible.
- Tap a sticker; field receives the content (listener fires with the right MIME type) and the sticker lands in Recents.
- PNG-only field gets a PNG copy of a WebP sticker; an animated sticker it rejects is copied with the hint.
- Field that does not accept images triggers the clipboard fallback and hint.
- Password field opens on letters with the sticker key off.
- Switching tabs and chips shows the right stickers.
- Typing: Shift, letters, space and Backspace produce the right text; Enter runs a Send action; the sticker and ABC keys swap views.

How the keyboard tests run (`KeyboardTest`):
- They swap `App.container` for an in-memory database and `files/ime-test/`, so the real library is never touched, and select the IME once per class (switching per test races with the old keyboard).
- The emulator gives tests a blanked-out text clip, so the clipboard test checks what the inserter copied (`lastCopied`), not the system clipboard. Real clipboard behavior is checked by hand on a phone.
- With a phone plugged in, Gradle runs device tests on every device. The app and its library stay installed afterwards (`app/gradle.properties`, `leaveApksInstalledAfterRun`). Set `$env:ANDROID_SERIAL = 'emulator-5554'` first.

## Test data

- A debug-only `SeedData` builder (in `androidTest` and debug source set, never release) that creates N collections (nested) and M stickers from generated bitmaps: transparent PNG, opaque PNG, static WebP, GIF (generated frames). Do not download images.
- Keep fixtures tiny so tests stay fast.

## Driving the emulator

Commands are in `SETUP.md`. For UI inspection prefer `uiautomator dump` over screenshots; use screenshots for visual judgement. Save screenshots under `artifacts/` named `<screen>-<dark|light>.png`.

Useful toggles:

```powershell
adb shell cmd uimode night yes     # dark
adb shell cmd uimode night no      # light
adb shell settings put system font_scale 2.0
adb shell settings put system font_scale 1.0
adb shell settings put global animator_duration_scale 0   # speed up UI tests (reset to 1)
```

## Reporting

When a task finishes, report: tests added, command run, pass/fail counts, screenshots taken (paths), and anything not verified (for example, real chat apps).

## Not testable on the emulator

Real chat app behavior (WhatsApp, Telegram) and Play Store billing. List these as manual checks for the user in the milestone notes.
