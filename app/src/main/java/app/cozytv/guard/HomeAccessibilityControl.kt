package app.cozytv.guard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object HomeAccessibilityControl {
    fun isEnabled(context: Context): Boolean = runCatching {
        val target = ComponentName(context, HomeAccessibilityService::class.java)
        Settings.Secure
            .getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .filter(String::isNotBlank)
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == target }
    }.getOrDefault(false)
}
