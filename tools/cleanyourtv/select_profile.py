#!/usr/bin/env python3
"""Select the closest CleanYourTV profile for an Android TV device."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def normalize(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def score(profile: str, tokens: list[str]) -> int:
    slug = normalize(profile)
    result = 0
    for token in tokens:
        value = normalize(token)
        if len(value) < 5:
            continue
        if slug == value:
            result += 100
        elif slug.endswith(value):
            result += 40 + len(value)
        elif value in slug:
            result += 20 + len(value)
        elif slug in value:
            result += 10 + len(slug)
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("tokens", nargs="+", help="model, device, product, board")
    parser.add_argument("--profiles", type=Path, default=Path(__file__).parent / "profiles")
    parser.add_argument("--list", action="store_true", help="list all profile names")
    args = parser.parse_args()

    profiles = sorted(args.profiles.glob("*.txt"))
    if args.list:
        for profile in profiles:
            print(profile.stem)
        return 0

    ranked = sorted(
        ((score(profile.stem, args.tokens), profile) for profile in profiles),
        key=lambda item: (item[0], item[1].stem),
        reverse=True,
    )
    if not ranked or ranked[0][0] <= 0:
        print("No matching CleanYourTV profile for: " + ", ".join(args.tokens), file=sys.stderr)
        return 1
    print(ranked[0][1])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
