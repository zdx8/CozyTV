package app.cozytv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap

data class TvApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isLeanback: Boolean,
    val icon: Bitmap?,
    val banner: Bitmap?,
    val accentColor: Int,
) {
    val key: String get() = "$packageName/$activityName"

    fun launch(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(
                if (isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER
                else Intent.CATEGORY_LAUNCHER,
            )
            .setComponent(ComponentName(packageName, activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }
}

enum class WallpaperStyle {
    Dawn,
    Aurora,
    Ember,
}

