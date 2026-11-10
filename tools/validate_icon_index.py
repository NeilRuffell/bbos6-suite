#!/usr/bin/env python3
"""Validate the canonical BBOS6 asset index; no third-party dependencies."""
import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "icon-index.csv"
ICONS = ROOT / "icons"


def main():
    with INDEX.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        expected = ["filename", "name", "keywords", "android_resource"]
        if reader.fieldnames != expected:
            raise SystemExit(f"Invalid columns: expected {expected!r}")
        rows = list(reader)

    if not rows:
        raise SystemExit("Icon index is empty")
    seen = set()
    last = -1
    for line, row in enumerate(rows, start=2):
        filename = row["filename"]
        match = re.fullmatch(r"(\d+)\.png", filename)
        if not match:
            raise SystemExit(f"Line {line}: invalid original asset filename")
        number = int(match.group(1))
        if filename in seen:
            raise SystemExit(f"Line {line}: duplicate entry {filename}")
        if number <= last:
            raise SystemExit(f"Line {line}: index not strictly numerically sorted")
        seen.add(filename)
        last = number

        if not (ICONS / filename).is_file():
            raise SystemExit(f"Line {line}: missing source asset {filename}")
        if not row["name"].strip():
            raise SystemExit(f"Line {line}: missing verified identification")
        resource = row["android_resource"].strip()
        if resource and not re.fullmatch(r"[a-z][a-z0-9_]*", resource):
            raise SystemExit(f"Line {line}: invalid Android resource {resource}")

    print(f"Valid canonical icon index: {len(rows)} verified assets")


if __name__ == "__main__":
    main()
