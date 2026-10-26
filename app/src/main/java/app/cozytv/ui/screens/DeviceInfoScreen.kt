package app.cozytv.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.system.DefaultDesktop
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.components.ActionRow
import app.cozytv.ui.components.BingBackdrop
import app.cozytv.ui.components.NavChip
import app.cozytv.ui.components.NavGlyph
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.delay
import java.util.Locale

private data class DeviceSnapshot(
    val android: String,
    val build: String,
    val abi: String,
    val uptime: String,
    val memory: String,
    val storage: String,
    val screen: String,
    val network: String,
    val desktop: String,
)

@Composable
fun DeviceInfoScreen(vm: LauncherViewModel) {
    BackHandler(onBack = vm::showSettings)
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val snapshot = remember(context, revision) {
        readSnapshot(context)
    }
    val backRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { refreshRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        BingBackdrop(vm.bingWallpaper, vm.wallpaper, dim = 0.5f)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 54.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavChip(
                    label = "个性化",
                    glyph = NavGlyph.Back,
                    focusRequester = backRequester,
                    onClick = vm::showSettings,
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    "只读检查 · 不修改系统",
                    style = CozyType.Caption.copy(color = Color.White.copy(alpha = 0.56f)),
                )
            }
            Spacer(Modifier.height(12.dp))
            BasicText("设备信息检查", style = CozyType.Hero.copy(fontSize = 30.sp))
            Spacer(Modifier.height(2.dp))
            BasicText(
                "查看电视、应用和运行环境状态；这里不会清理或停用任何系统组件。",
                style = CozyType.Caption,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(262.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                InfoCard(
                    title = "设备与系统",
                    rows = listOf(
                        "设备" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "型号代码" to "${Build.DEVICE} · ${Build.BOARD}",
                        "Android" to snapshot.android,
                        "构建版本" to snapshot.build,
                        "CPU 架构" to snapshot.abi,
                    ),
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    title = "运行状态",
                    rows = listOf(
                        "CozyTV" to vm.appVersionName,
                        "默认桌面" to snapshot.desktop,
                        "屏幕" to snapshot.screen,
                        "内存" to snapshot.memory,
                        "存储" to snapshot.storage,
                        "网络" to snapshot.network,
                        "运行时长" to snapshot.uptime,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            // resolveActivity() is a synchronous binder call — cache it
            // instead of running it on every recomposition.
            val isCozyDefault = remember(context, revision) {
                DefaultDesktop.isCozyDefault(context)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountTile("应用", "${vm.apps.size}", Modifier.weight(1f))
                CountTile("信号源", "${vm.sources.size}", Modifier.weight(1f))
                CountTile("直播源", "${vm.liveSourceCount}", Modifier.weight(1f))
                CountTile(
                    "默认桌面",
                    if (isCozyDefault) "CozyTV" else "原厂",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionRow(
                    title = "重新检查",
                    detail = "刷新应用、信号源和运行数据",
                    focusRequester = refreshRequester,
                    onClick = {
                        revision++
                        vm.refresh()
                    },
                    modifier = Modifier.weight(1f),
                )
                ActionRow(
                    title = "打开系统设置",
                    detail = "查看系统应用、网络与显示设置",
                    focusRequester = settingsRequester,
                    onClick = vm::openSystemSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        BasicText(title, style = CozyType.Title.copy(fontSize = 19.sp))
        Spacer(Modifier.height(10.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    label,
                    style = CozyType.Caption.copy(
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.width(82.dp),
                )
                BasicText(
                    value,
                    style = CozyType.Body.copy(
                        fontSize = 12.sp,
                        color = CozyColors.Paper.copy(alpha = 0.9f),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CountTile(label: String, value: String, modifier: Modifier) {
    Row(
        modifier
            .height(42.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            style = CozyType.Caption.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.weight(1f))
        BasicText(value, style = CozyType.Label.copy(fontSize = 12.sp))
    }
}

private fun readSnapshot(context: Context): DeviceSnapshot {
    val metrics = context.resources.displayMetrics
    val memory = context.getSystemService(ActivityManager::class.java)
        ?.let { manager ->
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }
    val storage = runCatching {
        StatFs(context.filesDir.absolutePath).let { it.availableBytes to it.totalBytes }
    }.getOrNull()
    val defaultDesktop = DefaultDesktop.entries(context)
        .firstOrNull { it.isSystemDefault }
        ?.label
        ?: "未知"
    return DeviceSnapshot(
        android = "Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}",
        build = Build.ID,
        abi = Build.SUPPORTED_ABIS.take(2).joinToString(" / "),
        uptime = formatDuration(SystemClock.elapsedRealtime()),
        memory = memory?.let {
            "${formatBytes(it.availMem)} 可用 / ${formatBytes(it.totalMem)}"
        } ?: "不可用",
        storage = storage?.let {
            "${formatBytes(it.first)} 可用 / ${formatBytes(it.second)}"
        } ?: "不可用",
        screen = "${metrics.widthPixels}×${metrics.heightPixels} · ${metrics.densityDpi} dpi",
        network = networkLabel(context),
        desktop = defaultDesktop,
    )
}

private fun networkLabel(context: Context): String = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        ?: return@runCatching "离线"
    when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络"
        else -> "已连接"
    }
}.getOrDefault("未知")

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 ->
        String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024))
    else -> "${bytes / 1024} KB"
}

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes % (24 * 60) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}天 ${hours}小时"
        hours > 0 -> "${hours}小时 ${minutes}分钟"
        else -> "${minutes}分钟"
    }
}
