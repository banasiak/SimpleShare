#!/usr/bin/env python3
"""Prints the on-screen labels screenshots.sh taps and waits for, in one of the listing's languages,
as regular expressions built from the app's own string resources, so the screenshots follow the
translations without the script having to know them.

  labels.py CODE     a Play Console language code such as de-DE; prints "name<TAB>regex" lines

A language's strings are read over the English ones, so a string it lacks falls back the same way
it does in the app.
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parents[2] / "share/src/main/res"

# the resource directories a Play Console code reads, in the order Android falls back through them.
# Any other code reads the directory named for its language alone, such as values-de for de-DE
DIRECTORIES = {"es-419": ["values-es", "values-b+es+419"]}


def unescape(value):
    # Android's own escaping in strings.xml, on top of XML's: an optional pair of surrounding
    # quotes, and backslash escapes such as the \' in "I\'m Feeling Lucky"
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] == '"':
        value = value[1:-1]
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), value)


def strings(directory):
    path = RES / directory / "strings.xml"
    if not path.exists():
        return {}
    return {s.get("name"): unescape("".join(s.itertext())) for s in ET.parse(path).getroot().iter("string")}


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    code = sys.argv[1]
    s = strings("values")
    if not code.startswith("en"):
        found = {}
        for directory in DIRECTORIES.get(code, [f"values-{code.split('-')[0]}"]):
            found.update(strings(directory))
        if not found:
            sys.exit(f"labels.py: the app has no strings for {code}")
        s.update(found)

    def exact(key):
        return "^" + re.escape(s[key]) + "$"

    labels = {
        "lucky": exact("feeling_lucky"),
        "remove": exact("remove_tracking"),
        "parameters": exact("query_parameters"),
        "sanitized": exact("sanitized_url"),
        "decode_hint": exact("hint_decode_short_url"),
    }
    for name, pattern in labels.items():
        print(f"{name}\t{pattern}")


if __name__ == "__main__":
    main()
