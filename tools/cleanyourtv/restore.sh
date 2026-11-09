#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TV_HOST="${TV_HOST:?请设置 TV_HOST=<电视IP>:5555（电视：设置 → 开发者选项 → 网络调试）}"
RECORD="${1:?usage: restore.sh /path/to/recovery.json}"

exec python3 "$ROOT/tools/tvclean.py" --device "$TV_HOST" --restore "$RECORD"
