# PRODUCT.md

## What it is

An Android app plus keyboard for people who collect stickers from the internet and can't find them when chatting. You save stickers into the app, sort them into collections, then pick them from a keyboard panel inside any chat app.

Part of a planned series of small, roughly $1 apps on Google Play.

## Problem

Stickers saved from the web end up scattered in galleries and downloads. Finding one mid-conversation means leaving the chat. Gboard-style sticker panels exist but don't hold your own collection in an organised way.

## v1 features

1. **Save** stickers: share-sheet "Save to library" from any app, and a picker inside the app. Supports PNG, JPEG, WebP, GIF, animated WebP. From ROADMAP M4.5, still images are stored at most 512px on the longest side as lossless WebP to keep the library small (see IMPORT_EXPORT, Stored format).
2. **Library:** grid of all stickers, favorites, recents, "Unsorted".
3. **Collections:** user-made, nestable (depth cap 3). Move stickers between collections. Select mode for bulk move, favorite and delete.
4. **Keyboard:** a basic typing keyboard (QWERTY, numbers and symbols; no autocorrect, suggestions or swipe) with a sticker key that swaps to the sticker panel: tabs for Recents, Favorites and collections, a grid, a globe key to switch keyboards. Animated stickers play only while visible. The keyboard never stores, logs or sends what is typed.
5. **Insert** a sticker into the chat app using commit-content. When the target app doesn't support images, fall back to copying to the clipboard and show a short hint.
6. **Backup:** export and import `.stickerpack` files (stickers, collections, optional settings). Dedupe on import.
7. **Share with a friend** by sending a `.stickerpack` file through any app. No accounts.
8. **Settings:** theme (system, dark, light), animation on or off, backup and restore, keyboard setup help.

## Non-goals for v1

- No accounts, cloud sync, or backend.
- No auto-tagging or ML. Future work: on-device labelling to suggest collections.
- No search. Collections replace tags. Search may come later.
- No sticker creation or editing.
- No iOS. The shared module keeps the door open but nothing iOS is built.
- No Wi-Fi or QR direct transfer, and no relay server. Both need the INTERNET permission or hosting; revisit after v1.

## Monetization

Free with a one-time ~$1 in-app unlock, preferred over paid-upfront. Free tier limits are undecided: **ask the user before implementing any gating**. Build everything ungated first; add a single `Entitlements` interface so gating can be added later without touching features.

## Privacy position

Everything stays on the device. No internet permission, no analytics. State this in the Play listing. Requires a privacy policy page on Play (the user will write it).

## Release facts (for later)

- Google Play developer account: $25 one-time.
- New personal accounts need a closed test (12 testers, 14 days) before production.
- Play takes 15% on the first $1M of revenue per year.
