# BRAND.md

Name, icon and listing text. The icon is final. The package ID is a default the user can still change before first release.

## Names

| Where | Text |
|---|---|
| Brand | Kisscut Keys |
| Launcher label (`app_name`) | Kisscut Keys |
| Play title (max 30 chars) | Kisscut: Sticker Keyboard |
| Keyboard name in Android settings (`android:label` of the IME service) | Kisscut Keys |
| Short description | Your sticker library, on your keyboard. |
| Package / `applicationId` | `app.kisscut.keys` (permanent once published; confirm with the user first) |

Kotlin namespace and Gradle module names follow the package ID. Replace `com.example.stickers` everywhere.

## Icon: the keycap

A lime keycap with a sticker face on a dark ink background. No text, no cut line.

Colors: Ink `#0F0E15` (background), Lime `#C8F23D` (top face), Side `#8DB10F` (key wall), Dish `#D9F76A` (inset), Face `#14131C`, highlight white at 70%.

### Adaptive icon (108 x 108 viewport, visible window 72, safe zone 66, so keep shapes inside 21..87)

`res/drawable/ic_launcher_background.xml`: solid `#0F0E15` rectangle covering 0..108.

`res/drawable/ic_launcher_foreground.xml`, in this order (all coordinates in the 108 viewport):

1. Shadow: rounded rect x=25 y=32 w=58 h=56 r=15, fill black at 40% (`#66000000`).
2. Key wall: rounded rect x=25 y=28 w=58 h=56 r=15, fill `#8DB10F`.
3. Top face: rounded rect x=31 y=31 w=46 h=42 r=11, fill `#C8F23D`.
4. Dish: rounded rect x=35 y=34.5 w=38 h=35 r=8, fill `#D9F76A`.
5. Highlight: line from (38,36.5) to (60,36.5), stroke `#FFFFFF` at 70% (`#B3FFFFFF`), width 2.2, round cap.
6. Eyes: circles r=2.4 at (48,48.8) and (60,48.8), fill `#14131C`.
7. Smile: path `M46.8 53.6 Q54 60.8 61.2 53.6`, stroke `#14131C`, width 2.4, round cap, no fill.

Rounded rects become paths with arcs in the vector drawable (`android:pathData`); do not use a rasterized image.

`res/drawable/ic_launcher_monochrome.xml` (themed icon, single color): items 2 and 3 as one path with `evenOdd` fill so the top face is a hole in the wall, then the eyes and smile from items 6 and 7 filled/stroked in the same color. Reference it from the adaptive icon XML's `<monochrome>` tag.

`res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` reference background, foreground and monochrome.

### Play Store icon

512 x 512 PNG, full bleed (no rounded corners, no shadow, Play applies the mask). Render the same layers at 512 scale (multiply coordinates by 512/108) over the Ink background. Save to `artifacts/play/icon-512.png`. Do not commit generated build output elsewhere.

### In-app use

- Wordmark: "Kisscut Keys" in Bricolage Grotesque 800, tight tracking (-1.5px at 44), in Text color on dark or Ink on light.
- Onboarding and the keyboard setup screen may show the icon at 72dp. Use the same vector drawable.
- Do not use the logo as a sticker or put a cut line around it.

## Do not

- Add text to the icon.
- Add a purple or other colored ring.
- Use gradients, blur or glow.
- Change the face. It matches the in-app sticker face.

## Reference

Design source: Logos page of the "Sticker app: UI designs" artifact, boards "Real keycaps" (this icon is the second variant, "Keycap, no cut line").
