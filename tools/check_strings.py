#!/usr/bin/env python3
"""
Run this after adding or editing a language: python3 tools/check_strings.py
Checks every values-*/strings.xml against values/strings.xml (the source of truth) for:
  - missing or extra keys
  - mismatched %1$s-style placeholders (a common cause of crashes)
Exit code is non-zero if anything is wrong, so it can be used in CI.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
PLACEHOLDER = re.compile(r"%(\d+\$)?[sd]")

def load(path):
    out = {}
    for c in ET.parse(path).getroot():
        if c.tag == "string" and c.get("name"):
            out[c.get("name")] = "".join(c.itertext())
    return out

def placeholders(s):
    return sorted(PLACEHOLDER.findall(s) or PLACEHOLDER.findall(s))

def main():
    base_file = RES / "values/strings.xml"
    base = load(base_file)
    ok = True
    for folder in sorted(RES.glob("values-*")):
        f = folder / "strings.xml"
        if not f.exists():
            continue
        other = load(f)
        missing = base.keys() - other.keys()
        extra = other.keys() - base.keys()
        if missing:
            ok = False
            print(f"[{folder.name}] MISSING {len(missing)} keys: {sorted(missing)}")
        if extra:
            ok = False
            print(f"[{folder.name}] EXTRA keys not in base: {sorted(extra)}")
        for k in base.keys() & other.keys():
            pb, po = re.findall(r"%\d\$[sd]", base[k]), re.findall(r"%\d\$[sd]", other[k])
            if sorted(pb) != sorted(po):
                ok = False
                print(f"[{folder.name}] {k}: placeholders differ - base {pb} vs translation {po}")
    if ok:
        print(f"OK - all languages match {base_file.relative_to(RES.parent.parent.parent)} ({len(base)} keys)")
    return 0 if ok else 1

if __name__ == "__main__":
    sys.exit(main())
