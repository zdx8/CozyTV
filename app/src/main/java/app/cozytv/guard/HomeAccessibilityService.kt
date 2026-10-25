package app.cozytv.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.cozytv.MainActivity

/**
 * Routes only the physical HOME key back to CozyTV.
 *
 * The previous usage-stats watcher also reacted to the stock launcher becoming
 * foreground, which made every intentional visit to the stock desktop bounce
 * straight back. Key filtering keeps those visits untouched.
 */
class HomeAccessibilityService : AccessibilityService() {
    private var lastHomeAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHomeAt >= HOME_THROTTLE_MS) {
                lastHomeAt = now
                sendBroadcast(
                    Intent(MainActivity.ACTION_SHOW_HOME).setPackage(packageName),
                )
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        ),
                )
            }
        }
        // Consume both DOWN and UP so the stock launcher does not process HOME.
        return true
    }

    private companion object {
        const val HOME_THROTTLE_MS = 500L
    }
}
