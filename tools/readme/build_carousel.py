"""Builds docs/media/demo-carousel.gif (the README carousel) from the three demo GIFs in docs/media: each demo
plays, then slides to the next, with a caption and page dots under the phone. Run from the repo root:
    python tools/readme/build_carousel.py
Needs Pillow. GitHub READMEs drop scripts and most CSS, so the carousel is one animated GIF."""
import os
from PIL import Image, ImageDraw, ImageFont

MEDIA = 'docs/media/'
FONT = 'app/ui/android/src/main/res/font/'
DEMOS = [
    ('demo-type-and-send.gif', 'One keyboard for typing and stickers'),
    ('demo-save-from-any-app.gif', 'Save stickers from any app'),
    ('demo-library.gif', 'Your library, sorted your way'),
]
W, H = 390, 844
BAR = 96
STEP = 100          # ms per output frame
SLIDE = 6           # frames per slide transition
INK = (15, 14, 21)
TEXT = (245, 244, 251)
MUTED = (74, 72, 96)
LIME = (200, 242, 61)

title_font = ImageFont.truetype(FONT + 'bricolage_grotesque.ttf', 22)
try:
    title_font.set_variation_by_axes([800])
except Exception:
    pass


def frames_of(path):
    """Every frame of a GIF, repeated to fill its duration in STEP ms slices."""
    im = Image.open(path)
    out = []
    for i in range(im.n_frames):
        im.seek(i)
        f = im.convert('RGB')
        out += [f] * max(1, round(im.info.get('duration', STEP) / STEP))
    return out


def bar(index):
    img = Image.new('RGB', (W, BAR), INK)
    d = ImageDraw.Draw(img)
    caption = DEMOS[index][1]
    tw = d.textlength(caption, font=title_font)
    d.text(((W - tw) / 2, 18), caption, font=title_font, fill=TEXT)
    # Page dots: the current one is a lime pill.
    total = len(DEMOS) * 10 + (len(DEMOS) - 1) * 8 + 14
    x = (W - total) / 2
    for i in range(len(DEMOS)):
        w = 24 if i == index else 10
        d.rounded_rectangle((x, 64, x + w, 74), radius=5, fill=LIME if i == index else MUTED)
        x += w + 8
    return img


def compose(phone, index):
    out = Image.new('RGB', (W, H + BAR), INK)
    out.paste(phone, (0, 0))
    out.paste(bar(index), (0, H))
    return out


demos = [frames_of(MEDIA + name) for name, _ in DEMOS]
out = []
for i, frames in enumerate(demos):
    out += [compose(f, i) for f in frames]
    nxt = demos[(i + 1) % len(demos)][0]
    for s in range(1, SLIDE + 1):
        t = s / SLIDE
        t = t * t * (3 - 2 * t)  # ease in and out
        shift = int(W * t)
        phone = Image.new('RGB', (W, H), INK)
        phone.paste(frames[-1], (-shift, 0))
        phone.paste(nxt, (W - shift, 0))
        out.append(compose(phone, (i + 1) % len(demos) if t > 0.5 else i))

# One palette for the whole loop, from frames spread across it.
sample = out[::max(1, len(out) // 12)]
sheet = Image.new('RGB', (W, (H + BAR) * len(sample)))
for i, f in enumerate(sample):
    sheet.paste(f, (0, (H + BAR) * i))
palette = sheet.quantize(colors=220, method=Image.Quantize.MEDIANCUT)
q = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in out]
target = MEDIA + 'demo-carousel.gif'
q[0].save(target, save_all=True, append_images=q[1:], duration=STEP, loop=0, optimize=True, disposal=1)
print(len(out), 'frames,', round(len(out) * STEP / 1000, 1), 's,', round(os.path.getsize(target) / 1024), 'KB')
