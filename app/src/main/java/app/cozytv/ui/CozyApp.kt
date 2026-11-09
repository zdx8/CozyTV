package app.cozytv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cozytv.ui.components.AppUpdateDialog
import app.cozytv.ui.screens.AppsScreen
import app.cozytv.ui.screens.HomeScreen
import app.cozytv.ui.screens.InputsScreen
import app.cozytv.ui.screens.LiveSourceScreen
import app.cozytv.ui.screens.SettingsScreen
import app.cozytv.ui.screens.DeviceInfoScreen

@Composable
fun CozyApp(vm: LauncherViewModel) {
    BackHandler(enabled = vm.screen == LauncherScreen.Home) {
        // A home launcher should never disappear because of Back.
    }
    Box(Modifier.fillMaxSize()) {
        when (vm.screen) {
            LauncherScreen.Home -> HomeScreen(vm)
            LauncherScreen.Apps -> AppsScreen(vm)
            LauncherScreen.Inputs -> InputsScreen(vm)
            LauncherScreen.DeviceInfo -> DeviceInfoScreen(vm)
            LauncherScreen.LiveSource -> LiveSourceScreen(vm)
            LauncherScreen.Settings -> SettingsScreen(vm)
        }
        AppUpdateDialog(vm)
    }
}

