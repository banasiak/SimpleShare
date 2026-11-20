#!/usr/bin/env python3
"""Checks store_listing.xml and release_notes.xml before they are pasted into Play Console.

  python3 store/scripts/check.py

Both files have to parse, cover the same languages, tag each with the code Play Console uses for
it, include English, and keep every field within Play's limit. Exits non-zero on any failure, after
reporting every one.
"""

import pathlib
import sys
import xml.etree.ElementTree as ET

STORE = pathlib.Path(__file__).resolve().parent.parent

# Play Console's own codes for the languages the app is translated into. Most are language-region,
# but not all: Thai is plain "th", and a block tagged th-TH is silently ignored by Play
PLAY_CODES = {
    "en-US", "de-DE", "es-ES", "es-419", "fr-FR", "hi-IN", "it-IT", "ja-JP", "ko-KR", "pl-PL", "pt-BR", "th", "tr-TR", "zh-CN"
}
DEFAULT_CODE = "en-US"

LISTING_LIMITS = {"app_name": 30, "short_description": 80, "full_description": 4000}
NOTES_LIMIT = 500


def text(element):
    # the files keep long text flush-left between indented tags, so the tags' own newlines and
    # indentation are not part of the field
    return (element.text or "").strip("\n ") if element is not None else ""


def main():
    problems = []
    listing = ET.parse(STORE / "store_listing.xml").getroot()
    notes = ET.parse(STORE / "release_notes.xml").getroot()

    for name, root in (("store_listing.xml", listing), ("release_notes.xml", notes)):
        codes = [el.tag for el in root]
        for code in codes:
            if code not in PLAY_CODES:
                problems.append(f"{name}: <{code}> is not one of Play Console's codes for this app's languages")
        if len(set(codes)) != len(codes):
            problems.append(f"{name}: a language appears more than once")
        if DEFAULT_CODE not in codes:
            problems.append(f"{name}: has no <{DEFAULT_CODE}>, the listing's default language")
    listing_codes = {el.tag for el in listing}
    notes_codes = {el.tag for el in notes}
    if listing_codes != notes_codes:
        problems.append(f"the two files cover different languages: {sorted(listing_codes ^ notes_codes)}")

    print(f"{'':7} {'name':>7} {'short':>7} {'full':>9}")
    for el in listing:
        sizes = []
        for field, limit in LISTING_LIMITS.items():
            value = text(el.find(field))
            if not value:
                problems.append(f"store_listing.xml: <{el.tag}> has no {field}")
            elif len(value) > limit:
                problems.append(f"store_listing.xml: <{el.tag}> {field} is {len(value)} characters, over {limit}")
            sizes.append(f"{len(value)}/{limit}")
        print(f"{el.tag:7} {sizes[0]:>7} {sizes[1]:>7} {sizes[2]:>9}")

    print(f"\n{'':7} {'notes':>7}")
    for el in notes:
        value = text(el)
        if not value:
            problems.append(f"release_notes.xml: <{el.tag}> is empty")
        elif len(value) > NOTES_LIMIT:
            problems.append(f"release_notes.xml: <{el.tag}> is {len(value)} characters, over {NOTES_LIMIT}")
        print(f"{el.tag:7} {f'{len(value)}/{NOTES_LIMIT}':>7}")

    for problem in problems:
        print(problem, file=sys.stderr)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
