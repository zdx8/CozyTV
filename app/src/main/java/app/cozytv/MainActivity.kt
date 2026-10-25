package app.cozytv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.cozytv.data.LauncherPreferences
import app.cozytv.ui.LauncherViewModel
import app.cozytv.ui.CozyApp

class MainActivity : ComponentActivity() {
    private val vm by viewModels<LauncherViewModel>()
    private var packagesRegistered = false
    private val showHomeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.showHome()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            showHomeReceiver,
            IntentFilter(ACTION_SHOW_HOME),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        applySavedBrightness()
        setContent {
            val currentDensity = LocalDensity.current
            val canvasDensity = Density(
                density = resources.displayMetrics.widthPixels / 960f,
                fontScale = currentDensity.fontScale,
            )
            CompositionLocalProvider(LocalDensity provides canvasDensity) {
                CozyApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!packagesRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                this,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            packagesRegistered = true
        }
    }

    override fun onResume() {
        super.onResume()
        applySavedBrightness()
        vm.refreshHomeGuard()
        vm.refreshDailyContent()
    }

    override fun onStop() {
        if (packagesRegistered) {
            unregisterReceiver(packageReceiver)
            packagesRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(showHomeReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.showHome()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun applySavedBrightness() {
        val saved = LauncherPreferences(this).brightness
        if (saved >= 0f) {
            window.attributes = window.attributes.apply {
                screenBrightness = saved.coerceIn(0.05f, 1f)
            }
        }
    }

    companion object {
        const val ACTION_SHOW_HOME = "app.cozytv.action.SHOW_HOME"
    }
}
