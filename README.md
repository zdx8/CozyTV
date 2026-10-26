# CozyTV

面向 Android TV / Google TV 的轻量桌面，使用 Kotlin、Jetpack Compose 和 Media3，
针对遥控器与低配置电视优化。

![CozyTV 首页](docs/cozytv-home.jpg)

## 主要功能

- 16:9 首页直播预览，进入桌面自动播放，支持多个 M3U / M3U8 源自动切换。
- 信号源菜单包含 TVLive、HDMI、AV 和电视输入；TVLive 在首页播放，其他输入直接全屏打开。
- 信号源可保存为默认项；冷启动 CozyTV 时会自动打开默认的全屏输入。
- 内置完整 TVLive，支持频道列表、数字键切台、收藏和频道抽屉。
- Apple TV 风格首页应用磁贴，支持 1×4 与 2×4 两种布局。
- 首页应用长按 OK 可收藏、隐藏或卸载；全部应用页同样支持这些操作。
- 首页菜单键保留给电视固件，可正常弹出原厂菜单。
- 首页 20 秒无按键操作后自动打开 TVLive 全屏直播。
- Bing 每日三张 UHD 壁纸缩略图，可选择首页壁纸；屏保每 15 秒自动轮播。
- 应用图标采用居中的环形标志，不使用额外三角装饰。
- 控制中心包含网络、系统设置和原厂桌面，并只读显示系统音量与亮度。
- 小米电视通过固件 `TvService` 控制实体背光，其他设备回退到应用遮罩。
- Home 键无障碍守护只拦截 Home 键；主动进入原厂桌面时不会被自动拉回。
- 电视开机完成后自动进入 CozyTV，不主动打开原厂桌面。
- 手动检查 GitHub Releases，下载后校验 SHA-256、包名、版本和签名。
- 设备信息检查页只读显示系统、屏幕、内存、存储、网络和运行状态。

## 界面

![控制中心](docs/cozytv-control-center-live.jpg)

![全部应用](docs/cozytv-apps.jpg)

![个性化](docs/cozytv-settings.jpg)

## 默认桌面

控制中心中的“原厂桌面”会直接打开原厂桌面：

- 普通入口只打开原厂桌面，不修改系统默认设置，也不会自动返回。
- 在原厂桌面按 Home 会返回 CozyTV。
- 个性化页“系统 → 恢复原厂桌面”同样只打开原厂桌面。

首次使用需在“个性化 → 系统 → Home 键守护”开启无障碍服务：

```bash
adb shell settings put secure enabled_accessibility_services \
  app.cozytv/app.cozytv.guard.HomeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

## CleanYourTV 清理工具箱

[CleanYourTV](https://github.com/Gathin23/cleanyourtv) 是一套独立于 CozyTV App
的电视清理能力，通过电脑端 ADB 辅助使用，不会增加应用内的清理权限或后台行为。

- 可识别电视型号，并匹配对应的机型应用清单。
- 可分类处理广告、遥测数据及可选预装应用。
- 执行前可预览待处理项目，执行后保留恢复记录。
- 默认避开 HOME、HDMI / AV / 电视输入、Wi-Fi 设置向导和核心系统组件。

## 软件更新

“个性化 → 软件更新”手动检查 GitHub Releases。发布附件命名为
`CozyTV-vX.Y.Z-release.apk`，并且必须与已安装版本使用相同签名。

```bash
./gradlew assembleRelease \
  -PcozytvVersionCode=8 \
  -PcozytvVersionName=0.3.5
```

更新地址可通过 `-PcozytvUpdateUrl=<GitHub API 或静态清单>` 覆盖。

### 从 v0.4.0 起：正式签名

v0.3.x 的 Release 是用本机 Android debug key 手工签名的（发布身份不是
`CN=CozyTV`）。从 **v0.4.0** 起改用正式密钥：

- 签名者：`CN=CozyTV, OU=zdx8, O=zdx8, L=Beijing, ST=Beijing, C=CN`
- 证书 SHA-256：`0a46b303962959c8b85df33b25c31c1d5ecfd21d4ba6cf5a36ae093d6128a04b`
- 密钥库：`app/signing/cozytv-release.keystore`（**不入库**，见
  `keystore.properties.example`）

**升级影响**：签名变了，Android 不允许覆盖安装。旧版用户必须先卸载
`app.cozytv` 再装新版，桌面布局与收藏会重置。应用内的自更新在检测到
签名不一致时也会提示先卸载旧版。

### 发布

```bash
tools/release.sh 0.4.0              # 构建 + 签名 + 校验签名 + 产出 dist/
tools/release.sh 0.4.0 --publish    # 再打 tag、推送、建 GitHub Release
```

脚本会自己升 `app/build.gradle.kts` 里的版本号、校验签名（**拒绝**
未签名或 debug 签名的产物）、把 APK 放到 `dist/` 并生成 `.sha256`。
`--publish` 之前会要求确认。

## 构建

需要 JDK 17 和 Android SDK 36。

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.cozytv/.MainActivity
```

Release APK：`app/build/outputs/apk/release/app-release.apk`
（配置了 `keystore.properties` 时为已签名产物），发布产物在 `dist/`。

真机冒烟测试：

```bash
TV_HOST=<TV_IP>:5555 tools/test-on-device.sh
```

### 当前版本

- 最新发布：**v0.4.0**（2026-10-27）—— <https://github.com/zdx8/CozyTV/releases/tag/v0.4.0>
- 资产 `CozyTV-v0.4.0-release.apk`，3,584,636 字节，
  SHA-256 `ad98c12542d4cd6a11c569928f794aa66d4ccc09ab74314b6f005ab5d260088a`
- 签名：`CN=CozyTV, OU=zdx8, O=zdx8, L=Beijing, ST=Beijing, C=CN`，
  证书 SHA-256 `0a46b303962959c8b85df33b25c31c1d5ecfd21d4ba6cf5a36ae093d6128a04b`
- 校验线上文件：`gh release view v0.4.0 -R zdx8/CozyTV --json assets`

## 验证

已在 MiTV-ASTP0（Android 9，1920×1080）和 Android TV API 33 模拟器验证：

- Amlogic HEVC 直播硬解与多源切换
- 控制中心、实体背光 100→95→100
- Home 键守护与原厂桌面入口
- 首页菜单键透传与 20 秒无操作自动进入 TVLive
- 开机自动进入 CozyTV
- 控制中心只读显示系统音量与亮度
- TVLive 首页播放及 HDMI / AV / 电视输入全屏切换
- 从个性化、全部应用等内部页面按 Home 均会回到 CozyTV 首页
- 全部应用、设备信息和更新流程
- Debug、Release、单元测试与 Android Lint

2026-10-27 修复了 20 类缺陷（播放重连失效、缓冲态暂停失效、切台后不播、
GBK 订阅乱码、更新校验误判等），新增 10 个回归单测，详见
[`HANDOVER.md`](HANDOVER.md)。修复后共 19 个单元测试全绿，
app 与 `:tvlive` 两个模块的 Lint 均为 0 error。

TVLive 为 Apache-2.0；Aerial TV 与 CleanYourTV 为 MIT。来源说明见
[`docs/REFERENCE_NOTES.md`](docs/REFERENCE_NOTES.md)。
