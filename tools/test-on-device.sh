#!/usr/bin/env bash
#
# CozyTV 真机 / 模拟器冒烟测试。
#
# 跑前：
#   1. 电视开启「开发者选项 → USB 调试 / 网络调试」
#   2. 网络调试：TV_HOST=192.168.x.x:5555（默认用本机已连的设备）
#   3. USB：直接插上，adb devices 能看到
#
# 它会自动：
#   - 安装 APK（默认 release 产物，可 --debug 换成 debug 包）
#   - 启动 CozyTV
#   - 抓 logcat
#   - 检查 6 个场景并报告 PASS/FAIL
#
# 用法：
#   tools/test-on-device.sh                 # 用 dist/ 或 release 产物
#   tools/test-on-device.sh --debug         # 用 debug 包
#   TV_HOST=<TV_IP>:5555 tools/test-on-device.sh
#
set -uo pipefail

cd "$(dirname "$0")/.."

export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

PKG="app.cozytv"
ACT="$PKG/.MainActivity"
USE_DEBUG=0
TV_HOST="${TV_HOST:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug) USE_DEBUG=1; shift ;;
    --host)  TV_HOST="$2"; shift 2 ;;
    -h|--help) sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

adb_cmd() {
  if [[ -n "$TV_HOST" ]]; then
    adb -s "$TV_HOST" "$@"
  else
    adb "$@"
  fi
}

# ---------- 设备 ----------
if [[ -n "$TV_HOST" ]]; then
  echo "==> connecting to $TV_HOST …"
  adb connect "$TV_HOST" >/dev/null 2>&1 || true
fi

if ! adb devices | grep -q "device$"; then
  echo "✗ 没有可用的 adb 设备（插上电视或设置 TV_HOST=ip:5555）"
  exit 1
fi
DEVICE="$(adb devices | awk '$2=="device"{print $1; exit}')"
echo "==> device: $DEVICE"

# ---------- APK ----------
if [[ "$USE_DEBUG" == "1" ]]; then
  APK="app/build/outputs/apk/debug/app-debug.apk"
else
  APK="$(ls -1 dist/CozyTV-v*-release.apk 2>/dev/null | sort -V | tail -1)"
  [[ -n "$APK" ]] || APK="app/build/outputs/apk/release/app-release.apk"
fi
if [[ ! -f "$APK" ]]; then
  echo "==> 没有可用 APK，先跑 tools/release.sh <version> 或 ./gradlew assembleDebug"
  exit 1
fi
echo "==> APK: $APK ($(stat -f%z "$APK") bytes)"

echo "==> 1. 安装 …"
adb_cmd install -r "$APK" || {
  echo "✗ 安装失败。若提示签名冲突（INSTALL_FAILED_UPDATE_INCOMPATIBLE），"
  echo "  说明电视上装的是旧 debug 签名版本：先 adb uninstall $PKG 再装。"
  exit 1
}

echo "==> 2. 清空 logcat 并启动 …"
adb_cmd logcat -c
adb_cmd shell am force-stop "$PKG"
sleep 1
adb_cmd shell am start -n "$ACT"

sleep 6
LOG=/tmp/cozytv-test.log
adb_cmd logcat -d -v time > "$LOG"
echo "==> logcat: $LOG ($(wc -l < "$LOG") 行)"

PASS=0
FAIL=0
check() {
  local name="$1"; local pattern="$2"
  if grep -qE "$pattern" "$LOG"; then
    echo "  ✓ $name"; PASS=$((PASS+1))
  else
    echo "  ✗ $name"; FAIL=$((FAIL+1))
  fi
}
check_absent() {
  local name="$1"; local pattern="$2"
  if grep -qE "$pattern" "$LOG"; then
    echo "  ✗ $name"; FAIL=$((FAIL+1))
  else
    echo "  ✓ $name"; PASS=$((PASS+1))
  fi
}

echo
echo "=========================================="
echo "  场景 1：启动与崩溃"
echo "=========================================="
check_absent "无 FATAL EXCEPTION"   '^.{0,40}FATAL EXCEPTION'
check_absent "无 AndroidRuntime 错误" '^.{0,40}AndroidRuntime:'
check_absent "无 ANR"                'ANR in app\.cozytv'
check        "MainActivity 已启动"    'Starting:.*app\.cozytv'

echo
echo "=========================================="
echo "  场景 2：桌面与 HOME"
echo "=========================================="
check "绘制了首页"          'CozyTV|LauncherViewModel|HomeScreen'
check "扫描应用"            'LauncherRepository|TvApp'
check "壁纸/天气请求"       'Bing|Weather|open-meteo|wttr'

echo
echo "=========================================="
echo "  场景 3：首页直播预览"
echo "=========================================="
check "播放器已就绪"        'HOME_PLAYER_READY'
check "直播源加载"          'LivePlaylist|CozyTV\.Live'
check_absent "播放无异常"   'FATAL EXCEPTION.*Player'

echo
echo "=========================================="
echo "  场景 4：TVLive 内嵌模块"
echo "=========================================="
check "TVLive 已启动"       'TVLive\.|com\.tvlive\.tv'
check_absent "TVLive 无崩溃" 'FATAL EXCEPTION.*tvlive'

echo
echo "=========================================="
echo "  场景 5：背光与系统桥接"
echo "=========================================="
check "背光探测"            'backlight|MitvPanel|XiaomiBrightness'

echo
echo "=========================================="
echo "  场景 6：系统状态"
echo "=========================================="
echo "  当前 HOME："
adb_cmd shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME |
  tr -d '\r' | tail -n 1 | sed 's/^/    /'

echo
echo "  结果：$PASS 通过 / $FAIL 失败"
echo
echo "接下来人工确认（用电视遥控器）："
echo "  1. 首页应自动播放直播预览"
echo "  2. 按 20 秒不动应自动进入 TVLive 全屏"
echo "  3. 切台后画面/声音应正常；暂停后再切台，新频道应立即出声"
echo "  4. 按 HOME 回到桌面，再进入应用，直播预览应自动恢复播放"
echo
echo "  截图：adb_cmd shell screencap -p /sdcard/screen.png"
echo "        adb_cmd pull /sdcard/screen.png ~/Desktop/cozytv-screen.png"

exit $FAIL
