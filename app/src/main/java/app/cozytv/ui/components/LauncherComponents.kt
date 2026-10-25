package app.cozytv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.TvApp
import app.cozytv.data.SourceKind
import app.cozytv.data.TvSource
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val CardShape = RoundedCornerShape(13.dp)

@Composable
fun AppTile(
    app: TvApp,
    width: Dp,
    height: Dp,
    showNames: Boolean,
    modifier: Modifier = Modifier,
    useBanner: Boolean = true,
    androidIcon: Boolean = false,
    hidden: Boolean = false,
    onMenu: (() -> Unit)? = null,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else if (focused) 1.04f else 1f,
        animationSpec = tween(160),
        label = "tile-scale",
    )
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(160),
        label = "tile-focus",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Color.White else Color.White.copy(alpha = 0.13f),
        animationSpec = tween(160),
        label = "tile-border",
    )
    val bannerImage = remember(app.banner) { app.banner?.asImageBitmap() }
    val iconImage = remember(app.icon) { app.icon?.asImageBitmap() }
    val accent = Color(app.accentColor)

    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -focusAmount * 2f
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) {
                    pressed = false
                    longPressJob?.cancel()
                }
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_MENU) {
                    if (onMenu != null) {
                        if (event.type == KeyEventType.KeyUp) onMenu.invoke()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                val isSelect = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER ||
                    native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isSelect) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        pressed = true
                        if (native.repeatCount > 0 && !longPressFired) {
                            longPressJob?.cancel()
                            longPressFired = true
                            onLongClick()
                        } else if (native.repeatCount == 0) {
                            longPressFired = false
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(550)
                                if (focused) {
                                    longPressFired = true
                                    onLongClick()
                                }
                            }
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        pressed = false
                        longPressJob?.cancel()
                        if (!longPressFired) onClick()
                        true
                    }
                    else -> true
                }
            }
            .focusable()
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = {
                        focusRequester.requestFocus()
                        onClick()
                    },
                    onLongPress = {
                        focusRequester.requestFocus()
                        onLongClick()
                    },
                )
            }
            .drawWithContent {
                drawContent()
                if (focusAmount > 0.01f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.95f * focusAmount),
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(13.dp.toPx()),
                    )
                }
            }
            .clip(CardShape)
            .then(
                if (androidIcon) {
                    Modifier.background(Color(0xC7151B2D), CardShape)
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.94f),
                                Color(0xFF12182E).copy(alpha = 0.96f),
                            ),
                            start = Offset.Zero,
                            end = Offset(width.value * 2f, height.value * 2f),
                        ),
                        CardShape,
                    )
                },
            )
            .border(1.dp, borderColor, CardShape),
    ) {
        if (androidIcon) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconOrMonogram(
                    image = iconImage,
                    label = app.label,
                    modifier = Modifier.size(if (showNames) 38.dp else 44.dp),
                )
                if (showNames) {
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        text = app.label,
                        style = CozyType.Caption.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (useBanner && bannerImage != null) {
            Image(
                bitmap = bannerImage,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        ),
                    ),
            )
            if (showNames) {
                BasicText(
                    text = app.label,
                    style = CozyType.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                )
            }
        } else {
            if (showNames) {
                IconOrMonogram(
                    image = iconImage,
                    label = app.label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(34.dp),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 10.dp, bottom = 10.dp),
                ) {
                    BasicText(
                        text = app.label,
                        style = CozyType.Body.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                IconOrMonogram(
                    image = iconImage,
                    label = app.label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                    ),
                ),
        )
        if (hidden) {
            BasicText(
                text = "已隐藏",
                style = CozyType.Caption.copy(fontSize = 8.sp, color = Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun IconOrMonogram(
    image: ImageBitmap?,
    label: String,
    modifier: Modifier,
) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier.background(
                Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(8.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label.take(1).uppercase(),
                style = CozyType.Title.copy(fontSize = 18.sp),
            )
        }
    }
}

enum class NavGlyph {
    Apps,
    Settings,
    Back,
    Inputs,
    Control,
    Network,
    Volume,
    Brightness,
    Personalize,
    Favorite,
    Hide,
    Uninstall,
    Play,
}

@Composable
fun NavChip(
    label: String,
    glyph: NavGlyph,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "nav-chip")
    Row(
        modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = 1f + 0.055f * amount
                scaleY = 1f + 0.055f * amount
            }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (isSelect(event.nativeKeyEvent) && event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    isSelect(event.nativeKeyEvent)
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (focused) Color.White else Color.Black.copy(alpha = 0.25f),
            )
            .border(
                1.dp,
                Color.White.copy(alpha = 0.16f + 0.3f * amount),
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavGlyphCanvas(
            glyph = glyph,
            color = if (focused) CozyColors.Ink else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = label,
            style = CozyType.Label.copy(
                color = if (focused) CozyColors.Ink else Color.White,
            ),
        )
    }
}

@Composable
fun NavGlyphCanvas(
    glyph: NavGlyph,
    color: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        when (glyph) {
            NavGlyph.Apps, NavGlyph.Personalize -> {
                val cell = size.minDimension * 0.34f
                listOf(
                    Offset(0f, 0f),
                    Offset(size.width - cell, 0f),
                    Offset(0f, size.height - cell),
                    Offset(size.width - cell, size.height - cell),
                ).forEach {
                    drawRoundRect(
                        color = color,
                        topLeft = it,
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx()),
                        style = Stroke(stroke),
                    )
                }
            }
            NavGlyph.Settings -> {
                val center = Offset(size.width / 2, size.height / 2)
                val outer = size.minDimension * 0.34f
                drawCircle(color, radius = outer, center = center, style = Stroke(stroke))
                drawCircle(
                    color,
                    radius = outer * 0.42f,
                    center = center,
                    style = Stroke(stroke),
                )
                repeat(8) { i ->
                    val angle = Math.toRadians((i * 45).toDouble())
                    drawLine(
                        color,
                        Offset(
                            center.x + cos(angle).toFloat() * outer * 0.82f,
                            center.y + sin(angle).toFloat() * outer * 0.82f,
                        ),
                        Offset(
                            center.x + cos(angle).toFloat() * outer * 1.08f,
                            center.y + sin(angle).toFloat() * outer * 1.08f,
                        ),
                        stroke,
                    )
                }
            }
            NavGlyph.Control -> {
                repeat(3) { i ->
                    val y = size.height * (0.2f + i * 0.3f)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
                    val x = size.width * if (i == 1) 0.66f else 0.34f
                    drawCircle(color, radius = 2.3.dp.toPx(), center = Offset(x, y))
                }
            }
            NavGlyph.Network -> {
                val w = size.width
                val h = size.height
                drawArc(
                    color,
                    205f,
                    130f,
                    false,
                    topLeft = Offset(w * 0.08f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.84f),
                    style = Stroke(stroke),
                )
                drawArc(
                    color,
                    205f,
                    130f,
                    false,
                    topLeft = Offset(w * 0.26f, h * 0.42f),
                    size = androidx.compose.ui.geometry.Size(w * 0.48f, h * 0.48f),
                    style = Stroke(stroke),
                )
                drawArc(
                    color,
                    205f,
                    130f,
                    false,
                    topLeft = Offset(w * 0.38f, h * 0.6f),
                    size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.24f),
                    style = Stroke(stroke),
                )
                drawCircle(
                    color,
                    radius = 1.5.dp.toPx(),
                    center = Offset(w * 0.5f, h * 0.82f),
                )
            }
            NavGlyph.Volume -> {
                val w = size.width
                val h = size.height
                val body = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.06f, h * 0.38f)
                    lineTo(w * 0.28f, h * 0.38f)
                    lineTo(w * 0.48f, h * 0.18f)
                    lineTo(w * 0.48f, h * 0.82f)
                    lineTo(w * 0.28f, h * 0.62f)
                    lineTo(w * 0.06f, h * 0.62f)
                    close()
                }
                drawPath(body, color, style = Stroke(stroke))
                drawArc(
                    color,
                    -50f,
                    100f,
                    false,
                    topLeft = Offset(w * 0.4f, h * 0.34f),
                    size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.32f),
                    style = Stroke(stroke),
                )
                drawArc(
                    color,
                    -50f,
                    100f,
                    false,
                    topLeft = Offset(w * 0.4f, h * 0.18f),
                    size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.64f),
                    style = Stroke(stroke),
                )
            }
            NavGlyph.Brightness -> {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color,
                    radius = size.minDimension * 0.2f,
                    center = center,
                    style = Stroke(stroke),
                )
                repeat(8) { i ->
                    val angle = Math.toRadians((i * 45).toDouble())
                    val inner = size.minDimension * 0.31f
                    val outer = size.minDimension * 0.45f
                    drawLine(
                        color,
                        Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner,
                        ),
                        Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer,
                        ),
                        stroke,
                    )
                }
            }
            NavGlyph.Favorite -> {
                val center = Offset(size.width / 2, size.height / 2)
                val outer = size.minDimension * 0.45f
                val inner = outer * 0.45f
                val path = androidx.compose.ui.graphics.Path()
                repeat(10) { i ->
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val radius = if (i % 2 == 0) outer else inner
                    val x = center.x + cos(angle).toFloat() * radius
                    val y = center.y + sin(angle).toFloat() * radius
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = Stroke(stroke))
            }
            NavGlyph.Play -> {
                val w = size.width
                val h = size.height
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.34f, h * 0.2f)
                    lineTo(w * 0.82f, h * 0.5f)
                    lineTo(w * 0.34f, h * 0.8f)
                    close()
                }
                drawPath(path, color)
            }
            NavGlyph.Hide -> {
                val w = size.width
                val h = size.height
                drawOval(
                    color,
                    topLeft = Offset(w * 0.08f, h * 0.3f),
                    size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.4f),
                    style = Stroke(stroke),
                )
                drawCircle(
                    color,
                    radius = w * 0.08f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(w * 0.12f, h * 0.84f),
                    Offset(w * 0.88f, h * 0.16f),
                    stroke,
                )
            }
            NavGlyph.Uninstall -> {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.28f, h * 0.3f),
                    size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
                    style = Stroke(stroke),
                )
                drawLine(color, Offset(w * 0.16f, h * 0.3f), Offset(w * 0.84f, h * 0.3f), stroke)
                drawLine(color, Offset(w * 0.4f, h * 0.14f), Offset(w * 0.6f, h * 0.14f), stroke)
                drawLine(color, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.42f, h * 0.72f), stroke)
                drawLine(color, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.58f, h * 0.72f), stroke)
            }
            NavGlyph.Back -> {
                drawLine(
                    color,
                    Offset(size.width * 0.68f, size.height * 0.12f),
                    Offset(size.width * 0.28f, size.height * 0.5f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.28f, size.height * 0.5f),
                    Offset(size.width * 0.68f, size.height * 0.88f),
                    stroke,
                )
            }
            NavGlyph.Inputs -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.06f, size.height * 0.28f)
                    lineTo(size.width * 0.94f, size.height * 0.28f)
                    lineTo(size.width * 0.83f, size.height * 0.7f)
                    lineTo(size.width * 0.17f, size.height * 0.7f)
                    close()
                }
                drawPath(path, color, style = Stroke(stroke))
                drawLine(
                    color,
                    Offset(size.width * 0.36f, size.height * 0.7f),
                    Offset(size.width * 0.31f, size.height * 0.94f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.64f, size.height * 0.7f),
                    Offset(size.width * 0.69f, size.height * 0.94f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.28f, size.height * 0.94f),
                    Offset(size.width * 0.72f, size.height * 0.94f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.38f, size.height * 0.48f),
                    Offset(size.width * 0.58f, size.height * 0.48f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.52f, size.height * 0.42f),
                    Offset(size.width * 0.58f, size.height * 0.48f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.52f, size.height * 0.54f),
                    Offset(size.width * 0.58f, size.height * 0.48f),
                    stroke,
                )
            }
        }
    }
}

@Composable
fun SourceTile(
    source: TvSource,
    isDefault: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.98f else if (focused) 1.025f else 1f,
        tween(150),
        label = "source-scale",
    )
    val accent = when (source.kind) {
        SourceKind.TvLive -> Color(0xFF4169E1)
        SourceKind.Hdmi -> Color(0xFF5C75D8)
        SourceKind.DisplayPort -> Color(0xFF3F8FBE)
        SourceKind.Tuner -> Color(0xFFB06B8B)
        SourceKind.Composite -> Color(0xFFB78357)
        SourceKind.Other -> Color(0xFF65718E)
    }
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
            }
            .onPreviewKeyEvent { event ->
                if (!isSelect(event.nativeKeyEvent)) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        pressed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        pressed = false
                        onClick()
                        true
                    }
                    else -> true
                }
            }
            .focusable()
            .pointerInput(source.id) {
                detectTapGestures(onTap = {
                    focusRequester.requestFocus()
                    onClick()
                })
            }
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.82f), Color(0xFF12182E).copy(alpha = 0.94f)),
                ),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(15.dp),
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            NavGlyphCanvas(
                glyph = NavGlyph.Inputs,
                color = Color.White,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                source.label,
                style = CozyType.Body.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                when (source.kind) {
                    SourceKind.TvLive -> "LIVE"
                    SourceKind.Hdmi -> "HDMI"
                    SourceKind.DisplayPort -> "DISPLAYPORT"
                    SourceKind.Tuner -> "TV / TUNER"
                    SourceKind.Composite -> "AV / COMPOSITE"
                    SourceKind.Other -> "TV INPUT"
                },
                style = CozyType.Caption.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
            )
        }
        if (isDefault) {
            BasicText(
                "默认",
                style = CozyType.Caption.copy(
                    fontSize = 8.sp,
                    color = Color(0xFF10221E),
                ),
                modifier = Modifier
                    .background(Color(0xFF6CE6C7), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .size(7.dp)
                .background(
                    if (source.connected) Color(0xFF6CE6C7)
                    else Color.White.copy(alpha = 0.24f),
                    RoundedCornerShape(4.dp),
                ),
        )
    }
}

@Composable
fun ActionRow(
    title: String,
    detail: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "action-focus")
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer {
                scaleX = 1f + 0.012f * amount
                scaleY = 1f + 0.012f * amount
            }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (isSelect(event.nativeKeyEvent) && event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    isSelect(event.nativeKeyEvent)
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.055f),
            )
            .border(
                1.dp,
                if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(13.dp),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = CozyType.Body)
            Spacer(Modifier.height(2.dp))
            BasicText(detail, style = CozyType.Caption)
        }
        BasicText("›", style = CozyType.Hero.copy(fontSize = 24.sp, color = CozyColors.Muted))
    }
}

@Composable
fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    focusRequester: FocusRequester,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "toggle-focus")
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer {
                scaleX = 1f + 0.012f * amount
                scaleY = 1f + 0.012f * amount
            }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (isSelect(event.nativeKeyEvent) && event.type == KeyEventType.KeyUp) {
                    onToggle()
                    true
                } else {
                    isSelect(event.nativeKeyEvent)
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggle() })
            }
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.055f),
            )
            .border(
                1.dp,
                if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(13.dp),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = CozyType.Body)
            Spacer(Modifier.height(2.dp))
            BasicText(detail, style = CozyType.Caption)
        }
        Box(
            Modifier
                .width(42.dp)
                .height(24.dp)
                .background(
                    if (checked) CozyColors.Violet else Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(12.dp),
                )
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(Color.White, RoundedCornerShape(9.dp)),
            )
        }
    }
}

@Composable
fun WallpaperChoice(
    label: String,
    swatch: Brush,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "swatch-focus")
    Column(
        modifier
            .graphicsLayer {
                scaleX = 1f + 0.035f * amount
                scaleY = 1f + 0.035f * amount
            }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (isSelect(event.nativeKeyEvent) && event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    isSelect(event.nativeKeyEvent)
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        Box(
            Modifier
                .width(148.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(swatch)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.13f),
                    RoundedCornerShape(12.dp),
                ),
        ) {
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .background(CozyColors.Violet, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("✓", style = CozyType.Label)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            text = label,
            style = CozyType.Label.copy(
                color = if (focused) Color.White else CozyColors.Muted,
            ),
        )
    }
}

@Composable
fun Keycap(text: String) {
    Box(
        Modifier
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = CozyType.Caption.copy(fontSize = 9.sp, color = Color.White))
    }
}

private fun isSelect(event: KeyEvent): Boolean =
    event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        event.keyCode == KeyEvent.KEYCODE_ENTER ||
        event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
