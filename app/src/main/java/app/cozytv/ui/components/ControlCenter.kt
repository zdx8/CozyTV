package app.cozytv.ui.components

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.R
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class NetworkSummary(val label: String, val online: Boolean)

@Composable
fun ControlCenter(vm: LauncherViewModel) {
    BackHandler(onBack = vm::closeControlCenter)
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) } ?: return
    var volume by remember {
        mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    val volumeMax = remember(audio) {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    val volumePercent = (volume * 100f / volumeMax).roundToInt()
    val brightnessPercent = if (vm.brightness < 0f) 80
    else (vm.brightness * 100f).roundToInt()
    val network = remember(context) { readNetwork(context) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    LaunchedEffect(audio) {
        while (true) {
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            vm.refreshSystemBrightness()
            delay(1_000)
        }
    }
    val time = remember(now, vm.clock24Hour) {
        SimpleDateFormat(
            if (vm.clock24Hour) "HH:mm" else "h:mm",
            Locale.getDefault(),
        ).format(now)
    }
    val date = remember(now) {
        SimpleDateFormat("M月d日  EEEE", Locale.SIMPLIFIED_CHINESE).format(now)
    }
    val sourceRequester = remember { FocusRequester() }
    val networkRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val personalizeRequester = remember { FocusRequester() }
    val desktopRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { sourceRequester.requestFocus() }
    }

    fun openNetwork() {
        vm.closeControlCenter()
        vm.openWifiSettings()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { vm.closeControlCenter() })
            },
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 54.dp, top = 70.dp)
                .width(380.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF02B4166), Color(0xF5131728)),
                    ),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(28.dp),
                )
                .padding(22.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        time,
                        style = CozyType.Clock.copy(
                            fontSize = 36.sp,
                            letterSpacing = (-0.8).sp,
                        ),
                    )
                    BasicText(
                        date,
                        style = CozyType.Caption.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.66f),
                        ),
                    )
                }
                    Row(
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { openNetwork() })
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                if (network.online) Color(0xFF56E39F)
                                else Color(0xFFFF8A80),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    ControlIcon(
                        iconRes = R.drawable.ic_cc_wifi,
                        color = Color.White.copy(alpha = 0.82f),
                        size = 14.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    BasicText(
                        network.label,
                        style = CozyType.Caption.copy(fontSize = 10.sp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircleAction(
                    label = "信号源",
                    iconRes = R.drawable.ic_cc_input,
                    tint = Color(0xFF4D8DFF),
                    focusRequester = sourceRequester,
                    modifier = Modifier.weight(1f),
                    onLeft = { desktopRequester.requestFocus() },
                    onRight = { networkRequester.requestFocus() },
                    onDown = { personalizeRequester.requestFocus() },
                    onClick = {
                        vm.closeControlCenter()
                        vm.showInputs()
                    },
                )
                CircleAction(
                    label = "WiFi",
                    iconRes = R.drawable.ic_cc_wifi,
                    tint = Color(0xFF38D39F),
                    focusRequester = networkRequester,
                    modifier = Modifier.weight(1f),
                    onLeft = { sourceRequester.requestFocus() },
                    onRight = { settingsRequester.requestFocus() },
                    onDown = { personalizeRequester.requestFocus() },
                    onClick = { openNetwork() },
                )
                CircleAction(
                    label = "设置",
                    iconRes = R.drawable.ic_cc_settings,
                    tint = Color(0xFF9AA4B8),
                    focusRequester = settingsRequester,
                    modifier = Modifier.weight(1f),
                    onLeft = { networkRequester.requestFocus() },
                    onRight = { desktopRequester.requestFocus() },
                    onDown = { personalizeRequester.requestFocus() },
                    onClick = {
                        vm.closeControlCenter()
                        vm.openSystemSettings()
                    },
                )
                CircleAction(
                    label = "原厂桌面",
                    iconRes = R.drawable.ic_cc_home,
                    tint = Color(0xFF7D8CFF),
                    focusRequester = desktopRequester,
                    modifier = Modifier.weight(1f),
                    onLeft = { settingsRequester.requestFocus() },
                    onRight = { sourceRequester.requestFocus() },
                    onDown = { personalizeRequester.requestFocus() },
                    onClick = {
                        vm.closeControlCenter()
                        vm.openStockDesktop()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            SystemStatusRow(
                iconRes = R.drawable.ic_cc_volume,
                tint = Color(0xFF3ED6C4),
                label = "音量",
                percent = volumePercent,
            )
            Spacer(Modifier.height(10.dp))
            SystemStatusRow(
                iconRes = R.drawable.ic_cc_brightness,
                tint = Color(0xFFFFC45C),
                label = "亮度",
                percent = brightnessPercent,
            )
            Spacer(Modifier.height(14.dp))
            PersonalizeRow(
                focusRequester = personalizeRequester,
                onUp = { desktopRequester.requestFocus() },
                onClick = {
                    vm.closeControlCenter()
                    vm.showSettings()
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (vm.homeGuardEnabled) Color(0xFF56E39F)
                            else Color(0xFFFFC66D),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(7.dp))
                BasicText(
                    if (vm.homeGuardEnabled) {
                        "Home 键守护已启用"
                    } else {
                        "Home 键守护未启用"
                    },
                    style = CozyType.Caption.copy(
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.62f),
                    ),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    "CozyTV ${vm.appVersionName}",
                    style = CozyType.Caption.copy(
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ControlIcon(
    iconRes: Int,
    color: Color,
    size: Dp,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(size),
    )
}

@Composable
private fun CircleAction(
    label: String,
    iconRes: Int,
    tint: Color,
    focusRequester: FocusRequester,
    modifier: Modifier,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val select = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER
                    when {
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionLeft -> {
                            onLeft()
                            true
                        }
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionRight -> {
                            onRight()
                            true
                        }
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown -> {
                            onDown()
                            true
                        }
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp -> true
                        select && event.type == KeyEventType.KeyUp -> {
                            onClick()
                            true
                        }
                        else -> select
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusRequester.requestFocus()
                            onClick()
                        },
                    )
                }
                .clip(CircleShape)
                .then(
                    if (focused) {
                        Modifier.background(Color.White, CircleShape)
                    } else {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.13f)),
                            ),
                            CircleShape,
                        )
                    },
                )
                .border(
                    1.dp,
                    if (focused) Color.White else tint.copy(alpha = 0.38f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            ControlIcon(
                iconRes = iconRes,
                color = if (focused) CozyColors.Ink else Color.White,
                size = 24.dp,
            )
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            label,
            style = CozyType.Caption.copy(
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.82f),
            ),
        )
    }
}

@Composable
private fun SystemStatusRow(
    iconRes: Int,
    tint: Color,
    label: String,
    percent: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(tint.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ControlIcon(
                iconRes = iconRes,
                color = tint.copy(alpha = 0.9f),
                size = 17.dp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                BasicText(
                    label,
                    style = CozyType.Caption.copy(
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.68f),
                    ),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    "$percent%",
                    style = CozyType.Caption.copy(fontSize = 10.sp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun PersonalizeRow(
    focusRequester: FocusRequester,
    onUp: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val select = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER
                when {
                    event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionUp -> {
                        onUp()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown -> true
                    event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft -> true
                    event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionRight -> true
                    select && event.type == KeyEventType.KeyUp -> {
                        onClick()
                        true
                    }
                    else -> select
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusRequester.requestFocus()
                        onClick()
                    },
                )
            }
            .background(
                if (focused) Color.White.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIcon(
            iconRes = R.drawable.ic_cc_personalize,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.8f),
            size = 16.dp,
        )
        Spacer(Modifier.width(9.dp))
        BasicText("个性化", style = CozyType.Body.copy(fontSize = 12.sp))
        Spacer(Modifier.weight(1f))
        BasicText(
            "›",
            style = CozyType.Title.copy(
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.5f),
            ),
        )
    }
}

private fun readNetwork(context: Context): NetworkSummary = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
        ?: return@runCatching NetworkSummary("离线", false)
    val active = manager.activeNetwork
    val capabilities = active?.let { manager.getNetworkCapabilities(it) }
    when {
        capabilities == null -> NetworkSummary("离线", false)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
            NetworkSummary("Wi-Fi", true)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            NetworkSummary("有线网络", true)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            NetworkSummary("移动网络", true)
        else -> NetworkSummary("已联网", true)
    }
}.getOrDefault(NetworkSummary("离线", false))


