package app.cozytv.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.TvSource
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.components.BingBackdrop
import app.cozytv.ui.components.Keycap
import app.cozytv.ui.components.NavChip
import app.cozytv.ui.components.NavGlyph
import app.cozytv.ui.components.SourceTile
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.delay

@Composable
fun InputsScreen(vm: LauncherViewModel) {
    BackHandler(onBack = vm::showHome)
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(source: TvSource) = requesters.getOrPut(source.id) { FocusRequester() }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(vm.sources) {
        delay(100)
        vm.sources.firstOrNull()?.let { runCatching { requester(it).requestFocus() } }
    }

    Box(Modifier.fillMaxSize()) {
        BingBackdrop(vm.bingWallpaper, vm.wallpaper, dim = 0.38f)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 54.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavChip(
                    label = "首页",
                    glyph = NavGlyph.Back,
                    focusRequester = backRequester,
                    onClick = vm::showHome,
                )
                Spacer(Modifier.weight(1f))
                BasicText("${vm.sources.count { it.connected }} 个已连接", style = CozyType.Caption)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 54.dp, vertical = 18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText("信号源", style = CozyType.Hero.copy(fontSize = 31.sp))
                Spacer(Modifier.width(13.dp))
                BasicText(
                    "TVLive 在首页播放；HDMI、电视和 AV 全屏打开",
                    style = CozyType.Caption,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (vm.sources.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 54.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText("电视没有注册可切换的信号源", style = CozyType.Title)
                        Spacer(Modifier.height(7.dp))
                        BasicText(
                            "盒子通常没有 HDMI 输入；部分国产电视把信号源放在厂商自己的面板里。",
                            style = CozyType.Caption,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 54.dp),
                ) {
                    items(vm.sources, key = TvSource::id) { source ->
                        SourceTile(
                            source = source,
                            isDefault = source.id == vm.defaultSourceId,
                            focusRequester = requester(source),
                            onClick = { vm.playSource(source) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 54.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Keycap("OK")
                Spacer(Modifier.width(7.dp))
                BasicText("切换并设为默认 · HDMI/电视/AV 全屏打开", style = CozyType.Caption)
                Spacer(Modifier.weight(1f))
                BasicText("绿点表示已连接", style = CozyType.Caption)
            }
        }
    }
}
