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
4. **Keyboard:** a typing keyboard (QWERTY, numbers and symbols) with English suggestions, autocorrect (off by default until Tagalog lands) and swipe typing, all on the device (ROADMAP M7; it was a v1 non-goal until the user asked for it), plus a personal word list it learns on the phone (M7a, with an off switch and Clear in Settings), and a sticker key that swaps to the sticker panel: tabs for Recents, Favorites and collections, a grid, hold the space bar to switch keyboards. Animated stickers play only while visible. The keyboard never stores, logs or sends what is typed: it holds only the word being typed, in memory, and reads at most the one character before a new word (KEYBOARD, Typing pipeline). Learning the user's words (ROADMAP M7a) will change this promise and needs the user's sign-off on the new wording.
5. **Insert** a sticker into the chat app using commit-content. When the target app doesn't support images, fall back to copying to the clipboard and show a short hint.
6. **Backup:** export and import `.stickerpack` files (stickers, collections, optional settings). Dedupe on import.
7. **Share with a friend** by sending a `.stickerpack` file through any app. No accounts.
8. **Settings:** theme (system, dark, light), animation on or off, backup and restore, keyboard setup help.

## Non-goals for v1

- No accounts, cloud sync, or backend.
- No auto-tagging or ML. Planned after v1 basics in ROADMAP M8 (on-device labelling); a non-goal until then.
- No search. Collections replace tags. Planned with tagging in ROADMAP M8; a non-goal until then.
- No sticker creation or editing.
- No iOS. The shared module keeps the door open but nothing iOS is built.
- No Wi-Fi or QR direct transfer, and no relay server. Both need the INTERNET permission or hosting; revisit after v1.

## Monetization

Free with a one-time ~$1 in-app unlock, preferred over paid-upfront. Free tier limits are undecided: **ask the user before implementing any gating**. Build everything ungated first; add a single `Entitlements` interface so gating can be added later without touching features.

## Privacy position

Everything stays on the device. No internet permission, no analytics. State this in the Play listing. Requires a privacy policy page on Play (the user will write it).

The keyboard keeps one thing from typing (ROADMAP M7a): new words the user types, so names, slang and Taglish stop being "fixed". They stay on the phone in a file Android never backs up or moves to a new phone, are never in packs, are never learned from password, email, web address, no-suggestion or incognito fields, and can be turned off or cleared in Settings (KEYBOARD, Learned words). For Play's data safety form this is on-device processing, not collection, since nothing leaves the phone.

## Release facts (for later)

- Google Play developer account: $25 one-time.
- New personal accounts need a closed test (12 testers, 14 days) before production.
- Play takes 15% on the first $1M of revenue per year.
