# ROADMAP.md

Work top to bottom. Tick boxes as you finish. Do not start a milestone until the previous one's done criteria pass. Every milestone ends with: `gradlew test` green and notes added below it.

Open decisions block nothing unless stated; use the defaults in `docs/ARCHITECTURE.md` and ask the user when a milestone needs one.

## M0: Project skeleton
- [x] Gradle project with modules `shared:db`, `shared:core`, `ui:android`; version catalog; Gradle wrapper
- [x] Empty app launches to a blank Compose screen on the emulator
- [x] Theme.kt with dark and light tokens and bundled fonts (UI_SPEC)
- [x] `scripts/` helpers from SETUP.md section 10
- [x] `.gitignore` (build, `local.properties`, `artifacts/`, `.gradle`)

Done when: `assembleDebug` and `installDebug` succeed and a screenshot of the themed blank screen exists in both modes.

## M1: Data layer
- [x] Entities, DAOs, database with `BundledSQLiteDriver` (DATA_MODEL)
- [x] `CollectionRepository` with depth cap, cycle check, both delete modes
- [x] `StickerRepository` with dedupe by hash
- [x] `FileStore` interface and Android implementation
- [x] Schema export committed; first migration test harness
- [x] Tests for everything under "Database and repositories" in TESTING

Done when: all those tests pass.

## M2: Import a sticker (core path)
- [x] Type sniffing, thumbnail generation, `hasAlpha`, `isAnimated`
- [x] Share-sheet intent (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `image/*`) opens the Save sheet
- [x] Save sheet with nested collection picker and "New collection"
- [x] In-app "Add stickers" using the system photo picker / SAF

Done when: sharing an image from the emulator's browser or gallery saves it; duplicate share is a no-op; test coverage per TESTING.

## M3: Library and collections UI
- [x] Library screen (chips, grid, hero tile, floating nav) with true sticker look (cut line for transparent, tile for opaque, GIF badge)
- [x] Collections list (nested, counts) and Inside-a-collection screen with breadcrumb
- [x] Select mode: move, favorite, delete
- [x] Empty states and accessibility pass
- [x] Dark and light verified against the artifact with screenshots

Done when: UI tests pass and screenshots match the designs; differences listed.

## M4: Keyboard
- [x] `StickerImeService`, panel view, tabs, grid, globe key (KEYBOARD)
- [x] Insert via commit-content with clipboard fallback and hints
- [x] `markUsed` on success; Recents works
- [x] Keyboard setup onboarding screen (enable, select, state tracking)
- [x] `ImeTestActivity` and keyboard tests

Done when: on the emulator a sticker is inserted into `ImeTestActivity` via the keyboard and tests pass.

## M4.2: Typing keyboard
Added after M4 at the user's request: the keyboard must also type (KEYBOARD, Typing layout).
- [x] Letters, `?123` and `=\<` pages with Shift, caps lock, auto-capitals, Backspace repeat, Enter action
- [x] Sticker key and ABC key swap between letters and the sticker panel; last view remembered
- [x] Field rules: number and phone fields open on numbers, password fields on letters with stickers off
- [x] Setup screen privacy text updated (types, never stores what is typed)
- [x] Tests: layout and shift logic on the JVM; typing into `ImeTestActivity` fields on the device

Done when: on the emulator, typing "Hi there" plus a sticker into `ImeTestActivity` works, Enter sends in a field with a Send action, and tests pass. The user tries it in Discord on their phone.

## M4.5: Smaller storage and file types
First, check the send rule (send the stored type if the field accepts it, else a PNG copy, else the clipboard):
- [x] Debug-only log in the keyboard of the MIME types each field accepts (`src/debug`, nothing in release)
- [x] The user taps the text box in WhatsApp, Discord, Messenger, Google Messages, Telegram on their phone; record each list in KEYBOARD's app table and check the rule fits before changing the stored format

Still images:
- [x] Accept JPEG; still images stored at most 512px as lossless WebP (PNG on API 26-28), never bigger than the input (IMPORT_EXPORT, Stored format)
- [x] Hash the bytes as received so duplicates still match; existing stickers untouched
- [x] Keyboard clipboard fallback copies still stickers as a PNG copy (same pixels, `cache/send/`), like the direct send; GIFs stay GIF (KEYBOARD, Inserting a sticker)

Large GIFs (output stays GIF so every chat app accepts it; near-lossless, as GIF allows 256 colors per frame; no new library):
- [ ] Pure Kotlin GIF reader in `shared:core` commonMain (frames, disposal, transparency, interlace, local palettes), with safety limits for broken or hostile files
- [ ] Shrink GIFs over 512px on the longest side: composite each frame, scale, re-pick 256 colors per frame without flicker, keep 1-bit transparency clean
- [ ] GIF writer: real LZW, store only the changed area per frame, keep delays and loop count
- [ ] Wire into import: only GIFs over 512px, never bigger than the input, one frame at a time in memory, progress for long GIFs
- [ ] Before reusing any existing code (for example AnimatedGifEncoder / NeuQuant), check its license with the user

WhatsApp stickers (last, after the still and GIF parts; the user asked for it):
- [ ] Probe first on the user's phone: send one 512x512 WebP as `image/webp.wasticker`; does WhatsApp show it as a sticker? If not, drop this and keep the PNG copy
- [ ] If yes: when a field lists `image/webp.wasticker`, send a still sticker as a 512x512 WebP copy (transparent padding to square, at most 100 KB, the stored file unchanged); animated WebP only if it already fits (512x512, at most 500 KB)

Tests per TESTING (Import / export, Stored format), plus GIF cases: odd disposal and palettes, malformed files, timing and loop preserved, "never bigger", a set of real-world GIFs. GIFs at 512px or smaller and animated WebP are stored unchanged.

Done when: a 3000px PNG saves as a 512px lossless WebP with identical pixels to a 512px scale of the source; a JPEG saves; a 1000px GIF saves at 512px as a GIF that plays with the same timing and looks the same on the emulator; small GIFs and animated WebP are unchanged; tests pass.

## M5: Backup and sharing
- [ ] `.stickerpack` export (all or one collection) with progress and cancel
- [ ] Import with all safety limits and rollback (IMPORT_EXPORT)
- [ ] Open `.stickerpack` from other apps
- [ ] Settings screen: theme, animation, export, import, keyboard help
- [ ] Import/export tests including malicious fixtures

Done when: round-trip test and all rejection tests pass.

## M6: Polish and release prep
- [ ] Performance pass: scrolling with 2000 stickers, animated items only when visible, memory check
- [ ] Release build config: minify, shrink, signing config read from user-provided properties (never commit secrets)
- [ ] App icon, name, Play listing assets (ask the user for name and direction)
- [ ] Privacy statement text for the listing (draft for the user to review)
- [ ] Entitlements interface and $1 unlock plumbing, behind a flag (ask the user for free-tier limits first)
- [ ] Manual test checklist for real chat apps (for the user)

Done when: signed release bundle builds and the manual checklist exists.

## Later (not planned yet)
- On-device auto-suggestions for collections (ML)
- Search
- iOS port (SwiftUI or Compose Multiplatform; keyboard cannot insert images directly there, clipboard only)
- Direct sharing without a file (needs a network decision)

## Notes log

Add one line per surprise or decision: date, milestone, what happened.

- 2026-11-21, M0: Versions: Gradle 9.7.0, AGP 9.5.2, Kotlin 2.4.21, Compose BOM 2026.11.01. Kotlin's official table only covers AGP up to 9.3.1; 9.5.2 built with no warnings.
- 2026-11-21, M0: Compose 1.13 requires compileSdk 37.1 (minor SDK), set via `compileSdk { version = release(37) { minorApiLevel = 1 } }`. Shared modules stay on 37.
- 2026-11-21, M0: minSdk 26 (variable fonts need `FontVariation`), targetSdk 36. Fonts are the Google Fonts variable TTFs; OFL texts in `ui/android/src/main/assets/licenses/`.
- 2026-11-21, M0: KMP modules have no `test` task, so each registers one that runs `allTests`; `gradlew test` now covers all three modules.
- 2026-11-21, M0: The spec's "Ink" is two colors (dark background #0F0E15, light text #14131C); Theme.kt keeps them as separate tokens.
- 2026-11-21, M0: APK path is `ui/android/build/outputs/apk/debug/android-debug.apk` (not `app/...` as SETUP.md assumes).
- 2026-11-21, M1: Added a `jvm()` target to `shared:db` and `shared:core` (reverses M0's Android-only choice). DAO, repository and migration tests run on the host with Room in-memory + BundledSQLiteDriver; it also makes "no Android imports in commonMain" compiler-enforced.
- 2026-11-21, M1: Versions: Room 2.8.5, KSP 2.3.14, sqlite-bundled 2.6.2 (matches Room's sqlite), coroutines 1.11.0, Turbine 1.2.1, room-testing, AndroidX Test runner 1.7.0 / ext-junit 1.3.0.
- 2026-11-21, M1: Ordering decided: All, Unsorted, Favorites and in-collection lists are newest first (addedAt, then id). Recents exclude never-used stickers. Re-parented children go to the end of the new parent's children.
- 2026-11-21, M1: `parentId` has a real self FK with CASCADE as a safety net; the repository re-parents children / deletes stickers before deleting rows (tests cover the order).
- 2026-11-21, M1: File delete failures are returned as `FileDeleteReport.failed`, not logged yet; callers log them. The startup orphan-file sweep (DATA_MODEL) is not built yet. DataStore settings are not in M1.
- 2026-11-21, M1: `AndroidFileStore` only accepts plain file names `[A-Za-z0-9._-]{1,128}`; import (M2) must name files by hash.
- 2026-11-21, M1: `DataLayerSmokeTest` (ui:android androidTest) checks the real Android database builder, bundled SQLite and file store on the emulator.
- 2026-11-21, M2: Versions: lifecycle-viewmodel-compose 2.11.1, Coil 3.6.3 (coil-compose only), material-icons-extended and ui-test-junit4 from the Compose BOM. The user approved the two not named in ARCHITECTURE (ViewModel, icons).
- 2026-11-21, M2: `hasAlpha` comes from the decoded thumbnail pixels, not the header: many opaque screenshots are RGBA PNGs. The header only says "may have alpha".
- 2026-11-21, M2: Thumbnails are plain 384px WebP. The die-cut outline is not baked in yet (M3); thumbnails saved before M3 will need regenerating.
- 2026-11-21, M2: Animated PNG (APNG) is stored as a still PNG (`isAnimated = false`); Android decoders only show its first frame. Max image side is 16384px, rejected as too large above that.
- 2026-11-21, M2: A duplicate import is a pure no-op (no decode, no writes, no move), even when the user picked a different collection. DATA_MODEL allows "optionally moving"; not offered in the UI.
- 2026-11-21, M2: MainActivity is the share target (single activity). Done after a share calls `finish()` and returns to the sharing app. Only `content:` URIs are read.
- 2026-11-21, M2: Save button height follows screen size (user's call): 48dp when the screen is under 700dp tall (small phones, landscape), 56dp on phones (the design), 64dp on tablets (600dp+ wide). The button is pinned below the scrolling content so it is never cut off. Rows are 48dp (touch minimum).
- 2026-11-21, M2: "New collection" creates inside the selected collection, or beside it when the selected one is at depth 3.
- 2026-11-21, M2: Checked on the emulator: shared from Google Photos ("Save to library" in the share sheet), saved into a nested collection, then shared again with "1 was already in your library" and still one file. The photo picker path works too. Chrome was not tried.
- 2026-11-21, M2: Gotcha: `image/*` inside a KDoc comment opens a nested Kotlin comment and breaks the build.
- 2026-11-21, M2: Save sheet checked against its board (dark and light, nested picker, GIF): matches. Fixed: the GIF badge used the page colors, so it was light in light mode (board: dark in both). Kept on purpose: rows are 48dp (board 44px, our 48dp rule), Unsorted has an inbox icon, "New collection in" names the selected collection (see above), preview tile uses the neutral raised fill (board: a violet tint).
- 2026-11-21, M3: Design vs UI_SPEC conflicts settled with the user: the design's Library layout (round filter rail plus All/Static/Animated chips), with Recents added to the rail; hero = most used, else newest ("NEWEST" label until M4 counts uses); long-press on a collection opens rename/move/delete (drag-to-reorder later, hint text changed); 4-item nav with Add, Settings a placeholder until M5.
- 2026-11-21, M3: Cut line is baked into the thumbnail at import (radius 12px of 384, white, anti-aliased) only for still images with transparent pixels; `hasAlpha` is read before padding. The shadow is drawn in Compose (same image tinted black, offset down) because its strength differs per theme. M2-era thumbnails had no outline: cleared emulator app data, no migration (nothing shipped).
- 2026-11-21, M3: Versions: Navigation Compose 2.10.2 (approved list). No schema change; new queries only (all collections with counts, newest 3 stickers per collection, stickers in a subtree).
- 2026-11-21, M3: LazyVerticalGrid has no row span, so the 2x2 hero and the tiles beside it are one full-width block. Columns are 3 on phones, more on wide screens (100dp minimum cell).
- 2026-11-21, M3: Bugs caught on the emulator: tapping the active Collections tab inside a collection reopened that collection (now returns to the list); at font scale 2.0 the nav pushed Settings off-screen (active label now ellipsizes). Both have tests.
- 2026-11-21, M3: UI tests use an in-memory database and temp folder (TestLibrary), so they never touch the emulator's real library. Debug-only SeedData/SeedReceiver fill the real library for screenshots (`adb shell am broadcast -n com.example.stickers/.debug.SeedReceiver --ez clear true`).
- 2026-11-21, M3: Differences from the designs (screenshots in artifacts/): collection previews are real stickers on tinted rounded squares (design draws round faces); subcollection cards are 150-190dp wide (design 150); Recents in the rail; rail and nav labels ellipsize at font 2.0; GIFs show their first frame only (playback while visible is M6); a sticker tile reacts only to long-press outside select mode (no sticker viewer in v1).
- 2026-11-21, M3: The emulator process exited once mid-run of connectedAndroidTest (reported as "status -1"); a restart and rerun passed 38/38.
- 2026-11-21, M3: Trying official game stickers (Pokémon GIPHY, Atelier Ryza 3 icons) found 3 layout bugs the generated seed missed: long titles under the art, hero label covering the GIF badge, and the rail button's rounded clip shaving long labels. Fixed with tests. JPEG is rejected by spec (PNG, WebP, GIF only).
- 2026-11-21, M4: Panel follows the Keyboard board, not the old UI_SPEC line (user's call): globe, All/Static/GIF and Backspace at the bottom, a subcollection chip row, 5 columns. Unsorted tab added (user's call) so unsorted stickers stay reachable; the panel opens on Recents if used, else the first non-empty tab.
- 2026-11-21, M4: Versions: RecyclerView 1.4.0, core-ktx 1.19.0, coil (views) 3.6.3, DataStore preferences 1.1.7 (all approved). DataStore holds only "setup dismissed" so far.
- 2026-11-21, M4: Still stickers whose type the field rejects go as a lossless PNG copy in `cache/send/` (cleared on the next send); stills over 4096x4096 px skip conversion and go to the clipboard. Animated ones are never converted.
- 2026-11-21, M4: Keyboard tests swap `App.container` for an in-memory library under `files/ime-test/` (debug-only FileProvider path). The emulator hands tests a redacted text clip, so the clipboard test checks what the inserter copied, not the clipboard itself.
- 2026-11-21, M4: Gotchas: setting a View background replaces its padding with the drawable's insets; `connectedAndroidTest` uninstalls the app, which wipes the emulator library and turns the keyboard off; switching keyboards per test raced with Gboard, so the IME is selected once per class.
- 2026-11-21, M4: Setup shows at launch until Skip or Done (not after a share); the steps update live from system settings (the picker is a dialog, so resume alone misses it). Also reachable from a keyboard card in the Settings placeholder.
- 2026-11-21, M4: Differences from the boards (screenshots in artifacts/): touch areas are 48dp around the 40dp tabs, 32dp chips and 38dp segments; GIFs show their first frame (playback is M6); thumbnails take 2-3s to appear on the first cold open on the emulator; in dark mode a done step and the current step are both lime (check vs number).
- 2026-11-21, M4.2: Typing added at the user's request after trying M4 (they expected a full keyboard; the spec said stickers only). Basic QWERTY, no autocorrect or suggestions. The sticker panel's globe became an ABC key (hold it for the keyboard list); the globe lives on the letters.
- 2026-11-21, M4.2: Opens on the view used last (kept in memory while the keyboard runs, not saved). Enter sends `\n` with commitText for new lines, not a KEYCODE_ENTER event, because some chat apps treat that key as Send.
- 2026-11-21, M4.2: Gotchas: in strings.xml `?123` must be written `\?123` (a leading `?` is a theme reference) and the backslash in `=\<` doubled; a MATCH_PARENT child of a wrap-content FrameLayout in an IME window takes the whole screen, so the frame is measured to the sticker panel's height. With a phone plugged in, `installDebug` and `connectedAndroidTest` hit both devices: set `ANDROID_SERIAL=emulator-5554` for tests.
- 2026-11-21, M4.2: Not built: key preview popups, long-press for accents or digits, other layouts and languages. No board exists for the letters; screenshots in artifacts/ (typing-dark, typing-light).
- 2026-11-22, M4.5: Logged the accepted types in 7 apps on the user's phone (KEYBOARD, Real-app checks). The send rule fits: Discord, Telegram, Viber, FB Lite and Google Messages take WebP; WhatsApp and Instagram don't, so they get the PNG copy. WhatsApp lists `image/webp.wasticker`, its own sticker type; not used yet. The release build has a no-op `FieldTypesLog` in `src/release`.
- 2026-11-22, M4.5: Still-image code written (JPEG sniffing, `AndroidStillConverter`, clipboard PNG copy); JVM tests pass, device tests written but not run yet at the user's request, so those boxes stay open. Additions not in the plan: EXIF orientation is applied (BitmapFactory ignores it) and a rotated image is always converted; a one-frame GIF is never turned into WebP; the clipboard copy is named `clip-<hash>.png` so the next send doesn't delete it.
- 2026-11-22, M4.5: Device tests 60/60 on the emulator. "Identical pixels" holds for opaque and fully transparent pixels; semi-transparent edge pixels can differ by about 255/alpha per channel, because Android bitmaps are premultiplied and the WebP and PNG encoders round differently. The test allows exactly that. `RepickTest` failed once in a full run: after the keyboard tests turn our keyboard on, the setup screen behind the Save sheet also shows "Done", so clicking by text found two buttons. Fixed by tagging the Save sheet's Done button (`SaveTags.DONE_BUTTON`); passes with the keyboard on.
