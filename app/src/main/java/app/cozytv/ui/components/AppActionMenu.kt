package app.cozytv.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.TvApp
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.delay

@Composable
fun AppActionMenu(
    vm: LauncherViewModel,
    app: TvApp,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val favoriteRequester = remember { FocusRequester() }
    val hideRequester = remember { FocusRequester() }
    val uninstallRequester = remember { FocusRequester() }
    val icon = remember(app.icon) { app.icon?.asImageBitmap() }
    val favorite = app.key in vm.favorites
    val hidden = vm.isHidden(app)

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { favoriteRequester.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF0233354), Color(0xF4101420)),
                    ),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(24.dp),
                )
                .padding(18.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        BasicText(
                            app.label.take(1).uppercase(),
                            style = CozyType.Title.copy(fontSize = 20.sp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        app.label,
                        style = CozyType.Title.copy(fontSize = 16.sp),
                        maxLines = 1,
                    )
                    BasicText(
                        app.packageName,
                        style = CozyType.Caption.copy(
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.55f),
                        ),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            MenuAction(
                label = if (favorite) "取消收藏" else "收藏",
                glyph = NavGlyph.Favorite,
                focusRequester = favoriteRequester,
                onUp = { uninstallRequester.requestFocus() },
                onDown = { hideRequester.requestFocus() },
                onClick = {
                    vm.toggleFavorite(app)
                    onDismiss()
                },
            )
            Spacer(Modifier.height(8.dp))
            MenuAction(
                label = if (hidden) "取消隐藏" else "隐藏",
                glyph = NavGlyph.Hide,
                focusRequester = hideRequester,
                onUp = { favoriteRequester.requestFocus() },
                onDown = { uninstallRequester.requestFocus() },
                onClick = {
                    vm.toggleHidden(app)
                    onDismiss()
                },
            )
            Spacer(Modifier.height(8.dp))
            MenuAction(
                label = "卸载应用",
                glyph = NavGlyph.Uninstall,
                focusRequester = uninstallRequester,
                onUp = { hideRequester.requestFocus() },
                onDown = { favoriteRequester.requestFocus() },
                onClick = {
                    vm.uninstall(app)
                    onDismiss()
                },
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                "按返回键关闭",
                style = CozyType.Caption.copy(
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.45f),
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun MenuAction(
    label: String,
    glyph: NavGlyph,
    focusRequester: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) selectPressed = false
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val select = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                        onUp()
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                        onDown()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> true
                    select && event.type == KeyEventType.KeyDown -> {
                        // The menu receives focus while OK is still held after a tile long-press.
                        // Only a new non-repeat press may activate a menu action.
                        if (native.repeatCount == 0) selectPressed = true
                        true
                    }
                    select && event.type == KeyEventType.KeyUp -> {
                        if (selectPressed) onClick()
                        selectPressed = false
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
                if (focused) Color.White.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.055f),
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (focused) Color.White.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavGlyphCanvas(
            glyph = glyph,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(label, style = CozyType.Body.copy(fontSize = 13.sp))
    }
}
