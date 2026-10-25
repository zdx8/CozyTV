package app.cozytv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cozytv.data.TvApp
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.components.AppActionMenu
import app.cozytv.ui.components.AppTile
import app.cozytv.ui.components.BingBackdrop
import app.cozytv.ui.components.Keycap
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType
import kotlinx.coroutines.delay

@Composable
fun AppsScreen(vm: LauncherViewModel) {
    BackHandler(onBack = vm::showHome)
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(app: TvApp) = requesters.getOrPut(app.key) { FocusRequester() }
    var menuApp by remember { mutableStateOf<TvApp?>(null) }

    LaunchedEffect(vm.loading, vm.displayedApps.map(TvApp::key)) {
        if (vm.loading) return@LaunchedEffect
        delay(100)
        vm.displayedApps.firstOrNull()?.let {
            runCatching { requester(it).requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        BingBackdrop(vm.bingWallpaper, vm.wallpaper, dim = 0.34f)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, end = 54.dp, top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "${vm.displayedApps.size} 个应用",
                    style = CozyType.Caption,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 54.dp, vertical = 18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(
                    text = "全部应用",
                    style = CozyType.Hero.copy(fontSize = 31.sp),
                )
                Spacer(Modifier.width(13.dp))
                BasicText(
                    text = "长按 OK 收藏到首页",
                    style = CozyType.Caption,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (vm.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText("正在扫描已安装的电视应用…", style = CozyType.Body)
                }
            } else if (vm.displayedApps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText("没有找到可启动的应用", style = CozyType.Body)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(
                        start = 54.dp,
                        end = 54.dp,
                        top = 12.dp,
                        bottom = 24.dp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(vm.displayedApps, key = TvApp::key) { app ->
                        AppTile(
                            app = app,
                            width = 158.dp,
                            height = 88.dp,
                            showNames = vm.showNames,
                            useBanner = true,
                            hidden = app.key in vm.hiddenKeys,
                            focusRequester = requester(app),
                            onClick = { vm.launch(app) },
                            onLongClick = { vm.toggleFavorite(app) },
                            onMenu = { menuApp = app },
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
                Keycap("← ↑ ↓ →")
                Spacer(Modifier.width(7.dp))
                BasicText("浏览", style = CozyType.Caption)
                Spacer(Modifier.width(15.dp))
                Keycap("OK")
                Spacer(Modifier.width(7.dp))
                BasicText("打开", style = CozyType.Caption)
                Spacer(Modifier.weight(1f))
                BasicText("按返回键回到首页", style = CozyType.Caption)
            }
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
                        .padding(top = 22.dp)
                        .background(
                            Color(0xEE11162B),
                            RoundedCornerShape(20.dp),
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.16f),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    BasicText(it, style = CozyType.Label.copy(color = CozyColors.Paper))
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
    }
}
