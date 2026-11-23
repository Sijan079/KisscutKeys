# Kisscut Keys

A sticker manager and keyboard for Android. Save stickers from any app, sort them into collections, and send them
from a keyboard panel without leaving the chat.

Everything stays on the device: no internet permission, no accounts, no analytics.

## Features

- **Save** stickers from the share sheet or an in-app picker (PNG, JPEG, WebP, GIF, animated WebP)
- **Library** with favorites, recents and "Unsorted"
- **Collections**, nestable up to 3 levels, with bulk move, favorite and delete
- **Keyboard** with basic typing (QWERTY, numbers, symbols), key popups with accents, a space bar that slides the cursor, an Emoji tab and a sticker panel; it never stores or sends what you type
- **Keyboard settings**: key sound, vibration, number row and height
- **Insert** stickers into chat apps, with a clipboard fallback when an app does not accept images
- **Backup and share** as `.stickerpack` files

## Status

In development. Milestones and progress are in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Tech

- Kotlin Multiplatform shared logic (`app/shared/`), Room database shared by the app and the keyboard
- Native Android UI in Jetpack Compose (`app/ui/android`)
- Native keyboard built on `InputMethodService`

## Build

Needs a JDK and the Android SDK command-line tools. The Gradle project lives in `app/`. Full setup, emulator and adb notes: [`SETUP.md`](SETUP.md).

```powershell
cd app
.\gradlew.bat test            # fast JVM tests
.\gradlew.bat assembleDebug   # build the debug APK
.\gradlew.bat installDebug    # install on a booted emulator or phone
```

## Docs

| Doc | About |
|---|---|
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | Scope, v1 features, non-goals |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Modules, layering, DI |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | Room schema and migrations |
| [`docs/UI_SPEC.md`](docs/UI_SPEC.md) | Screens and design tokens |
| [`docs/KEYBOARD.md`](docs/KEYBOARD.md) | The keyboard and sticker insertion |
| [`docs/IMPORT_EXPORT.md`](docs/IMPORT_EXPORT.md) | `.stickerpack` format and security rules |
| [`docs/TESTING.md`](docs/TESTING.md) | Test strategy |
| [`docs/BRAND.md`](docs/BRAND.md) | Name, package ID, icon |
