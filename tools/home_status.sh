#!/usr/bin/env bash
set -euo pipefail

# Read-only HOME diagnostics. CozyTV never disables the stock launcher.
TV_HOST="${TV_HOST:-<TV_IP>:5555}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"

adb_shell() {
  "$ADB" -s "$TV_HOST" shell "$@"
}

echo "Current HOME:"
adb_shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME |
  tr -d '\r' |
  tail -n 1

echo
echo "Available HOME activities:"
adb_shell cmd package query-activities --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME |
  tr -d '\r'

echo
echo "Disabled launchers (expected to be empty):"
adb_shell pm list packages -d |
  tr -d '\r' |
  grep -E '(^package:com\.mitv\.tvhome$|launcher|cozytv)' ||
  echo "none"
