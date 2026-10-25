package app.cozytv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

data class DesktopEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemDefault: Boolean,
) {
    val key: String get() = "$packageName/$activityName"
}

object DefaultDesktop {
    fun entries(context: Context): List<DesktopEntry> {
        val manager = context.packageManager
        val homeIntent = homeIntent()
        val current = manager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return manager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter {
                it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled
            }
            .map {
                val info = it.activityInfo
                DesktopEntry(
                    packageName = info.packageName,
                    activityName = info.name,
                    label = displayLabel(
                        context,
                        info.packageName,
                        info.loadLabel(manager).toString().ifBlank { info.packageName },
                    ),
                    isSystemDefault = info.packageName == current?.activityInfo?.packageName &&
                        info.name == current.activityInfo.name,
                )
            }
            .distinctBy(DesktopEntry::key)
            .sortedWith(
                compareByDescending<DesktopEntry> { it.packageName == context.packageName }
                    .thenByDescending(DesktopEntry::isSystemDefault)
                    .thenBy(DesktopEntry::label),
            )
            .toList()
    }

    fun isCozyDefault(context: Context): Boolean = runCatching {
        val resolved = context.packageManager.resolveActivity(
            homeIntent(),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        resolved?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    fun open(context: Context, entry: DesktopEntry): Boolean {
        return launch(context, entry)
    }

    fun openStock(context: Context): Boolean {
        val stock = stockEntry(context) ?: return false
        return open(context, stock)
    }

    fun restoreStock(context: Context): Boolean {
        return openStock(context)
    }

    private fun stockEntry(context: Context): DesktopEntry? {
        val desktops = entries(context)
        return desktops.firstOrNull {
            it.packageName != context.packageName && it.isSystemDefault
        } ?: desktops.firstOrNull {
            it.packageName != context.packageName
        }
    }

    private fun launch(context: Context, entry: DesktopEntry): Boolean {
        return runCatching {
            context.startActivity(
                homeIntent()
                    .setComponent(ComponentName(entry.packageName, entry.activityName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.recoverCatching {
            Log.i(TAG, "${entry.key} is protected; trying the package entry", it)
            if (!startPackageFallback(context, entry.packageName)) throw it
            true
        }.getOrDefault(false)
    }

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)

    private fun displayLabel(context: Context, packageName: String, fallback: String): String =
        when (packageName) {
            context.packageName -> "CozyTV"
            "com.mitv.tvhome" -> "小米电视桌面"
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            -> "Android TV 桌面"
            else -> fallback
        }

    private fun startPackageFallback(context: Context, packageName: String): Boolean {
        val fallback = when (packageName) {
            "com.mitv.tvhome" -> ComponentName(
                packageName,
                "com.mitv.tvhome.MainActivity",
            )
            else -> context.packageManager.getLaunchIntentForPackage(packageName)?.component
                ?: return false
        }
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setComponent(fallback)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    private const val TAG = "CozyTV.DefaultDesktop"
}
