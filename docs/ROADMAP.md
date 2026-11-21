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
- [ ] Type sniffing, thumbnail generation, `hasAlpha`, `isAnimated`
- [ ] Share-sheet intent (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `image/*`) opens the Save sheet
- [ ] Save sheet with nested collection picker and "New collection"
- [ ] In-app "Add stickers" using the system photo picker / SAF

Done when: sharing an image from the emulator's browser or gallery saves it; duplicate share is a no-op; test coverage per TESTING.

## M3: Library and collections UI
- [ ] Library screen (chips, grid, hero tile, floating nav) with true sticker look (cut line for transparent, tile for opaque, GIF badge)
- [ ] Collections list (nested, counts) and Inside-a-collection screen with breadcrumb
- [ ] Select mode: move, favorite, delete
- [ ] Empty states and accessibility pass
- [ ] Dark and light verified against the artifact with screenshots

Done when: UI tests pass and screenshots match the designs; differences listed.

## M4: Keyboard
- [ ] `StickerImeService`, panel view, tabs, grid, globe key (KEYBOARD)
- [ ] Insert via commit-content with clipboard fallback and hints
- [ ] `markUsed` on success; Recents works
- [ ] Keyboard setup onboarding screen (enable, select, state tracking)
- [ ] `ImeTestActivity` and keyboard tests

Done when: on the emulator a sticker is inserted into `ImeTestActivity` via the keyboard and tests pass.

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
