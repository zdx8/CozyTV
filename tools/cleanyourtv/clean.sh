#!/usr/bin/env bash
set -euo pipefail

# Device-specific CleanYourTV workflow over ADB.
# Default is a read-only dry run. Nothing is disabled until --apply is supplied.

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TV_HOST="${TV_HOST:-<TV_IP>:5555}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
PROFILE="${PROFILE:-}"

shell() {
  "$ADB" -s "$TV_HOST" shell "$@" | tr -d '\r'
}

if [[ -z "$PROFILE" ]]; then
  MODEL="$(shell getprop ro.product.model)"
  DEVICE="$(shell getprop ro.product.device)"
  PRODUCT="$(shell getprop ro.product.name)"
  BOARD="$(shell getprop ro.product.board)"
  PROFILE="$(python3 "$ROOT/tools/cleanyourtv/select_profile.py" \
    "$MODEL" "$DEVICE" "$PRODUCT" "$BOARD")"
fi

if [[ ! -f "$PROFILE" ]]; then
  echo "CleanYourTV profile not found: $PROFILE" >&2
  echo "Use PROFILE=$ROOT/tools/cleanyourtv/profiles/<model>.txt" >&2
  exit 2
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
SCOPES="${CLEAN_SCOPES:-ads,telemetry}"
if [[ "$SCOPES" == "all" ]]; then SCOPES="ads,telemetry,optional"; fi

FILTERED_PROFILE="$TMP_DIR/debloat.txt"
python3 "$ROOT/tools/cleanyourtv/filter_plan.py" \
  --scopes "$SCOPES" --profile "$PROFILE" --output "$FILTERED_PROFILE" || true

echo "TV:      $TV_HOST"
echo "Profile: $PROFILE"
echo "Scopes:  $SCOPES"
echo

if [[ ! -s "$FILTERED_PROFILE" ]] || ! grep -qE '^[A-Za-z_][A-Za-z0-9_]*\.' "$FILTERED_PROFILE"; then
  echo "No packages are selected in the cleanup plan. Nothing to do." >&2
  exit 0
fi

args=(--adb "$ADB" --device "$TV_HOST" --list "$FILTERED_PROFILE")
if [[ "${1:-}" == "--apply" ]]; then
  args+=(--apply)
  shift
fi

exec python3 "$ROOT/tools/tvclean.py" "${args[@]}" "$@"
