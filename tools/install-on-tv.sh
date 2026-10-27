#!/usr/bin/env bash
#
# CozyTV 电视端安装 / 升级脚本。
#
# 解决一个麻烦事：v0.3.x 是用本机 Android debug key 签的，v0.4.0 起换成了正式
# 密钥（CN=CozyTV）。签名不同 → Android 不允许覆盖安装 → 必须先卸载。
# 直接 `adb install -r` 会报 INSTALL_FAILED_UPDATE_INCOMPATIBLE。
#
# 本脚本会：
#   1. 连接电视（TV_HOST，默认 <TV_IP>:5555）
#   2. 看电视上是否已装 CozyTV、什么版本
#   3. 先试覆盖安装；失败且是签名冲突时：
#      - 有 root 就先把 /data/data/app.cozytv 整包备份（tar）
#      - 卸载旧版 → 装新版 → 把备份还原回去（收藏、直播源、桌面布局不丢）
#      - 没 root 只能卸载重装，数据会丢（脚本会明确告知）
#   4. 启动 CozyTV，打印版本与 logcat 摘要
#
# 用法：
#   tools/install-on-tv.sh                 # 装 dist/ 里最新的 release 包
#   tools/install-on-tv.sh --debug         # 装 debug 包
#   TV_HOST=<TV_IP>:5555 tools/install-on-tv.sh
#
set -uo pipefail

cd "$(dirname "$0")/.."

export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

TV_HOST="${TV_HOST:-<TV_IP>:5555}"
PKG="app.cozytv"
ACT="$PKG/.MainActivity"
USE_DEBUG=0
KEEP_DATA=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug) USE_DEBUG=1; shift ;;
    --no-keep-data) KEEP_DATA=0; shift ;;
    --host) TV_HOST="$2"; shift 2 ;;
    -h|--help) sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

log()  { printf '\033[1;36m▸ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m! %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 连接
log "连接电视 $TV_HOST"
if ! adb devices | grep -q "^${TV_HOST}[[:space:]]*device$"; then
  adb connect "$TV_HOST" 2>&1 | sed 's/^/  /'
fi
adb devices | grep -q "^${TV_HOST}[[:space:]]*device$" || die "连不上 $TV_HOST
  电视端要开：设置 → 关于 → 连点版本号 7 次 → 开发者选项 → 打开「ADB 调试」和「网络调试」。
  电视的 IP 随 DHCP 变化，先在路由器或电视的「网络信息」里确认。"
ok "已连接"

adb_s() { adb -s "$TV_HOST" "$@"; }

# ---------------------------------------------------------------- APK
if [[ "$USE_DEBUG" == "1" ]]; then
  APK="app/build/outputs/apk/debug/app-debug.apk"
else
  APK="$(ls -1 dist/CozyTV-v*-release.apk 2>/dev/null | sort -V | tail -1)"
  [[ -n "$APK" ]] || APK="app/build/outputs/apk/release/app-release.apk"
fi
[[ -f "$APK" ]] || die "找不到 APK（先跑 tools/release.sh <version> 或 ./gradlew assembleDebug）"
ok "APK: $APK ($(stat -f%z "$APK") 字节)"

# ---------------------------------------------------------------- 现状
INSTALLED_VERSION="$(adb_s shell dumpsys package "$PKG" 2>/dev/null | grep -m1 'versionName=' | tr -d '\r' | sed 's/.*versionName=//;s/ .*//')"
if [[ -n "$INSTALLED_VERSION" ]]; then
  warn "电视上已装 $PKG 版本 $INSTALLED_VERSION"
else
  log "电视上未安装 $PKG"
fi

HAS_ROOT=0
if adb_s shell su -c id 2>/dev/null | grep -q "uid=0"; then
  HAS_ROOT=1
  ok "电视有 root，可以备份并还原应用数据"
fi

BACKUP="/tmp/cozytv-data-backup.tar"
rm -f "$BACKUP"

# ---------------------------------------------------------------- 安装
INSTALL_OUT="$(adb_s install -r "$APK" 2>&1)"
if echo "$INSTALL_OUT" | grep -q "Success"; then
  ok "覆盖安装成功（签名一致，无需卸载）"
else
  warn "覆盖安装失败：$(echo "$INSTALL_OUT" | tr -d '\r' | head -1)"
  if ! echo "$INSTALL_OUT" | grep -q "UPDATE_INCOMPATIBLE"; then
    echo "$INSTALL_OUT" | sed 's/^/  /'
    die "安装失败（不是签名冲突，原因见上）"
  fi

  log "签名不同（v0.3.x 是 debug 签名，v0.4.0 起是正式密钥 CN=CozyTV），必须卸载重装"
  if [[ "$KEEP_DATA" == "1" && "$HAS_ROOT" == "1" ]]; then
    log "备份应用数据 …"
    adb_s shell su -c "tar -cf /data/local/tmp/cozytv-data.tar -C /data/data $PKG" >/dev/null 2>&1
    adb_s pull /data/local/tmp/cozytv-data.tar "$BACKUP" >/dev/null 2>&1
    if [[ -s "$BACKUP" ]]; then
      ok "数据已备份到 $BACKUP"
    else
      warn "备份失败，卸载后数据将丢失"
      BACKUP=""
    fi
  else
    warn "没有 root，无法备份 —— 卸载后收藏/直播源/桌面布局会重置"
  fi

  log "卸载旧版 …"
  adb_s uninstall "$PKG" | sed 's/^/  /'
  log "安装新版 …"
  INSTALL_OUT="$(adb_s install "$APK" 2>&1)"
  echo "$INSTALL_OUT" | grep -q "Success" || { echo "$INSTALL_OUT" | sed 's/^/  /'; die "安装失败"; }
  ok "安装成功"

  if [[ -n "$BACKUP" && -s "$BACKUP" ]]; then
    log "还原应用数据 …"
    adb_s push "$BACKUP" /data/local/tmp/cozytv-data.tar >/dev/null 2>&1
    adb_s shell su -c "tar -xf /data/local/tmp/cozytv-data.tar -C /data/data" >/dev/null 2>&1
    adb_s shell su -c "chown -R \$(stat -c '%u:%g' /data/data/$PKG) /data/data/$PKG" >/dev/null 2>&1
    adb_s shell rm -f /data/local/tmp/cozytv-data.tar >/dev/null 2>&1
    ok "数据已还原（收藏、直播源、桌面布局保留）"
  fi
fi

# ---------------------------------------------------------------- 启动
log "启动 CozyTV"
adb_s shell am force-stop "$PKG" >/dev/null 2>&1
adb_s shell am start -n "$ACT" 2>&1 | sed 's/^/  /'
sleep 4

echo
log "安装结果"
adb_s shell dumpsys package "$PKG" 2>/dev/null | grep -m2 -E "versionName=|versionCode=" | tr -d '\r' | sed 's/^/  /'
echo
log "最近的错误日志（如有）"
adb_s logcat -d -t 200 2>/dev/null | grep -E "FATAL|AndroidRuntime|cozytv" | tail -12 | sed 's/^/  /'

echo
ok "完成。电视上打开 CozyTV 即可；桌面磁贴、直播预览、控制中心都应正常。"
