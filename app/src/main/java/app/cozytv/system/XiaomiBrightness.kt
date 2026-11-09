package app.cozytv.system

import android.content.Context
import android.util.Log
import kotlin.math.roundToInt

/**
 * Xiaomi panel brightness via the mitv middleware, following Aerial TV's
 * verified ASTP0 approach. Returns null when this is not a compatible
 * MiTV, letting the launcher use its in-app dim fallback.
 */
object XiaomiBrightness {
    private const val TAG = "CozyTVBrightness"
    private const val PREFS = "cozy_brightness"
    private const val KEY_LEVEL = "mitv_backlight"

    private var lastLevel: Int? = null

    @Synchronized
    fun level(context: Context): Float? {
        if (!isXiaomi(context)) return null
        if (!runCatching { MitvPanel.usable(context) }.getOrDefault(false)) {
            Log.w(TAG, "MiTV panel path unavailable (model=${android.os.Build.MODEL})")
            return null
        }
        val value = runCatching { MitvPanel.level(context) }.getOrNull()
            ?: lastLevel
            ?: stored(context)
            ?: 50
        lastLevel = value.coerceIn(1, 100)
        return lastLevel!! / 100f
    }

    @Synchronized
    fun adjust(context: Context, direction: Int): Float? {
        val current = level(context) ?: return null
        val percent = (current * 100f).roundToInt().coerceIn(1, 100)
        val next = if (direction > 0) {
            (percent / 5 + 1) * 5
        } else {
            ((percent + 4) / 5 - 1) * 5
        }.coerceIn(1, 100)
        val from = lastLevel
            ?: runCatching { MitvPanel.level(context) }.getOrNull()
            ?: stored(context)
            ?: percent
        return runCatching {
            MitvPanel.setBacklight(context, from, next)
            lastLevel = next
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LEVEL, next)
                .apply()
            Log.i(TAG, "mitv brightness $from -> $next")
            next / 100f
        }.onFailure {
            Log.w(TAG, "mitv backlight step failed: $it")
        }.getOrNull()
    }

    private fun stored(context: Context): Int? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LEVEL, -1)
        return value.takeIf { it in 1..100 }
    }

    private fun isXiaomi(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo("com.xiaomi.mitv.settings", 0)
        }.isSuccess
}
