package app.cozytv.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.BuildConfig
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import app.cozytv.update.AppUpdateState
import kotlinx.coroutines.delay

@Composable
fun AppUpdateDialog(vm: LauncherViewModel) {
    val state = vm.updateState
    if (state is AppUpdateState.Idle) return

    val primaryRequester = remember { FocusRequester() }
    val secondaryRequester = remember { FocusRequester() }
    val busy = state is AppUpdateState.Checking || state is AppUpdateState.Downloading
    BackHandler {
        if (busy) vm.cancelUpdate() else vm.dismissUpdate()
    }

    LaunchedEffect(state::class) {
        delay(100)
        val defaultRequester = when (state) {
            is AppUpdateState.Available,
            is AppUpdateState.Ready,
            is AppUpdateState.Failed,
            -> secondaryRequester
            else -> primaryRequester
        }
        runCatching { defaultRequester.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF32A4168), Color(0xF5111525)),
                    ),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(28.dp),
                )
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF77CBFF), Color(0xFF8A8CFF)),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "↻",
                        style = CozyType.Title.copy(
                            fontSize = 28.sp,
                            color = CozyColors.Ink,
                        ),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        title(state),
                        style = CozyType.Title.copy(fontSize = 21.sp),
                    )
                    BasicText(
                        subtitle(state),
                        style = CozyType.Caption.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.62f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            when (state) {
                is AppUpdateState.Checking -> {
                    UpdateMessage("正在连接 GitHub Releases，检查是否有新版本…")
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        UpdateButton(
                            label = "取消",
                            primary = false,
                            focusRequester = primaryRequester,
                            onLeft = {},
                            onRight = {},
                            onClick = vm::cancelUpdate,
                        )
                    }
                }
                is AppUpdateState.UpToDate -> {
                    UpdateMessage("当前版本 v${vm.appVersionName} 已是最新版本。")
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        UpdateButton(
                            label = "完成",
                            primary = true,
                            focusRequester = primaryRequester,
                            onLeft = {},
                            onRight = {},
                            onClick = vm::dismissUpdate,
                        )
                    }
                }
                is AppUpdateState.Available -> {
                    UpdateMessage(availableMessage(state))
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UpdateButton(
                            label = "稍后",
                            primary = false,
                            focusRequester = secondaryRequester,
                            onLeft = { primaryRequester.requestFocus() },
                            onRight = { primaryRequester.requestFocus() },
                            onClick = vm::dismissUpdate,
                            modifier = Modifier.weight(1f),
                        )
                        UpdateButton(
                            label = "下载并安装",
                            primary = true,
                            focusRequester = primaryRequester,
                            onLeft = { secondaryRequester.requestFocus() },
                            onRight = { secondaryRequester.requestFocus() },
                            onClick = vm::downloadUpdate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is AppUpdateState.Downloading -> {
                    UpdateMessage("正在下载 v${state.info.versionName}，请保持电视联网。")
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(4.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.percent.coerceIn(0, 100) / 100f)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF82E8D2), Color(0xFF8A8CFF)),
                                    ),
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        "${state.percent}%",
                        style = CozyType.Caption.copy(fontSize = 11.sp),
                        modifier = Modifier.align(Alignment.End),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        UpdateButton(
                            label = "取消下载",
                            primary = false,
                            focusRequester = primaryRequester,
                            onLeft = {},
                            onRight = {},
                            onClick = vm::cancelUpdate,
                        )
                    }
                }
                is AppUpdateState.Ready -> {
                    val hint = if (state.permissionRequired) {
                        "请先允许 CozyTV 安装未知应用，返回后再次选择“去安装”。"
                    } else {
                        "安装包已下载并通过应用包名与签名校验。"
                    }
                    UpdateMessage(hint)
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UpdateButton(
                            label = "稍后",
                            primary = false,
                            focusRequester = secondaryRequester,
                            onLeft = { primaryRequester.requestFocus() },
                            onRight = { primaryRequester.requestFocus() },
                            onClick = vm::dismissUpdate,
                            modifier = Modifier.weight(1f),
                        )
                        UpdateButton(
                            label = "去安装",
                            primary = true,
                            focusRequester = primaryRequester,
                            onLeft = { secondaryRequester.requestFocus() },
                            onRight = { secondaryRequester.requestFocus() },
                            onClick = vm::openUpdateInstaller,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is AppUpdateState.Failed -> {
                    UpdateMessage(state.message)
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UpdateButton(
                            label = "关闭",
                            primary = false,
                            focusRequester = secondaryRequester,
                            onLeft = { primaryRequester.requestFocus() },
                            onRight = { primaryRequester.requestFocus() },
                            onClick = vm::dismissUpdate,
                            modifier = Modifier.weight(1f),
                        )
                        UpdateButton(
                            label = "重试",
                            primary = true,
                            focusRequester = primaryRequester,
                            onLeft = { secondaryRequester.requestFocus() },
                            onRight = { secondaryRequester.requestFocus() },
                            onClick = vm::checkForUpdates,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                AppUpdateState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun UpdateMessage(text: String) {
    BasicText(
        text,
        style = CozyType.Body.copy(
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.78f),
        ),
        maxLines = 9,
    )
}

private fun title(state: AppUpdateState): String = when (state) {
    AppUpdateState.Idle, AppUpdateState.Checking -> "检查更新"
    is AppUpdateState.UpToDate -> "已是最新版本"
    is AppUpdateState.Available -> "发现新版本 ${state.info.versionName}"
    is AppUpdateState.Downloading -> "正在下载更新"
    is AppUpdateState.Ready -> if (state.permissionRequired) "需要安装权限" else "安装包已就绪"
    is AppUpdateState.Failed -> "更新失败"
}

private fun subtitle(state: AppUpdateState): String = when (state) {
    AppUpdateState.Idle -> ""
    AppUpdateState.Checking -> if ("api.github.com" in BuildConfig.UPDATE_MANIFEST_URL) {
        "CozyTV · GitHub Releases"
    } else {
        "CozyTV · 更新清单"
    }
    is AppUpdateState.UpToDate -> "当前无需更新"
    is AppUpdateState.Available -> sizeLabel(state.info.sizeBytes)
    is AppUpdateState.Downloading -> "${state.percent}% · 请稍候"
    is AppUpdateState.Ready -> if (state.permissionRequired) {
        "安装包已校验，等待系统授权"
    } else {
        "即将交给系统安装器"
    }
    is AppUpdateState.Failed -> "可重试，或稍后从 Release 页面手动下载"
}

private fun availableMessage(state: AppUpdateState.Available): String = buildString {
    append("当前版本 v")
    append(state.info.versionName)
    append(" 可供安装。")
    if (state.info.notes.isNotBlank()) {
        appendLine()
        appendLine()
        append(
            state.info.notes
                .replace("\r", "")
                .take(720),
        )
    }
    appendLine()
    appendLine()
    append("下载完成后会打开系统安装界面，最后一按仍需遥控器确认。")
}

private fun sizeLabel(size: Long): String =
    if (size > 0) "安装包约 ${size / 1024 / 1024} MB" else "从官方 Release 获取安装包"

@Composable
private fun UpdateButton(
    label: String,
    primary: Boolean,
    focusRequester: FocusRequester,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(46.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val select = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        onLeft()
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        onRight()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> true
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
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    focused && primary -> Color.White
                    primary -> CozyColors.Violet
                    focused -> Color.White.copy(alpha = 0.20f)
                    else -> Color.White.copy(alpha = 0.07f)
                },
            )
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = CozyType.Label.copy(
                fontSize = 12.sp,
                color = if (focused && primary) CozyColors.Ink else Color.White,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}
