# CLAUDE.md

Kisscut Keys: sticker manager + custom keyboard for Android (Google Play). Kotlin Multiplatform shared logic, native Android UI, native keyboard. iOS comes later and is out of scope now.

Read this file first, then the doc for the area you are touching.

## Docs index

| Doc | Read when |
|---|---|
| `SETUP.md` | Environment, emulator, adb, Gradle commands |
| `docs/PRODUCT.md` | Scope, v1 features, non-goals, pricing |
| `docs/ARCHITECTURE.md` | Modules, layering, DI, platform split |
| `docs/DATA_MODEL.md` | Room schema, DAOs, nesting, migrations |
| `docs/UI_SPEC.md` | Screens, design tokens, Compose constraints |
| `docs/KEYBOARD.md` | The `InputMethodService`, typing layout and sticker insertion |
| `docs/IMPORT_EXPORT.md` | `.stickerpack` format, security rules |
| `docs/TESTING.md` | What to test and how, per layer |
| `docs/ROADMAP.md` | Ordered milestones and the current task |
| `docs/BRAND.md` | App name, package ID, launcher icon spec |

Design source of truth: the "Sticker app: UI designs" artifact. Kisscut (dark and light) is the target look. Skeletal is the plain reference. If a doc and a design disagree, ask the user.

## Ground rules

1. Work on one milestone from `docs/ROADMAP.md` at a time. Do not start the next until the current one's done criteria pass.
2. Shared logic goes in `app/shared/`. No Android imports in `commonMain`.
3. The keyboard and the app share one Room database. Never open a second database file.
4. Every behavior change gets a test (see `docs/TESTING.md`). Run `.\gradlew.bat test` (from `app/`) before saying a task is done.
5. No internet permission in v1. Do not add network libraries. If a feature seems to need the network, stop and ask.
6. No analytics, ads or accounts. Stickers never leave the device unless the user shares a file.
7. Keep dependencies few. Ask before adding a library not named in `docs/ARCHITECTURE.md`.
8. Do not delete or rewrite user data code (migrations, import) without a test that covers old data.
9. Small commits, one concern each. Do not commit `local.properties`, keystores, `artifacts/`, or build output.
10. If a requirement is unclear or two docs conflict, ask instead of guessing.
11. Keep the tree clean, every task: code goes in its feature package (ARCHITECTURE, Packages), debug-only code in `src/debug`, no stray or scratch files in the repo, no unused code, imports, strings or drawables left behind, and docs updated in the same task as the change. Screenshots in `artifacts/` use `<screen>-<dark|light>.png`; delete throwaway ones.

## Commands

Windows PowerShell. Details in `SETUP.md`. The Gradle project lives in `app/`; run Gradle from there.

```powershell
.\scripts\emu-start.ps1             # boot emulator `stickertest` (from the repo root)
.\scripts\emu-stop.ps1
cd app
.\gradlew.bat test                  # fast JVM tests, run first
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug          # needs a booted emulator
.\gradlew.bat connectedAndroidTest  # needs a booted emulator
```

With a phone plugged in, Gradle installs and tests on every device. Device tests leave the app and its library installed (`app/gradle.properties`), but still switch keyboards. Before tests run `$env:ANDROID_SERIAL = 'emulator-5554'`.

## Conventions

- Kotlin official style. Max line length 120.
- Naming: `StickerCollection` (not `Collection`, it clashes with kotlin.collections).
- Coroutines and Flow for async. Repositories expose `Flow`, never LiveData.
- UI state: one immutable `UiState` per screen, one `ViewModel` per screen.
- Strings in resources, never hard-coded. No emoji in UI strings.
- Accessibility: every tappable element has a content description and a 48dp minimum target.

## Definition of done (every task)

- Builds, `test` passes, new behavior has a test.
- UI changes match `docs/UI_SPEC.md`, checked on the emulator with a screenshot saved to `artifacts/`.
- ROADMAP checkbox ticked and one line added to the milestone's notes if something surprising came up.
