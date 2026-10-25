package app.cozytv.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.LiveSourcePortal
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.components.ActionRow
import app.cozytv.ui.components.BingBackdrop
import app.cozytv.ui.components.NavChip
import app.cozytv.ui.components.NavGlyph
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

@Composable
fun LiveSourceScreen(vm: LauncherViewModel) {
    BackHandler(onBack = vm::showSettings)
    var urls by remember { mutableStateOf(vm.liveSourceUrls.joinToString("\n")) }
    var error by remember { mutableStateOf<String?>(null) }
    val backRequester = remember { FocusRequester() }
    val fieldRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val defaultsRequester = remember { FocusRequester() }
    val portal = remember {
        LiveSourcePortal(
            urlsProvider = { vm.liveSourceUrls },
            onSave = { incoming -> vm.saveLiveSources(incoming, navigateHome = false) },
        )
    }
    var portalUrl by remember { mutableStateOf<String?>(null) }
    val qrBitmap = remember(portalUrl) {
        portalUrl?.let { runCatching { makeQrBitmap(it) }.getOrNull() }
    }

    DisposableEffect(portal) {
        val url = portal.start()
        portalUrl = url
        if (url == null) portal.stop()
        onDispose { portal.stop() }
    }
    LaunchedEffect(vm.liveSourceRevision) {
        urls = vm.liveSourceUrls.joinToString("\n")
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { backRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        BingBackdrop(vm.bingWallpaper, vm.wallpaper, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f)),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 54.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavChip(
                    label = "设置",
                    glyph = NavGlyph.Back,
                    focusRequester = backRequester,
                    onClick = vm::showSettings,
                )
                Spacer(Modifier.weight(1f))
                BasicText("多源 · M3U / HLS", style = CozyType.Caption)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("直播源", style = CozyType.Hero.copy(fontSize = 32.sp))
                Spacer(Modifier.width(12.dp))
                BasicText(
                    "${vm.liveSourceUrls.size} 个地址",
                    style = CozyType.Caption,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            BasicText(
                "每行一个 M3U / M3U8 地址，按顺序尝试；手机扫码可统一管理。",
                style = CozyType.Caption,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText("播放列表 URL（每行一个）", style = CozyType.Label)
                    Spacer(Modifier.height(9.dp))
                    val fieldFocused = remember { mutableStateOf(false) }
                    BasicTextField(
                        value = urls,
                        onValueChange = {
                            urls = it
                            error = null
                        },
                        singleLine = false,
                        maxLines = 10,
                        textStyle = CozyType.Body.copy(fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Default,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .focusRequester(fieldRequester)
                            .onFocusChanged { fieldFocused.value = it.isFocused }
                            .background(
                                if (fieldFocused.value) Color.White.copy(alpha = 0.17f)
                                else Color.Black.copy(alpha = 0.32f),
                                RoundedCornerShape(13.dp),
                            )
                            .border(
                                if (fieldFocused.value) 2.dp else 1.dp,
                                if (fieldFocused.value) Color.White
                                else Color.White.copy(alpha = 0.13f),
                                RoundedCornerShape(13.dp),
                            )
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (urls.isBlank()) {
                                    BasicText(
                                        "https://example.com/a.m3u8\n" +
                                            "https://example.com/b.m3u8",
                                        style = CozyType.Body.copy(
                                            color = CozyColors.Muted,
                                            fontSize = 14.sp,
                                        ),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            it,
                            style = CozyType.Caption.copy(color = Color(0xFFFF9297)),
                        )
                    }
                    vm.liveMessage?.let {
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            it,
                            style = CozyType.Caption.copy(color = Color.White.copy(alpha = 0.72f)),
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ActionRow(
                            title = if (vm.liveLoading) "正在加载…" else "保存并播放",
                            detail = "返回首页并播放第一个可用频道",
                            focusRequester = saveRequester,
                            onClick = {
                                val saved = vm.saveLiveSources(
                                    urls.lineSequence()
                                        .map(String::trim)
                                        .filter(String::isNotEmpty)
                                        .toList(),
                                )
                                error = if (saved) null else vm.liveMessage
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ActionRow(
                            title = "使用内置演示源",
                            detail = "清空所有自定义来源",
                            focusRequester = defaultsRequester,
                            onClick = {
                                urls = ""
                                error = if (vm.saveLiveSources(emptyList())) null
                                else vm.liveMessage
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Column(Modifier.width(280.dp)) {
                    BasicText("手机扫码统一管理", style = CozyType.Label)
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.13f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(14.dp),
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "扫码管理直播源",
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(8.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                BasicText(
                                    portalUrl.orEmpty(),
                                    style = CozyType.Caption.copy(fontSize = 9.sp),
                                    maxLines = 2,
                                )
                                Spacer(Modifier.height(4.dp))
                                BasicText(
                                    "手机与电视连同一网络\n扫码后自动聚焦输入框",
                                    style = CozyType.Caption.copy(
                                        color = Color.White.copy(alpha = 0.62f),
                                        fontSize = 9.sp,
                                    ),
                                )
                            } else {
                                BasicText(
                                    "局域网输入未启动\n请确认电视已连接 Wi-Fi 或网线",
                                    style = CozyType.Caption,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicText(
                "只播放你拥有授权的内容。TVLive / Media3 播放器内核遵循 Apache-2.0。",
                style = CozyType.Caption.copy(fontSize = 9.sp),
            )
        }
    }
}

private fun makeQrBitmap(text: String, size: Int = 480): Bitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val row = y * size
        for (x in 0 until size) {
            pixels[row + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
