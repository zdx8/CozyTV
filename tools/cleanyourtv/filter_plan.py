#!/usr/bin/env python3
"""Filter a CleanYourTV profile by the cleanup scopes chosen in CozyTV."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$")
AD_WORDS = (
    "advert", "ads", "adservice", "webcontent", "shop", "promotion",
    "recommend", "acr", "sponsor", "autoinstall", "content",
)
TELEMETRY_WORDS = (
    "analytic", "telemetry", "statistic", "tracking", "metric", "report",
    "feedback", "push", "cloudcontrol", "logger", "logging",
)


def group_for(package: str, note: str) -> str:
    text = f"{package} {note}".lower()
    if any(word in text for word in AD_WORDS):
        return "ads"
    if any(word in text for word in TELEMETRY_WORDS):
        return "telemetry"
    return "optional"


def main() -> int:
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--plan", type=Path)
    source.add_argument("--scopes", help="comma-separated ads,telemetry,optional")
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.plan:
        plan = json.loads(args.plan.read_text(encoding="utf-8"))
        selected = {
            group
            for group in ("ads", "telemetry", "optional")
            if plan.get(group) is True
        }
        device = plan.get("device", "this TV")
    else:
        selected = {
            item.strip()
            for item in (args.scopes or "").split(",")
            if item.strip() in {"ads", "telemetry", "optional"}
        }
        device = "this TV"
    if not selected:
        print("No cleanup scopes were selected.")

    output = [
        f"# CozyTV cleanup plan for {device}",
        "# Selected: " + (", ".join(sorted(selected)) if selected else "none"),
        "# Review this file before using --apply.",
        "",
    ]
    count = 0
    for raw in args.profile.read_text(encoding="utf-8-sig").splitlines():
        clean = raw.split("#", 1)[0].strip()
        if not PACKAGE_RE.fullmatch(clean):
            continue
        note = raw.split("#", 1)[1].strip() if "#" in raw else ""
        if group_for(clean, note) not in selected:
            continue
        output.append(f"{clean}  # {note}".rstrip())
        count += 1

    args.output.write_text("\n".join(output) + "\n", encoding="utf-8")
    print(
        f"Selected {count} package(s) for {', '.join(sorted(selected)) if selected else 'no scopes'}.",
    )
    return 0 if count else 1


if __name__ == "__main__":
    raise SystemExit(main())
