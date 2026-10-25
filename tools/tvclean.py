#!/usr/bin/env python3
"""Safe adapter for CleanYourTV-style Android TV package lists.

The default is a read-only dry run. --apply records the current state before issuing
reversible `pm disable-user --user 0` commands. HOME and core system packages are refused
unless the caller explicitly allows them.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$")
CORE_PROTECTED = {
    "android",
    "app.cozytv",
    "com.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.android.providers.settings",
    "com.android.settings",
    "com.android.shell",
    "com.android.systemui",
    "com.android.tv.settings",
    "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller",
}
HOME = "android.intent.category.HOME"
TV_INPUT_PERMISSION = "android.permission.TV_INPUT_HARDWARE"
SETUP_WIZARD = "com.google.android.tungsten.setupwraith"


def read_packages(path: Path) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if not PACKAGE_RE.fullmatch(line):
            raise ValueError(f"{path}:{number}: invalid package name: {line!r}")
        if line not in seen:
            result.append(line)
            seen.add(line)
    if not result:
        raise ValueError(f"{path}: no package names")
    return result


class Adb:
    def __init__(self, adb: str, serial: str | None) -> None:
        self.adb = adb
        self.serial = serial

    def command(self, *args: str) -> list[str]:
        result = [self.adb]
        if self.serial:
            result += ["-s", self.serial]
        return result + list(args)

    def shell(self, *args: str, check: bool = True) -> str:
        completed = subprocess.run(
            self.command("shell", *args),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if completed.returncode != 0 or check and "Error:" in completed.stderr:
            raise RuntimeError(
                f"adb {' '.join(args)} failed ({completed.returncode}): "
                f"{completed.stderr.strip() or completed.stdout.strip()}"
            )
        return completed.stdout.strip()

    def package_set(self, disabled: bool) -> set[str]:
        args = ["pm", "list", "packages"]
        if disabled:
            args.append("-d")
        try:
            output = self.shell(*args, "--user", "0")
        except RuntimeError:
            output = self.shell(*args)
        return {
            line.removeprefix("package:")
            for line in output.splitlines()
            if line.startswith("package:")
        }

    def home(self) -> str | None:
        output = self.shell(
            "cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN", "-c", HOME,
            check=False,
        )
        for line in reversed(output.splitlines()):
            if "/" in line and " " not in line.strip():
                return line.strip()
        return None


def default_record_dir() -> Path:
    return Path.home() / ".cozytv" / "cleanup"


def build_record(
    adb: Adb,
    list_path: Path,
    packages: list[str],
) -> tuple[dict[str, str], dict]:
    installed = adb.package_set(disabled=False)
    disabled = adb.package_set(disabled=True)
    states = {
        package: (
            "absent" if package not in installed
            else "disabled" if package in disabled
            else "enabled"
        )
        for package in packages
    }
    return states, {
        "format": 1,
        "created": datetime.now(timezone.utc).isoformat(),
        "list": str(list_path.resolve()),
        "home": adb.home(),
        "packages": states,
    }


def protected_reason(package: str, allow_home: bool, installed_home: str | None) -> str | None:
    if package in CORE_PROTECTED:
        return "core system package"
    if not allow_home:
        home_package = (installed_home or "").split("/", 1)[0]
        if home_package and package == home_package:
            return "current HOME package; use the system Home picker first"
        if "." in package and any(part in package.lower() for part in ("launcher", ".home", "tvhome")):
            return "possible HOME package; --allow-home is required"
    return None


def input_driver_reason(adb: Adb, package: str) -> str | None:
    output = adb.shell("dumpsys", "package", package, check=False)
    if TV_INPUT_PERMISSION in output:
        return "requests TV_INPUT_HARDWARE; may control HDMI/AV/tuner inputs"
    return None


def setup_wizard_reason(adb: Adb) -> str:
    wifi = adb.shell("dumpsys", "wifi", check=False)
    if SETUP_WIZARD in wifi:
        return "owns a saved Wi-Fi network; disabling it may disconnect the TV"
    if "cname=" not in wifi:
        return "could not verify Wi-Fi ownership; refusing to disable by default"
    return "setup wizard is listed; re-run with --force-setupwraith only after review"


def print_plan(states: dict[str, str], protected: dict[str, str]) -> None:
    enabled = [name for name, state in states.items() if state == "enabled" and name not in protected]
    print(f"Will disable {len(enabled)} package(s):")
    for package in enabled:
        print(f"  {package}")
    skipped = [(name, state) for name, state in states.items() if state != "enabled"]
    if skipped:
        print(f"Skipping {len(skipped)} package(s) that are absent or already disabled:")
        for name, state in skipped:
            print(f"  {name}  [{state}]")
    if protected:
        print(f"Skipping {len(protected)} protected package(s):")
        for name, reason in protected.items():
            print(f"  {name}  [{reason}]")


def apply_plan(adb: Adb, enabled: list[str]) -> list[tuple[str, str]]:
    failures: list[tuple[str, str]] = []
    for package in enabled:
        try:
            output = adb.shell("pm", "disable-user", "--user", "0", package)
            print(f"disabled {package}: {output or 'OK'}")
        except Exception as error:  # Keep applying independent packages.
            failures.append((package, str(error)))
            print(f"FAILED   {package}: {error}", file=sys.stderr)
    return failures


def restore(adb: Adb, record_path: Path) -> int:
    record = json.loads(record_path.read_text(encoding="utf-8"))
    if record.get("format") != 1:
        raise ValueError("unsupported recovery record format")
    packages = record.get("packages")
    if not isinstance(packages, dict):
        raise ValueError("recovery record has no package states")
    failures = 0
    for package, state in packages.items():
        if not isinstance(package, str) or not PACKAGE_RE.fullmatch(package):
            raise ValueError(f"invalid package in recovery record: {package!r}")
        if state != "enabled":
            continue
        try:
            output = adb.shell("pm", "enable", "--user", "0", package)
            print(f"enabled  {package}: {output or 'OK'}")
        except Exception as error:
            failures += 1
            print(f"FAILED   {package}: {error}", file=sys.stderr)
    print(f"Restore complete; {failures} failure(s). HOME was not changed by this tool.")
    return 1 if failures else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Audit or apply a CleanYourTV-compatible package list with recovery.",
    )
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--list", type=Path, help="debloat.txt file")
    action.add_argument("--restore", type=Path, help="JSON recovery record")
    parser.add_argument("--apply", action="store_true", help="actually disable packages")
    parser.add_argument("--yes", action="store_true", help="skip the interactive confirmation")
    parser.add_argument("--allow-home", action="store_true", help="allow HOME-looking packages")
    parser.add_argument(
        "--allow-input",
        action="store_true",
        help="allow packages requesting TV_INPUT_HARDWARE",
    )
    parser.add_argument(
        "--force-setupwraith",
        action="store_true",
        help="allow the setup wizard even when it may own saved Wi-Fi",
    )
    parser.add_argument("--device", help="adb serial, for example <TV_IP>:5555")
    parser.add_argument("--adb", default="adb", help="adb executable")
    parser.add_argument("--record-dir", type=Path, default=default_record_dir())
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    adb = Adb(args.adb, args.device)
    if args.restore:
        return restore(adb, args.restore)

    try:
        packages = read_packages(args.list)
        states, record = build_record(adb, args.list, packages)
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    protected = {
        name: reason
        for name, state in states.items()
        if state == "enabled"
        if (reason := protected_reason(name, args.allow_home, record.get("home")))
    }
    if not args.allow_input:
        for package, state in states.items():
            if state != "enabled" or package in protected:
                continue
            try:
                reason = input_driver_reason(adb, package)
            except RuntimeError:
                reason = "could not verify TV input permissions; refusing to disable by default"
            if reason:
                protected[package] = reason
    if not args.force_setupwraith and states.get(SETUP_WIZARD) == "enabled":
        try:
            protected[SETUP_WIZARD] = setup_wizard_reason(adb)
        except RuntimeError:
            protected[SETUP_WIZARD] = "could not inspect Wi-Fi ownership; refusing by default"
    print_plan(states, protected)
    enabled = [
        name for name, state in states.items()
        if state == "enabled" and name not in protected
    ]
    if not args.apply:
        print("Dry run only. Nothing changed. Re-run with --apply after reviewing the list.")
        return 0
    if not enabled:
        print("Nothing to apply.")
        return 0
    if not args.yes:
        answer = input(f"Disable {len(enabled)} package(s) on this TV? Type 'yes': ").strip()
        if answer != "yes":
            print("Cancelled. Nothing changed.")
            return 1

    args.record_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    record_path = args.record_dir / f"recovery-{stamp}.json"
    record_path.write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Recovery record: {record_path}")
    failures = apply_plan(adb, enabled)
    print(
        f"Applied {len(enabled) - len(failures)}/{len(enabled)} package(s). "
        f"Undo: python3 {Path(__file__).name} --restore {record_path}",
    )
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())

