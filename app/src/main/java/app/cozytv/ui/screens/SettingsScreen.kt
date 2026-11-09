package app.cozytv.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.BingWallpaper
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.components.ActionRow
import app.cozytv.ui.components.BingBackdrop
import app.cozytv.ui.components.ToggleRow
import app.cozytv.ui.theme.CozyType
import app.cozytv.update.AppUpdateState
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    BackHandler(onBack = vm::showHome)
    val liveSourceRequester = remember { FocusRequester() }
    val dreamRequester = remember { FocusRequester() }
    val refreshBingRequester = remember { FocusRequester() }
    val showHiddenRequester = remember { FocusRequester() }
    val namesRequester = remember { FocusRequester() }
    val favoritesGridRequester = remember { FocusRequester() }
    val clockRequester = remember { FocusRequester() }
    val rescanRequester = remember { FocusRequester() }
    val homeGuardRequester = remember { FocusRequester() }
    val restoreStockRequester = remember { FocusRequester() }
    val deviceInfoRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }
    val wallpaperRequesters = remember { List(3) { FocusRequester() } }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { liveSourceRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        BingBackdrop(vm.bingWallpaper, vm.wallpaper, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f)),
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, top = 22.dp, end = 54.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    BasicText(
                        "个性化",
                        style = CozyType.Hero.copy(fontSize = 30.sp),
                    )
                    BasicText(
                        "直播、外观与系统设置",
                        style = CozyType.Caption.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.56f),
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                BasicText(
                    "CozyTV ${vm.appVersionName}",
                    style = CozyType.Caption.copy(
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.48f),
                    ),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 54.dp, vertical = 2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    Column(Modifier.width(418.dp)) {
                    SettingsHeading("壁纸", "三张精选 · 屏保全屏轮播")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        val gallery = vm.bingWallpaperGallery.take(3)
                        if (gallery.isEmpty()) {
                            repeat(3) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(88.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.06f),
                                            RoundedCornerShape(13.dp),
                                        )
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.10f),
                                            RoundedCornerShape(13.dp),
                                        ),
                                )
                            }
                        } else {
                            gallery.forEachIndexed { index, wallpaper ->
                                WallpaperThumbnail(
                                    wallpaper = wallpaper,
                                    selected = wallpaper.date == vm.bingWallpaper?.date,
                                    focusRequester = wallpaperRequesters[index],
                                    onClick = { vm.selectBingWallpaper(wallpaper) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - gallery.size) {
                                Spacer(Modifier.weight(1f).height(88.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ActionRow(
                            title = "直播源",
                            detail = when {
                                vm.liveSourceUrls.isEmpty() -> "内置演示源"
                                vm.liveSourceUrls.size == 1 -> "1 个自定义源"
                                else -> "${vm.liveSourceUrls.size} 个直播源"
                            },
                            focusRequester = liveSourceRequester,
                            onClick = vm::showLiveSource,
                            modifier = Modifier.weight(1f),
                        )
                        ActionRow(
                            title = "屏保设置",
                            detail = "三张壁纸自动轮播",
                            focusRequester = dreamRequester,
                            onClick = vm::openScreensaverSettings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    ActionRow(
                        title = "换一组 Bing 壁纸",
                        detail = "加载另外三张 Bing UHD 精选",
                        focusRequester = refreshBingRequester,
                        onClick = vm::rotateBingWallpaper,
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = "显示已隐藏应用",
                        detail = "在全部应用中显示，可从应用菜单取消隐藏",
                        checked = vm.showHiddenApps,
                        focusRequester = showHiddenRequester,
                        onToggle = { vm.updateShowHiddenApps(!vm.showHiddenApps) },
                    )
                }
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(490.dp)
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                    Column(Modifier.weight(1f)) {
                    SettingsHeading("时间与布局", "针对遥控器优化")
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = "显示应用名称",
                        detail = "在右侧首页应用磁贴上保留名称",
                        checked = vm.showNames,
                        focusRequester = namesRequester,
                        onToggle = { vm.updateShowNames(!vm.showNames) },
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = "首页应用 2×4 图标",
                        detail = "2 列 × 4 行 Android 图标；不足 8 个用示例图标预览",
                        checked = vm.favoritesGrid,
                        focusRequester = favoritesGridRequester,
                        onToggle = { vm.updateFavoritesGrid(!vm.favoritesGrid) },
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = "24 小时制",
                        detail = "关闭后在时间后显示 12 小时制",
                        checked = vm.clock24Hour,
                        focusRequester = clockRequester,
                        onToggle = { vm.updateClock24Hour(!vm.clock24Hour) },
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingsHeading("系统", "需要时可从这里进入")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ActionRow(
                            title = "重新扫描应用",
                            detail = "刷新电视应用",
                            focusRequester = rescanRequester,
                            onClick = vm::refresh,
                            modifier = Modifier.weight(1f),
                        )
                        ActionRow(
                            title = "Home 键守护",
                            detail = if (vm.homeGuardEnabled) {
                                "已启用 · 仅按 Home 返回"
                            } else {
                                "未启用 · 点击开启"
                            },
                            focusRequester = homeGuardRequester,
                            onClick = vm::openHomeGuardSettings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    ActionRow(
                        title = "恢复原厂桌面",
                        detail = "主动进入时保持原厂桌面，不自动返回",
                        focusRequester = restoreStockRequester,
                        onClick = vm::restoreStockDesktop,
                    )
                    Spacer(Modifier.height(7.dp))
                    ActionRow(
                        title = "设备信息检查",
                        detail = "查看系统、内存、存储、屏幕与运行状态",
                        focusRequester = deviceInfoRequester,
                        onClick = vm::showDeviceInfo,
                    )
                    Spacer(Modifier.height(9.dp))
                    SettingsHeading("更新", "手动检查，不后台打扰")
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        title = "软件更新",
                        detail = when (val update = vm.updateState) {
                            AppUpdateState.Idle ->
                                "当前 v${vm.appVersionName} · 点击手动检查"
                            AppUpdateState.Checking -> "正在检查更新…"
                            is AppUpdateState.UpToDate ->
                                "已是最新版本 v${update.remoteVersion}"
                            is AppUpdateState.Available ->
                                "发现新版本 v${update.info.versionName}"
                            is AppUpdateState.Downloading ->
                                "正在下载 ${update.percent}%"
                            is AppUpdateState.Ready -> "安装包已下载，等待系统安装"
                            is AppUpdateState.Failed -> "更新失败 · ${update.message}"
                        },
                        focusRequester = updateRequester,
                        onClick = vm::checkForUpdates,
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeading(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        BasicText(title, style = CozyType.Title.copy(fontSize = 18.sp))
        Spacer(Modifier.width(10.dp))
        BasicText(
            subtitle,
            style = CozyType.Caption,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun WallpaperThumbnail(
    wallpaper: BingWallpaper,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val image = remember(wallpaper.image) { wallpaper.image.asImageBitmap() }
    Box(
        modifier
            .height(88.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val select = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                if (select && event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    select
                }
            }
            .focusable()
            .pointerInput(wallpaper.date) {
                detectTapGestures(
                    onTap = {
                        focusRequester.requestFocus()
                        onClick()
                    },
                )
            }
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White
                else if (selected) Color(0xFF8B9CFF)
                else Color.White.copy(alpha = 0.13f),
                RoundedCornerShape(13.dp),
            ),
    ) {
        Image(
            bitmap = image,
            contentDescription = wallpaper.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            BasicText(
                wallpaper.title,
                style = CozyType.Caption.copy(fontSize = 8.sp),
                maxLines = 1,
            )
            if (selected) {
                BasicText(
                    "使用中",
                    style = CozyType.Caption.copy(
                        fontSize = 7.sp,
                        color = Color(0xFFB9C4FF),
                    ),
                )
            }
        }
    }
}
