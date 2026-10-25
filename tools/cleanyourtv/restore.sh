#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TV_HOST="${TV_HOST:-<TV_IP>:5555}"
RECORD="${1:?usage: restore.sh /path/to/recovery.json}"

exec python3 "$ROOT/tools/tvclean.py" --device "$TV_HOST" --restore "$RECORD"
