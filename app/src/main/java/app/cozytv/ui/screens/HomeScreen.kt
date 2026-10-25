package app.cozytv.ui.screens

import android.view.KeyEvent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.TvApp
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.components.AppActionMenu
import app.cozytv.ui.components.AppTile
import app.cozytv.ui.components.BingBackdrop
import app.cozytv.ui.components.ControlCenter
import app.cozytv.ui.components.LivePlayer
import app.cozytv.ui.components.NavChip
import app.cozytv.ui.components.NavGlyph
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The design canvas is 960dp wide; the player column is 650dp after the 174dp
// favourites rail and 28dp gap, so this keeps the player at exactly 16:9.
private val HomePlayerHeight = 365.625.dp

private data class PreviewApp(
    val label: String,
    val badge: String,
    val start: Color,
    val end: Color,
)

private val PreviewApps = listOf(
    PreviewApp("芒果TV", "芒果", Color(0xFFFFA53D), Color(0xFFF05A28)),
    PreviewApp("优酷", "优酷", Color(0xFF5AC8FA), Color(0xFF2F6BFF)),
    PreviewApp("哔哩哔哩", "B站", Color(0xFF7BD8FF), Color(0xFFFF7AAE)),
    PreviewApp("云视听", "云视听", Color(0xFF63B3FF), Color(0xFF2C6BFF)),
    PreviewApp("央视频", "央视", Color(0xFFFF7A6B), Color(0xFFE02D45)),
    PreviewApp("网易云音乐", "网易云", Color(0xFFFF6B6B), Color(0xFFC31F37)),
    PreviewApp("QQ音乐", "QQ", Color(0xFF6CE3A8), Color(0xFF0FAE6B)),
    PreviewApp("小米应用市场", "小米", Color(0xFFFFB347), Color(0xFFFF6A00)),
)

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val homeApps = vm.homeApps
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(app: TvApp) = requesters.getOrPut(app.key) { FocusRequester() }
    val appsRequester = remember { FocusRequester() }
    val tvLiveRequester = remember { FocusRequester() }
    val controlRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val playerRequester = remember { FocusRequester() }
    val now = rememberClock()
    var menuApp by remember { mutableStateOf<TvApp?>(null) }
    var playerFocused by remember { mutableStateOf(false) }
    var lastActivityAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumed = true
                lastActivityAt = SystemClock.elapsedRealtime()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        resumed,
        lastActivityAt,
        vm.loading,
        menuApp,
        vm.controlCenterOpen,
    ) {
        if (!resumed || vm.loading || menuApp != null || vm.controlCenterOpen) {
            return@LaunchedEffect
        }
        delay(20_000)
        vm.openTvLive()
    }

    LaunchedEffect(vm.loading, homeApps.map(TvApp::key)) {
        if (vm.loading) return@LaunchedEffect
        delay(120)
        homeApps.firstOrNull()?.let { runCatching { requester(it).requestFocus() } }
            ?: runCatching { appsRequester.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                lastActivityAt = SystemClock.elapsedRealtime()
                // Keep the firmware menu available on the home screen.
                false
            },
    ) {
        BingBackdrop(
            wallpaper = vm.bingWallpaper,
            fallbackStyle = vm.wallpaper,
        )
        HomeTopBar(
            now = now,
            clock24Hour = vm.clock24Hour,
            onTvLive = vm::openTvLive,
            onControl = vm::openControlCenter,
            onSettings = vm::openSystemSettings,
            tvLiveRequester = tvLiveRequester,
            controlRequester = controlRequester,
            settingsRequester = settingsRequester,
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 54.dp, end = 54.dp, top = 70.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Spacer(Modifier.height(28.dp))
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(HomePlayerHeight)
                        .focusRequester(playerRequester)
                        .onFocusChanged { playerFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            val select = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                            if (select && event.type == KeyEventType.KeyUp) {
                                vm.openTvLive()
                                true
                            } else {
                                select
                            }
                        }
                        .focusable()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    playerRequester.requestFocus()
                                    vm.openTvLive()
                                },
                            )
                        }
                        .border(
                            if (playerFocused) 2.dp else 0.dp,
                            Color.White,
                            RoundedCornerShape(18.dp),
                        ),
                ) {
                    LivePlayer(
                        channel = vm.liveChannel,
                        modifier = Modifier.fillMaxSize(),
                        attempt = vm.liveAttempt,
                        onPlaybackError = vm::onLivePlaybackError,
                    )
                }
                Spacer(Modifier.weight(1f))
                val weather = vm.weather
                BasicText(
                    weather?.let {
                        "${it.city} ${it.minTempC}°~${it.maxTempC}° ${it.description} · ${it.advice}"
                    } ?: "正在获取天气…",
                    style = CozyType.Body.copy(
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.72f),
                    ),
                    maxLines = 1,
                )
            }
            Column(
                Modifier
                    .width(174.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                Spacer(Modifier.height(12.dp))
                if (vm.loading) {
                    AppListSkeleton(grid = vm.favoritesGrid)
                } else if (vm.favoritesGrid) {
                    val realApps = homeApps.take(8)
                    val previewApps = PreviewApps.take((8 - realApps.size).coerceAtLeast(0))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HomePlayerHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        repeat(4) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(2) { column ->
                                    val index = row * 2 + column
                                    val app = realApps.getOrNull(index)
                                    if (app != null) {
                                        AppTile(
                                            app = app,
                                            width = 80.dp,
                                            height = 85.dp,
                                            showNames = vm.showNames,
                                            useBanner = false,
                                            androidIcon = true,
                                            focusRequester = requester(app),
                                            onClick = { vm.launch(app) },
                                            onLongClick = { menuApp = app },
                                        )
                                    } else {
                                        val preview = previewApps.getOrNull(index - realApps.size)
                                        if (preview != null) {
                                            PreviewAppTile(preview)
                                        } else {
                                            Spacer(Modifier.width(80.dp).height(85.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HomePlayerHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            7.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        homeApps.take(4).forEach { app ->
                            AppTile(
                                app = app,
                                width = 150.dp,
                                height = 86.dp,
                                showNames = vm.showNames,
                                useBanner = true,
                                focusRequester = requester(app),
                                onClick = { vm.launch(app) },
                                onLongClick = { menuApp = app },
                            )
                        }
                        if (homeApps.isEmpty()) {
                            EmptyFavorites()
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                AllAppsTile(
                    count = vm.apps.size,
                    focusRequester = appsRequester,
                    onClick = vm::showApps,
                )
            }
        }
        val effectiveBrightness = if (vm.brightness < 0f) 0.8f else vm.brightness
        val dimAlpha = ((1f - effectiveBrightness) * 0.6f).coerceIn(0f, 0.55f)
        if (!vm.brightnessHardware && dimAlpha > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }
        AnimatedVisibility(
            visible = vm.notice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            vm.notice?.let {
                LaunchedEffect(it) {
                    delay(1800)
                    vm.dismissNotice()
                }
                Box(
                    Modifier
                        .padding(top = 20.dp)
                        .background(Color(0xDD11162B), RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    BasicText(it, style = CozyType.Label)
                }
            }
        }
        menuApp?.let { app ->
            AppActionMenu(
                vm = vm,
                app = app,
                onDismiss = { menuApp = null },
            )
        }
        if (vm.controlCenterOpen) {
            ControlCenter(vm)
        }
    }
}

@Composable
private fun HomeTopBar(
    now: Date,
    clock24Hour: Boolean,
    onTvLive: () -> Unit,
    onControl: () -> Unit,
    onSettings: () -> Unit,
    tvLiveRequester: FocusRequester,
    controlRequester: FocusRequester,
    settingsRequester: FocusRequester,
) {
    val time = remember(now, clock24Hour) {
        SimpleDateFormat(if (clock24Hour) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    }
    val date = remember(now) {
        SimpleDateFormat("M月d日  EEEE", Locale.SIMPLIFIED_CHINESE).format(now)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 54.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            time,
            style = CozyType.Clock.copy(
                fontSize = 42.sp,
                letterSpacing = (-0.8).sp,
            ),
        )
        Spacer(Modifier.width(13.dp))
        BasicText(
            date,
            style = CozyType.Body.copy(
                color = CozyColors.Paper.copy(alpha = 0.72f),
                fontSize = 16.sp,
            ),
        )
        Spacer(Modifier.weight(1f))
        NavChip(
            label = "TVLive",
            glyph = NavGlyph.Play,
            focusRequester = tvLiveRequester,
            onClick = onTvLive,
        )
        Spacer(Modifier.width(9.dp))
        NavChip(
            label = "控制中心",
            glyph = NavGlyph.Control,
            focusRequester = controlRequester,
            onClick = onControl,
        )
        Spacer(Modifier.width(9.dp))
        NavChip(
            label = "设置",
            glyph = NavGlyph.Settings,
            focusRequester = settingsRequester,
            onClick = onSettings,
        )
    }
}

@Composable
private fun AllAppsTile(
    count: Int,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(150.dp)
            .height(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val select = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                if (select && event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else {
                    select
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusRequester.requestFocus()
                    onClick()
                })
            }
            .background(
                if (focused) Color.White else Color.Black.copy(alpha = 0.28f),
                RoundedCornerShape(13.dp),
            )
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(13.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppGridIcon()
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "全部应用",
                    style = CozyType.Caption.copy(
                        fontSize = 10.sp,
                        color = if (focused) CozyColors.Ink else Color.White,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                BasicText(
                    count.toString(),
                    style = CozyType.Caption.copy(
                        fontSize = 8.sp,
                        color = if (focused) CozyColors.Ink.copy(alpha = 0.6f)
                        else Color.White.copy(alpha = 0.55f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun AppGridIcon() {
    val colors = listOf(
        Color(0xFFFF6B6B),
        Color(0xFFFFB84D),
        Color(0xFF4ECB83),
        Color(0xFF5A9BFF),
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { column ->
                    Box(
                        Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(
                                colors[row * 2 + column],
                                RoundedCornerShape(2.5.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewAppTile(app: PreviewApp) {
    Box(
        Modifier
            .width(80.dp)
            .height(85.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xC7151B2D))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(app.start, app.end))),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    app.badge,
                    style = CozyType.Title.copy(
                        fontSize = if (app.badge.length > 2) 9.sp else 11.sp,
                        color = Color.White,
                    ),
                )
            }
            Spacer(Modifier.height(3.dp))
            BasicText(
                app.label,
                style = CozyType.Caption.copy(
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.82f),
                ),
                maxLines = 1,
            )
        }
        BasicText(
            "示例",
            style = CozyType.Caption.copy(
                fontSize = 6.sp,
                color = Color.White.copy(alpha = 0.48f),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp),
        )
    }
}

@Composable
private fun EmptyFavorites() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(
                Color.Black.copy(alpha = 0.24f),
                RoundedCornerShape(13.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("还没有首页应用", style = CozyType.Caption)
    }
}

@Composable
private fun AppListSkeleton(grid: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(HomePlayerHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (grid) 8.dp else 7.dp,
            Alignment.CenterVertically,
        ),
    ) {
        if (grid) {
            repeat(4) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) {
                        Box(
                            Modifier
                                .width(80.dp)
                                .height(85.dp)
                                .background(
                                    Color.White.copy(alpha = 0.07f),
                                    RoundedCornerShape(13.dp),
                                ),
                        )
                    }
                }
            }
        } else {
            repeat(4) {
                Box(
                    Modifier
                        .width(150.dp)
                        .height(86.dp)
                        .background(
                            Color.White.copy(alpha = 0.07f),
                            RoundedCornerShape(13.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun rememberClock(): Date {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    return now
}
