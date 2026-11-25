"""Builds the keyboard's English word list from the AOSP LatinIME dictionary (docs/KEYBOARD.md, Word list).

Source: platform/packages/inputmethods/LatinIME, dictionaries/en_US_wordlist.combined.gz
(blob 8aed9c5e0b94d0f3e9654f66104d7af1e84c454f), Apache License 2.0, Copyright (c) 2008
The Android Open Source Project. Download it by hand; this script never touches the network.

Usage (from the repo root):
    python tools/wordlist/build_en.py path/to/en_US_wordlist.combined.gz

Writes app/ui/android/src/main/assets/words/en_US.txt and en_US_fixes.txt. Changes made to the
source, stated in each output file's header as the Apache license asks:
- entries with f=0 or not_a_word=true are dropped (their fix pairs are kept);
- the rest is sorted by frequency (ties by word) and cut to the top WORDS entries;
- possibly offensive words are kept but marked, so they are never suggested or used as a correction.
"""

import gzip
import sys
from pathlib import Path

WORDS = 50_000
OUT = Path(__file__).resolve().parents[2] / "app/ui/android/src/main/assets/words"
HEADER = (
    "# From the AOSP LatinIME en_US word list (Apache License 2.0, Copyright (c) 2008 The Android Open Source\n"
    "# Project; see licenses/Apache-2.0-AOSP-LatinIME.txt). Modified by Kisscut Keys: {change}\n"
    "# Built by tools/wordlist/build_en.py; do not edit by hand.\n"
)


def fields(line: str) -> dict:
    return dict(part.split("=", 1) for part in line.strip().split(",") if "=" in part)


def parse(path: Path):
    words, fixes = [], []
    current = None
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith(" word="):
                current = fields(line)
                words.append(current)
            elif line.startswith("  shortcut=") and current is not None:
                s = fields(line)
                if s.get("f") == "whitelist":
                    fixes.append((current["word"], s["shortcut"]))
    return words, fixes


def main(source: str) -> None:
    words, fixes = parse(Path(source))
    kept = [w for w in words if w.get("not_a_word") != "true" and int(w["f"]) > 0]
    kept.sort(key=lambda w: (-int(w["f"]), w["word"]))
    kept = kept[:WORDS]
    for w in kept:
        if "\t" in w["word"] or "\n" in w["word"]:
            raise SystemExit(f"bad word: {w['word']!r}")
    OUT.mkdir(parents=True, exist_ok=True)
    lines = [HEADER.format(change=f"top {WORDS} by frequency; offensive words marked 'o'.")]
    lines.append("# word<TAB>frequency (0-255)[<TAB>o = possibly offensive]\n")
    for w in kept:
        mark = "\to" if w.get("possibly_offensive") == "true" else ""
        lines.append(f"{w['word']}\t{w['f']}{mark}\n")
    (OUT / "en_US.txt").write_text("".join(lines), encoding="utf-8", newline="\n")
    fix_lines = [HEADER.format(change="only the typed-word to fix pairs (whitelist shortcuts) are kept.")]
    fix_lines.append("# typed<TAB>fix\n")
    fix_lines += [f"{typed}\t{fix}\n" for typed, fix in sorted(set(fixes))]
    (OUT / "en_US_fixes.txt").write_text("".join(fix_lines), encoding="utf-8", newline="\n")
    print(f"{len(kept)} words, {len(set(fixes))} fixes -> {OUT}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
