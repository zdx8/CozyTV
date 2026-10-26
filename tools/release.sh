#!/usr/bin/env bash
#
# CozyTV release script.
#
# One command from "code is ready" to "signed APK on GitHub Releases":
#
#   tools/release.sh 0.4.0                 # build + sign + verify + dist/
#   tools/release.sh 0.4.0 --publish       # …and publish to GitHub
#
# What it does, in order:
#   1. preflight  — JDK 17, keystore.properties, keystore file, gh CLI
#   2. bumps the versionCode / versionName defaults in app/build.gradle.kts
#   3. assembles the release build (R8 + resource shrinking + signing)
#   4. verifies the APK signature — an unsigned or debug-signed build is
#      rejected, because the in-app updater compares signatures and a
#      debug key can never be rotated
#   5. copies the APK to dist/CozyTV-vX.Y.Z-release.apk + .sha256
#   6. --publish: tag, push and create the GitHub Release
#
# Environment:
#   GRADLE_FLAGS   extra flags for gradlew (e.g. "--offline")
#   COZYTV_REPO    GitHub repo (default zdx8/CozyTV)
#   COZYTV_JAVA    JDK 17 home (default: /usr/libexec/java_home -v 17)
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

COZYTV_REPO="${COZYTV_REPO:-zdx8/CozyTV}"
GRADLE_FLAGS="${GRADLE_FLAGS:-}"
DIST_DIR="$REPO_ROOT/dist"

VERSION=""
VERSION_CODE=""
PUBLISH=0
ASSUME_YES=0
NOTES=""

usage() {
  sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

Usage:
  tools/release.sh <version> [options]

Options:
  --code <n>      versionCode (default: current default + 1)
  --publish       push the tag and create the GitHub Release
  --notes <text>  release notes (default: a short auto-generated note)
  --repo <slug>   GitHub repo (default: zdx8/CozyTV)
  --yes           do not ask for confirmation before publishing
  -h, --help      this text
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --code) VERSION_CODE="$2"; shift 2 ;;
    --publish) PUBLISH=1; shift ;;
    --notes) NOTES="$2"; shift 2 ;;
    --repo) COZYTV_REPO="$2"; shift 2 ;;
    --yes|-y) ASSUME_YES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown option: $1" >&2; usage; exit 2 ;;
    *) VERSION="$1"; shift ;;
  esac
done

if [[ -z "$VERSION" ]]; then
  echo "error: version is required (e.g. tools/release.sh 0.4.0)" >&2
  usage
  exit 2
fi

if [[ ! "$VERSION" =~ ^[0-9]+(\.[0-9]+)*(-[0-9A-Za-z.]+)?$ ]]; then
  echo "error: '$VERSION' is not a valid version string" >&2
  exit 2
fi

log()  { printf '\033[1;36m▸ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m! %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- preflight
log "preflight"

if [[ -z "${COZYTV_JAVA:-}" ]]; then
  COZYTV_JAVA="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
[[ -n "$COZYTV_JAVA" && -d "$COZYTV_JAVA" ]] || die "JDK 17 not found (set COZYTV_JAVA)"
export JAVA_HOME="$COZYTV_JAVA"
JAVA_VERSION="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')"
ok "JDK $JAVA_VERSION"

[[ -f "$REPO_ROOT/keystore.properties" ]] || \
  die "keystore.properties missing — copy keystore.properties.example and fill it in"
# shellcheck disable=SC1091
STORE_FILE="$(sed -n 's/^storeFile=//p' "$REPO_ROOT/keystore.properties" | head -1 | tr -d '\r')"
[[ -n "$STORE_FILE" ]] || die "keystore.properties has no storeFile"
if [[ ! -f "$REPO_ROOT/$STORE_FILE" && ! -f "$REPO_ROOT/app/$STORE_FILE" ]]; then
  die "keystore file '$STORE_FILE' not found"
fi
ok "keystore configured"

command -v apksigner >/dev/null 2>&1 || {
  APKSIGNER="$(ls -d "$HOME/Library/Android/sdk/build-tools/"* 2>/dev/null | sort -V | tail -1)/apksigner"
  [[ -x "$APKSIGNER" ]] || die "apksigner not found"
  export PATH="$(dirname "$APKSIGNER"):$PATH"
}
command -v apksigner >/dev/null 2>&1 || die "apksigner not found"

if [[ "$PUBLISH" == "1" ]]; then
  command -v gh >/dev/null 2>&1 || die "gh CLI not found (needed for --publish)"
  gh auth status >/dev/null 2>&1 || die "gh is not authenticated — run 'gh auth login'"
  ok "gh authenticated"
fi

# ------------------------------------------------------- version defaults
CURRENT_CODE="$(sed -n 's/.*versionCodeOverride ?: \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
CURRENT_NAME="$(sed -n 's/.*versionNameOverride ?: "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
: "${CURRENT_CODE:=0}"
: "${CURRENT_NAME:=0.0.0}"

if [[ -z "$VERSION_CODE" ]]; then
  if [[ "$CURRENT_NAME" == "$VERSION" ]]; then
    VERSION_CODE="$CURRENT_CODE"
  else
    VERSION_CODE=$((CURRENT_CODE + 1))
  fi
fi

log "version $VERSION (code $VERSION_CODE), previous default was $CURRENT_NAME ($CURRENT_CODE)"

# Bump the defaults so a plain `./gradlew assembleRelease` keeps producing
# the released version instead of the previous one.
python3 - "$VERSION_CODE" "$VERSION" <<'PY'
import re, sys, pathlib
code, name = sys.argv[1], sys.argv[2]
p = pathlib.Path("app/build.gradle.kts")
s = p.read_text()
# NOTE: the "?" in "?: " has to be escaped — "?:" would otherwise be
# read as the start of a non-capturing group and the pattern would not
# match the Kotlin source.
s = re.sub(r'versionCode = versionCodeOverride \?: \d+', f'versionCode = versionCodeOverride ?: {code}', s, count=1)
s = re.sub(r'versionName = versionNameOverride \?: "[^"]*"', f'versionName = versionNameOverride ?: "{name}"', s, count=1)
p.write_text(s)
PY
ok "app/build.gradle.kts defaults updated to $VERSION ($VERSION_CODE)"

# ------------------------------------------------------------------ build
log "assembleRelease (this takes ~45 s cold)"
# shellcheck disable=SC2086
./gradlew $GRADLE_FLAGS assembleRelease \
  -PcozytvVersionCode="$VERSION_CODE" \
  -PcozytvVersionName="$VERSION" \
  -q || die "gradle build failed"

APK="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || die "signed APK not found at $APK"

# ----------------------------------------------------------- verify signing
log "verifying signature"
CERT_DN="$(apksigner verify --print-certs "$APK" 2>/dev/null | sed -n 's/^Signer #1 certificate DN: //p' | head -1)"
CERT_SHA="$(apksigner verify --print-certs "$APK" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
[[ -n "$CERT_DN" ]] || die "APK is not signed — check keystore.properties"
if [[ "$CERT_DN" == *"Android Debug"* ]]; then
  die "APK is signed with the Android debug key ('$CERT_DN'). Publish that and the
    key can never be rotated. Configure keystore.properties and rebuild."
fi
ok "signed by $CERT_DN"
ok "certificate SHA-256 $CERT_SHA"

BADGING="$(command -v aapt2 >/dev/null 2>&1 && aapt2 dump badging "$APK" 2>/dev/null | head -1 || true)"
if [[ -z "$BADGING" ]]; then
  AAPT2="$(ls -d "$HOME/Library/Android/sdk/build-tools/"* 2>/dev/null | sort -V | tail -1)/aapt2"
  BADGING="$("$AAPT2" dump badging "$APK" 2>/dev/null | head -1)"
fi
echo "  $BADGING"
echo "$BADGING" | grep -q "versionName='$VERSION'" || die "APK versionName does not match $VERSION"
echo "$BADGING" | grep -q "versionCode='$VERSION_CODE'" || die "APK versionCode does not match $VERSION_CODE"

# ------------------------------------------------------------------- dist
mkdir -p "$DIST_DIR"
OUT_APK="$DIST_DIR/CozyTV-v$VERSION-release.apk"
cp "$APK" "$OUT_APK"
chmod 644 "$OUT_APK"
SHA="$(shasum -a 256 "$OUT_APK" | awk '{print $1}')"
echo "$SHA  $(basename "$OUT_APK")" > "$OUT_APK.sha256"
SIZE="$(stat -f%z "$OUT_APK")"

ok "artifact $OUT_APK"
ok "sha256  $SHA"
ok "size    $SIZE bytes"

# ---------------------------------------------------------------- publish
if [[ "$PUBLISH" != "1" ]]; then
  echo
  log "build only — nothing was pushed. Add --publish to create the GitHub Release."
  exit 0
fi

TAG="v$VERSION"
if git rev-parse "$TAG" >/dev/null 2>&1; then
  die "tag $TAG already exists"
fi

if [[ "$ASSUME_YES" != "1" ]]; then
  echo
  log "about to publish"
  echo "  repo    $COZYTV_REPO"
  echo "  tag     $TAG"
  echo "  asset   $(basename "$OUT_APK") ($SIZE bytes)"
  echo "  sha256  $SHA"
  read -r -p "publish? [y/N] " reply
  [[ "$reply" == "y" || "$reply" == "Y" ]] || die "aborted"
fi

[[ -n "$NOTES" ]] || NOTES="CozyTV $VERSION.

- 正式发布签名（CN=CozyTV），从此版本起更新走 GitHub Releases 自更新。
- 此前版本用本地 debug key 签名，签名不同，需先卸载旧版再安装。"

log "git tag + push"
git tag -a "$TAG" -m "CozyTV $VERSION"
git push origin HEAD
git push origin "$TAG"

log "gh release create"
gh release create "$TAG" "$OUT_APK" \
  --repo "$COZYTV_REPO" \
  --title "CozyTV $VERSION" \
  --notes "$NOTES"

ok "published https://github.com/$COZYTV_REPO/releases/tag/$TAG"
