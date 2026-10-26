# CozyTV 交接文档

> 本文档按 `系统设置/交接文档模板.md` 的 11 节填写，所有数字均为 2026-10-27 在
> macOS 27.2 / JDK 17.0.21 上实测，未使用的章节写明「不适用 + 原因」。

---

## 1. 一句话结论

CozyTV 是一个面向 Android TV / Google TV 的**第三方桌面**（Kotlin + Jetpack Compose +
Media3），把电视的首页换成直播预览 + Apple TV 风格磁贴墙，并内置完整 TVLive 直播客户端；
当前状态 **v0.4.0 已构建、已签名、可安装**，20 类缺陷已修复并补了 10 个回归单测，
**尚未推送 GitHub、尚未发布 v0.4.0，也尚未在真机上跑过修复后的版本**。
使用场景：把闲置的安卓电视/盒子变成能看 IPTV 的桌面（作者自用，README 里的设备是
MiTV-ASTP0，Android 9 / 1920×1080）。

## 2. 交付物与位置

| 交付物 | 路径 | 版本 | 校验 |
|---|---|---|---|
| 安装包（已签名） | `dist/CozyTV-v0.4.0-release.apk` | v0.4.0 (versionCode 9) | SHA-256 `f3be5df9dc7fe00d15bfe68c9cd5efc0d959e0958975f0aa375fb78611d94d11`，3,584,625 字节 |
| 摘要文件 | `dist/CozyTV-v0.4.0-release.apk.sha256` | — | 同上 |
| 签名证书 | `app/signing/cozytv-release.keystore`（不入库） | — | 证书 SHA-256 `0a46b303962959c8b85df33b25c31c1d5ecfd21d4ba6cf5a36ae093d6128a04b`，DN `CN=CozyTV, OU=zdx8, O=zdx8, L=Beijing, ST=Beijing, C=CN` |
| 源码 | GitHub `zdx8/CozyTV`（`main`）· Gitea `ssh://gitea/ZYG/CozyTV.git` | 本地 HEAD `1be6384` + 1 个未推送提交 | — |
| 已发布的线上版本 | GitHub Releases v0.3.0 – v0.3.5 | v0.3.5（2026-10-26 发布，4 次下载） | 资产 `CozyTV-v0.3.5-release.apk` SHA-256 `c1a4cd27aabe1f2c9ccf05d61a041f91abefb823b78274f984d124327103c99a`，3,579,097 字节 |

**已对外发布**：有，GitHub Releases 共 6 个（v0.3.0 起）。
**官网**：无（CozyTV 没有 GitHub Pages，同作者的 TVLive 有 `zdx8.github.io/TVLive/`）。
**注意**：v0.3.x 的线上包是用**本机 Android debug key** 手工签名的（DN `CN=Android
Debug`，证书 SHA-256 `b285093b…`），v0.4.0 起改为上面的正式密钥 —— 见第 5 节「阻断」。

## 3. 怎么跑起来

- **构建环境实测**：macOS 27.2（26B118）、JDK 17.0.21（`<jdk17>`）、
  Android SDK 36（`~/Library/Android/sdk`）、Gradle 8.14.3、AGP 8.12.0、Kotlin 2.2.10、
  compileSdk 36 / targetSdk 36 / minSdk 23、JDK target 17。
- **首次构建**需要联网拉依赖（Compose BOM 2025.11.01、Media3 1.7.0、Room 2.7.0、
  kotlinx-serialization 1.6.3、zxing 3.5.3）；依赖缓存后可以 `--offline`。
- **构建耗时实测**（本机，已缓存）：
  - `./gradlew --offline assembleDebug` 增量 **11 秒**
  - `./gradlew --offline assembleRelease` 增量 **18 秒**
  - `assembleDebug + assembleRelease + testDebugUnitTest + lintDebug` 全量 **47 秒**
  - `tools/release.sh 0.4.0`（含签名校验 + 产物归档）**约 50 秒**
- **命令**：

```bash
cd <repo>
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home"
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY   # 本机有代理时 Gradle 会卡住

./gradlew --offline assembleDebug          # 调试包
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.cozytv/.MainActivity

tools/release.sh 0.4.0                     # 正式签名包 → dist/
TV_HOST=<TV_IP>:5555 tools/test-on-device.sh   # 真机冒烟
```

- **前置条件**：一台 Android TV / 装了 Android TV 固件的盒子；首次使用需在
  「个性化 → 系统 → Home 键守护」开启无障碍服务，否则按 Home 会回到原厂桌面。

## 4. 架构与关键决策

### 模块划分

```
CozyTV/
├── app/                    ← 桌面本体（Compose，applicationId app.cozytv）
│   ├── ui/                 LauncherViewModel（唯一状态源）+ screens/ + components/
│   ├── data/               LauncherRepository / LauncherPreferences / LiveSourcePortal
│   │                       / Weather / BingWallpaper
│   ├── system/             MitvPanel（小米背光）/ DefaultDesktop / XiaomiBrightness
│   ├── guard/              HomeAccessibilityService（只拦 HOME 键）
│   ├── dream/              CozyDream（屏保）
│   └── update/             AppUpdater（GitHub Releases 自更新）
└── tvlive/                 ← 整体 vendored 的 TVLive 2.0.0（library，Apache-2.0）
    ├── player/             PlayerController / PlaybackActivity / ChannelDrawer
    ├── data/               Room 库 itellytv.db + M3U 解析 + 订阅仓库
    └── update/ pairing/    TVLive 自带的更新器与手机配对（内嵌模式下已屏蔽入口）
```

### 数据流

```
遥控器按键 → Compose 焦点系统 → HomeScreen/AppsScreen
                                    │
                     LauncherViewModel（mutableStateOf / derivedStateOf）
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
  LauncherRepository          LivePlaylistRepository        Weather/Bing
  (PackageManager 查询)      (M3U 拉取 → LiveChannel)      (缓存 + 壁纸)
        │                           │
        ▼                           ▼
   磁贴墙 / 全部应用         LivePlayer(ExoPlayer)  ←→  TVLive(PlaybackActivity)
                                                            ↕ SubscriptionStore
                                                           (SharedPreferences hftv_home)
```

### 关键决策

| 决策 | 为什么 | 代价 | 什么条件下推翻 |
|---|---|---|---|
| 整体 vendored TVLive 成 `:tvlive` library，而不是重写直播能力 | TVLive 已是可用的电视端产品（切台、收藏、订阅、诊断），重写要几个月；Apache-2.0 允许 | 库与宿主共用一个进程，`TVLiveEmbedded.active` 这类全局开关必须小心；库自己的 Application（崩溃处理器）不会执行 | 需要 TVLive 独立演进、或要摆脱 XML/View 混合架构时 |
| 用 `TVLiveEmbedded.active` 屏蔽内嵌模式下的 TVLive「检查更新」 | 否则 TVLive 会去拉 `zdx8/TVLive` 的 APK（包名 com.tvlive.tv），给用户装出第二个应用 | 内嵌模式下 TVLive 的 MENU 整个被屏蔽，没有订阅管理入口，只能回 CozyTV 的「直播源」页改 | 宿主自己接管订阅管理后放开 MENU |
| Home 键用**无障碍服务只拦 KEYCODE_HOME**（500ms 节流） | 早期用「前台监听原厂桌面」会把用户主动进入原厂桌面的动作弹回来 | 要求用户手动开无障碍；小米/部分 ROM 上需要 ADB 辅助开启 | ROM 提供官方 HOME 拦截 API 时 |
| 背光通过反射调用小米固件 `TvService`，其他机型回退到应用遮罩 | 小米电视的实体背光只能通过隐藏 API 控制，遮罩方案伤眼 | 隐藏 API 有被 ROM 封禁的风险；探测结果缓存在进程内 | ROM 提供公开背光 API 时 |
| 画布密度固定为 `widthPixels / 960` | 电视分辨率跨度大（720p–4K），按 dp 布局在不同机型上字号差异巨大 | 非 960 宽的设备上字号会按比例缩放 | 需要支持手机/平板形态时 |
| v0.4.0 起用正式 release keystore | v0.3.x 用 debug key 签名，私钥保护等级为零、无法轮换 | **旧用户必须卸载重装**（签名不同无法覆盖安装），桌面布局与收藏会重置 | 用户量显著增长后（那时迁移成本更高） |
| 更新链路做「包名 + versionCode + 版本名 + SHA-256 + 签名证书」五重校验 | APK 自更新是最容易被劫持的环节 | 自建清单必须提供 sha256，否则跳过摘要校验 | — |

## 5. 已完成 / 未完成 / 已知问题

### 已完成

- 桌面主体：首页直播预览、磁贴墙（1×4 / 2×4）、全部应用页、控制中心、个性化、
  设备信息、直播源管理（扫码 / 手输）、信号源、屏保、Home 键守护、开机自启。
- 内嵌 TVLive：频道列表、数字键切台、收藏、订阅管理（由 CozyTV 侧写入 TVLive 仓库）。
- 更新链路：手动检查 GitHub Releases → 下载 → 五重校验 → 调系统安装器。
- 配套工具：`tools/release.sh`（构建+签名+校验+发布）、`tools/test-on-device.sh`
  （真机冒烟）、`tools/home_status.sh`（HOME 只读诊断）、`tools/tvclean.py` +
  `tools/cleanyourtv/`（电脑端清理工具箱）。
- 2026-10-27 修复 20 类缺陷（见变更历史），新增 10 个回归单测。

### 未完成

- **v0.4.0 尚未推送 GitHub / 尚未发布 Release**（本地已构建出签名包，等用户点头）。
- **修复后的版本尚未在真机验证**：电视在 `<TV_IP>`（ADB 网络调试），但
  2026-10-27 本机已在 `192.168.2.x` 网段（路由器/网络变更），到 `192.168.0.x` 无路由，
  `ping` 与 `adb connect` 均不通；`192.168.2.x` 邻居里也没有 5555/5554 开放的设备。
  需要把本机接回同一网段，或确认电视的新 IP 后再跑冒烟脚本。
- 没有 CI（GitHub Actions）：构建、签名、发版全靠 `tools/release.sh` 手工跑。
- 没有 `docs/ARCHITECTURE.md`，架构只存在于本文件第 4 节。

### 已知问题

**阻断**

1. **v0.3.x → v0.4.0 无法覆盖安装**：签名从 debug key 换成正式 key，Android 报
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。用户必须先 `adb uninstall app.cozytv`
   或在系统设置里卸载再装，桌面布局与收藏会重置。应用内自更新会提示
   「更新包签名与当前安装版本不一致，请先卸载旧版再安装」。**在 v0.4.0 发布说明里
   必须写清楚**。
2. **内嵌模式下 TVLive 没有订阅管理入口**：`PlaybackActivity.showMenu()` 在
   `TVLiveEmbedded.active` 时直接 return，MENU 键无反应，只能退回 CozyTV 的
   「直播源」页改源。是产品取舍，不是崩溃，但用户容易当成 bug。

**影响体验**

3. **应用内自更新无法跨密钥升级**（同 1），应用本身没有「卸载并重装」引导流程。
4. **`PlaybackService` 是空壳**：服务自建 ExoPlayer + MediaSession，但真正播放的是
   Activity 里的另一个 ExoPlayer，所以系统媒体面板没有条目、锁屏媒体键无效。
5. **内嵌模式下 TVLive 的崩溃落盘日志不生效**：`tvlive` 的清单没有
   `android:name`，`TvLiveApp` 的全局异常处理器不会被安装（需在宿主补装）。
6. **诊断面板是死代码**：`StreamDiagnoser` / `showDiagnosticFor` 无调用点，
   `onError` 的注释仍写着「长按 MENU 打开诊断面板」，实际 MENU 已被屏蔽。

**边缘**

7. `Weather.kt` 用明文 HTTP 的 `ip-api.com` 做 IP 定位（该服务免费版无 HTTPS），
   同网段被劫持时可能显示错误城市。
8. `LiveSourcePortal` 与 `PairingServer` 在同网段无鉴权（扫码/配对场景可接受），
   已加 Content-Length 与 header 行长度上限防 OOM。
9. `MitvPanel` 的可用性探测在进程内缓存；首次探测时若固件服务未就绪，会缓存
   「不可用」直到进程重启（2026-10-27 已改为读不到值时不缓存负结果，但明确失败
   仍会缓存）。
10. `#EXTVLCOPT:http-proxy=` 能解析但**未实现**（Media3 1.7 没有代理 setter），
    订阅里写代理的台会连不上。
11. `allowBackup=true` 且没有 `dataExtractionRules`：`adb backup` 可导出偏好
    （含直播源 URL）。

## 6. 外部依赖与凭据

| 依赖 | 版本 | 许可 |
|---|---|---|
| androidx.compose（BOM） | 2025.11.01 | Apache-2.0 |
| androidx.media3 | 1.7.0 | Apache-2.0 |
| androidx.room | 2.7.0 | Apache-2.0 |
| kotlinx-serialization-json / coroutines | 1.6.3 / 1.8.1 | Apache-2.0 |
| com.google.zxing:core | 3.5.3 | Apache-2.0 |
| TVLive（vendored 到 `:tvlive`） | 2.0.0-embedded | Apache-2.0（LICENSE/NOTICE 打进 APK `assets/tvlive/`） |
| Aerial TV / CleanYourTV（参考与集成） | — | MIT |

**凭据位置（不写明文）**

- 发布签名：`~/AiWork/Codex/CozyTV/keystore.properties`（600，gitignored）+
  `app/signing/cozytv-release.keystore`；**备份**在 `~/.cozytv/signing/`
  （keystore + `credentials.txt`，均 600）。
- GitHub 凭据：macOS 钥匙串（`gh auth status` 可验证）。
- 电视调试：`adb` 网络调试，未开时用 `TV_HOST=<ip>:5555` 指定。

## 7. 数据与配置在哪

| 内容 | 位置（电视上） |
|---|---|
| 桌面偏好（壁纸、收藏、隐藏应用、直播源列表、亮度、时钟格式） | `SharedPreferences: cozy_launcher` |
| 天气缓存 | `SharedPreferences: cozy_weather` |
| 背光缓存 | `SharedPreferences: cozy_brightness` |
| TVLive 订阅仓库 + 活动源 | `SharedPreferences: hftv_home` |
| TVLive 更新记录 | `SharedPreferences: hftv_update` |
| 频道 / 播放列表数据库 | Room `itellytv.db`（`/data/data/app.cozytv/databases/`） |
| Bing 壁纸图片 + 画廊元数据 | `cache/bing/`（`bing-*.jpg` + `bing-gallery.json`） |
| 下载的更新包 | `cache/cozytv-update.apk`（校验通过后才落名） |

**重置到初始状态**

```bash
TV=<TV_IP>:5555
adb -s $TV shell pm clear app.cozytv      # 清空全部偏好与数据库（恢复出厂）
adb -s $TV uninstall app.cozytv           # 彻底卸载（换签名密钥时用这个）
adb -s $TV shell rm /data/data/app.cozytv/cache/bing/*   # 只清壁纸缓存
```

## 8. 维护手册（出问题先看这里）

| 现象 | 排查步骤 | 日志位置 |
|---|---|---|
| 直播黑屏 / 一直 RECONNECTING | `adb logcat -s TVLive.Player`；看是否出现 `Discarding stale redirect result`（切台竞态）或 `Reconnect skipped`（URL 被清空，2026-10-27 已修） | logcat tag `TVLive.Player` |
| 首页没有声音 | `adb logcat -s CozyTV.LivePlayer`；确认 `HOME_PLAYER_READY` 是否出现；缓冲中按 OK 是否能暂停（2026-10-27 已修为无条件 pause） | tag `CozyTV.LivePlayer` |
| 频道名乱码 | 订阅是 GBK/GB2312 编码。2026-10-27 已改为严格解码，乱码说明文件本身不是文本 | — |
| 自更新提示「更新包校验失败」 | 清单里的 sha256 与下载内容不符；GitHub 资产的 `digest` 字段可用 `gh release view --json assets` 核对 | tag `CozyTV.Update` |
| 自更新提示「签名不一致」 | 电视上装的是 v0.3.x（debug 签名），必须先卸载 | 同上 |
| 更新失败「不是有效 APK」 | 下载被并发任务删掉了临时文件（2026-10-27 已改为每次尝试独立 `.part` 文件） | 同上 |
| 背光调不动 | `adb logcat -s MitvPanel`；看 `backlight transactions unverified` / `backlight read unavailable` | tag `MitvPanel` |
| 按 Home 回不到桌面 | 无障碍服务没开：`adb shell settings put secure enabled_accessibility_services app.cozytv/app.cozytv.guard.HomeAccessibilityService` | — |
| 看不到原厂桌面选项 | `TV_HOST=<ip> tools/home_status.sh` | — |
| 崩溃 | `adb logcat -d -v time > /tmp/cozytv.log`；`tools/test-on-device.sh` 会自动抓并跑 6 个场景断言 | `/tmp/cozytv-test.log` |

## 9. 复现与验证清单

照下面逐条执行，括号里是**预期实测值**。

```bash
cd <repo>
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home"
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
```

1. **单元测试**：`./gradlew --offline testDebugUnitTest`
   → `BUILD SUCCESSFUL`，5 个测试类共 **19 个用例 0 失败**
   （AppOrderTest 3、LivePlaylistTest 3、M3UDecodeTest 4、AppUpdaterTest 3、AppUpdaterVersionTest 6）。
2. **Lint**：`./gradlew --offline lintDebug`
   → app `0 errors, 38 warnings`；`:tvlive` `0 errors, 76 warnings`。
3. **Debug 构建**：`./gradlew --offline assembleDebug`
   → `app/build/outputs/apk/debug/app-debug.apk` 约 13 MB。
4. **Release 构建 + 签名**：`./gradlew --offline assembleRelease -PcozytvVersionCode=9 -PcozytvVersionName=0.4.0`
   → `app-release.apk` **3,584,625 字节**。
5. **签名校验**：
   `~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
   → `DN: CN=CozyTV, OU=zdx8, O=zdx8, L=Beijing, ST=Beijing, C=CN`，
   `SHA-256 digest: 0a46b303962959c8b85df33b25c31c1d5ecfd21d4ba6cf5a36ae093d6128a04b`。
6. **发布脚本**：`GRADLE_FLAGS="--offline" tools/release.sh 0.4.0`
   → 打印 `version 0.4.0 (code 9)`、证书指纹、产物路径与
   `sha256 f3be5df9dc7fe00d15bfe68c9cd5efc0d959e0958975f0aa375fb78611d94d11`、
   `size 3584625 bytes`，末尾提示 `build only — nothing was pushed`。
7. **未签名保护**：`mv keystore.properties /tmp/kp && GRADLE_FLAGS="--offline" tools/release.sh 0.4.0`
   → 脚本在 preflight 阶段直接 `✗ keystore.properties missing`（不会产出无签名包）；
   跑完记得 `mv /tmp/kp keystore.properties`。
8. **真机冒烟**：`TV_HOST=<电视IP>:5555 tools/test-on-device.sh`
   → 安装 → 启动 → 抓 logcat → 6 个场景逐条 PASS/FAIL，最后打印当前 HOME
   `app.cozytv/.MainActivity`。**这一步尚未在真机跑过**（2026-10-27 局域网内没找到
   开启网络调试的设备）。

## 10. 下一步建议

1. **发布 v0.4.0 并写清「需卸载旧版」**（优先级最高）。v0.3.5 只有 4 次下载，
   现在迁移成本最低；越晚发布，迁移的人越多。改动：`tools/release.sh 0.4.0 --publish`
   + Release notes 里写明卸载步骤。
2. **把真机冒烟跑通**。`tools/test-on-device.sh` 已就绪，只差一台开启网络调试的电视；
   跑通后把 PASS/FAIL 结果补进本文件第 9 节。
3. **补 GitHub Actions**：把 `assembleRelease + testDebugUnitTest + lintDebug` 挂到
   PR，签名走 repository secrets（照抄 TVLive 的 `.github/workflows/release-build.yml` 思路）。
   涉及：新增 `.github/workflows/`、`app/build.gradle.kts` 的 signingConfig（已支持从
   secrets 注入，缺 `keystore.properties` 时只是不签名）。
4. **给内嵌模式的 TVLive 补订阅入口**：把「隐藏更新按钮」与「屏蔽整个 MENU」拆开，
   保留订阅管理。涉及 `tvlive/.../PlaybackActivity.kt:598` 与
   `ui/MainActivity.kt:162-206`。
5. **把 `PlaybackService` 要么做实要么摘掉**：现在它是空壳，系统媒体面板没有条目。
   涉及 `tvlive/.../player/PlaybackService.kt` 与 `PlayerController`。

## 11. 变更历史

| 日期 | 版本 | 改了什么 |
|---|---|---|
| 2026-10-25 | 0.3.0–0.3.4 | 首次成型：桌面、磁贴墙、控制中心、内嵌 TVLive、信号源、个性化 |
| 2026-10-26 | 0.3.5 | 控制中心只读显示音量与亮度、开机自启、README 与截图更新 |
| 2026-10-27 | 0.4.0（未发布） | **修复 20 类缺陷**：播放重连失效、缓冲态暂停失效、切台不播、GBK 订阅乱码、订阅 URL 解析崩溃、Android 6 `contentLengthLong` 崩溃、更新校验误判/漏判、首页预览无法恢复、直播源不同步、焦点跳回首项、提示被吞、OOM 与越界风险等；**新增正式签名 + 发布脚本 + 真机测试脚本 + 10 个回归单测** |

---

## 长期记忆（配套动作）

- **项目级**：见本文件 §2（产物与哈希）、§5（已知问题）、§9（验证清单）。
  会话工作区 `.workbuddy/memory/MEMORY.md` 只留一行索引。
- **跨项目可复用的**（已写入用户级记忆）：
  - Gradle `signingConfigs` 块必须写在 `buildTypes` **之前**，否则 release 包不签名；
  - `JSONObject.optString()` 对 JSON null 返回字符串 `"null"`，需要自己过滤；
  - `String(bytes, UTF_8)` 不抛异常，非法字节变 U+FFFD，做编码探测必须用严格解码器；
  - `Player.isPlaying` 在缓冲态为 false，判断“是否在播”要用 `playWhenReady`；
  - macOS 的 `grep` 是 BRE，`\|` 不生效（用 `grep -E`）。
