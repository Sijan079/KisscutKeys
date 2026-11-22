# ARCHITECTURE.md

## Principles

- Kotlin Multiplatform for logic. Native UI per platform. The keyboard is always native.
- Android only for now, but `shared/` must compile without Android types so iOS can be added later.
- Keep it boring: few modules, few libraries, no framework magic.

## Modules

```
/
  shared/
    db/        Room database, entities, DAOs        (commonMain + androidMain)
    core/      repositories, import/export, models   (commonMain + androidMain)
  ui/
    android/   Compose app + keyboard package        (Android application)
  scripts/
  docs/
```

- `ui/android` depends on `shared:core` and `shared:db`.
- `shared:core` depends on `shared:db`.
- `shared:core` source sets: `commonMain` (pure Kotlin, no `java.*`), `androidMain`, `jvmMain` (host tests), and `jvmSharedMain` for code both Android and the JVM can run but common cannot, such as `java.util.zip` (`.stickerpack` reading and writing). `.stickerpack` code lives in the `pack` package.
- The keyboard lives in `ui/android` under a `keyboard` package, **not** a separate module. It uses the same repositories and database as the app.

Packages in `ui/android` (`app.kisscut.keys`): `library`, `collections`, `save` and `share` (Save sheet and share intents), `settings`, `setup` (keyboard setup and the DataStore settings store), `keyboard` (IME), `nav`, `common` (shared parts), `ui/theme`. Debug-only code (seed data, `ImeTestActivity`) lives in `src/debug`. One feature per package; a new screen gets its own package.
- `ui/ios` does not exist yet.

## expect/actual and interfaces

Use `expect/actual` only for tiny platform facts (file paths, database builder, MIME sniffing). For anything bigger, define an interface in `commonMain` and inject the Android implementation. Reason: interfaces are easier to fake in tests.

Platform pieces needed on Android:
- Database builder (`Room.databaseBuilder` with `BundledSQLiteDriver`).
- File storage paths (app-private `files/stickers/` and `files/thumbs/`).
- Thumbnail generation (Bitmap or ImageDecoder).
- Image type sniffing (read magic bytes; do not trust file extensions).
- Zip reading and writing (java.util.zip on Android).

## Layers

```
UI (Compose screens, keyboard Views)
  -> ViewModel (screen state, Flow collection)
    -> Repository (shared:core) : one per aggregate
      -> DAO (shared:db)
      -> FileStore (interface; Android impl)
```

- Repositories are the only thing UI code calls. UI never touches DAOs or files directly.
- Enforce rules (nesting depth, dedupe, delete semantics) in repositories, not in UI.

## Dependency injection

Start with manual DI: a small `AppContainer` built in `Application.onCreate`, also reachable from the keyboard service via `(applicationContext as App).container`. Add Koin only if the manual version becomes painful. Ask first.

## Libraries (approved list)

| Purpose | Library |
|---|---|
| Database | Room (KMP) with KSP and `BundledSQLiteDriver` |
| Settings | DataStore (preferences) |
| UI | Jetpack Compose (Material 3), Navigation Compose |
| Images in Compose | Coil 3, with GIF and animated WebP decoders (`coil` for the keyboard's ImageViews) |
| Keyboard | AndroidX Core (commit content, FileProvider), RecyclerView |
| Async | kotlinx.coroutines |
| Serialization | kotlinx.serialization (manifest JSON) |
| Tests | kotlin.test, JUnit4, Turbine (Flow), AndroidX Test, Compose UI test |

Anything else: ask first.

## Compose Multiplatform constraints (design rules)

The UI is Android-only for now, but designs were made so a Compose Multiplatform port stays possible. So:
- Standard Material 3 parts only. One font family pair (see UI_SPEC).
- No blur, shaders or platform-specific effects.
- Keep screen composables free of Android-only APIs where cheap; isolate those behind small wrappers.

## Keyboard

Native Android Views (RecyclerView), not Compose. Reasons: `InputMethodService` view lifecycle makes Compose awkward and fragile. See `docs/KEYBOARD.md`.

## Files and storage

- Stickers: `files/stickers/<hash>.<ext>`, stored per IMPORT_EXPORT "Stored format" (still images at most 512px, lossless WebP); `<ext>` follows the stored type
- Thumbnails (static, small): `files/thumbs/<hash>.webp`
- The database stores relative paths only.
- Content URIs for insertion go through a `FileProvider` (authority `<APP_ID>.fileprovider`) granting read access to the target app.

## Threading

- Disk and DB work on `Dispatchers.IO`.
- Import decodes and thumbnails on a bounded worker pool; report progress as `Flow`.

## Open decisions (ask the user)

- Package ID: `app.kisscut.keys` (user confirmed 2026-11-22, see `docs/BRAND.md`). It cannot change after the first Play upload.
- Free-tier limits for the $1 unlock.
- Whether to add Koin.
- Shrinking animated stickers: decided for GIF (own Kotlin encoder, no library, ROADMAP M4.5). Animated WebP would need libwebp; still undecided, stored unchanged.
