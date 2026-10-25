package app.cozytv.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object LauncherRepository {
    private const val CATEGORY_APPSTORE_LAUNCHER = "android.intent.category.APPSTORE_LAUNCHER"
    private val EXCLUDED_PACKAGES = setOf(
        "com.android.vending",
        "com.mitv.tvhome",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "com.android.tv.launcher",
        "com.cleanyourtv.mihome",
    )
    private val KNOWN_TV_MARKETS = listOf(
        "com.xiaomi.mitv.appstore",
        "com.tcl.appmarket2",
        "com.tcl.appstore",
        "com.helios.appstore",
    )

    @SuppressLint("QueryPermissionsNeeded")
    suspend fun load(context: Context): List<TvApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val found = LinkedHashMap<String, Pair<ActivityInfo, Boolean>>()

        val categories = listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER,
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_APP_MARKET,
            CATEGORY_APPSTORE_LAUNCHER,
        )
        categories.forEachIndexed { index, category ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .forEach { info ->
                    val packageName = info.activityInfo.packageName
                    if (info.activityInfo.applicationInfo.enabled &&
                        packageName !in EXCLUDED_PACKAGES &&
                        packageName !in found
                    ) {
                        found[packageName] = info.activityInfo to (index == 0)
                    }
                }
        }
        for (packageName in KNOWN_TV_MARKETS) {
            if (packageName in found || packageName in EXCLUDED_PACKAGES) continue
            val component = pm.getLaunchIntentForPackage(packageName)?.component ?: continue
            val info = runCatching { pm.getActivityInfo(component, 0) }.getOrNull() ?: continue
            if (info.exported && info.applicationInfo.enabled) found[packageName] = info to false
        }
        // Some vendor launchers expose apps with categories we do not know.
        // Add any remaining launchable installed package so a stock TV's
        // preinstalled apps still show up in the all-apps grid.
        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { appInfo ->
            val packageName = appInfo.packageName
            if (!appInfo.enabled ||
                packageName in found ||
                packageName in EXCLUDED_PACKAGES
            ) {
                return@forEach
            }
            val component = pm.getLaunchIntentForPackage(packageName)?.component ?: return@forEach
            val info = runCatching { pm.getActivityInfo(component, 0) }.getOrNull() ?: return@forEach
            if (info.exported) found[packageName] = info to false
        }
        found.remove(context.packageName)

        found.values.mapNotNull { (info, leanback) ->
            runCatching { readApp(pm, info, leanback) }.getOrNull()
        }.sortedWith { a, b ->
            Collator.getInstance(Locale.getDefault()).compare(a.label, b.label)
        }
    }

    private fun readApp(pm: PackageManager, info: ActivityInfo, leanback: Boolean): TvApp {
        val appInfo = info.applicationInfo
        val resources = pm.getResourcesForApplication(appInfo)
        val bannerId = info.banner.takeIf { it != 0 } ?: appInfo.banner
        val banner = loadDrawable(pm, appInfo, bannerId)?.let(::toBitmap)
        val icon = loadDrawable(pm, appInfo, info.icon.takeIf { it != 0 } ?: appInfo.icon)
            ?.let(::toBitmap)
        val accent = icon?.let(::dominantColor) ?: 0xFF222943.toInt()
        return TvApp(
            packageName = info.packageName,
            activityName = info.name,
            label = info.loadLabel(pm).toString().ifBlank { info.packageName },
            isLeanback = leanback,
            icon = icon,
            banner = banner,
            accentColor = accent,
        )
    }

    private fun loadDrawable(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        resId: Int,
    ): Drawable? {
        if (resId == 0) return null
        return runCatching {
            pm.getResourcesForApplication(appInfo).getDrawableForDensity(
                resId,
                android.util.DisplayMetrics.DENSITY_XXXHIGH,
                null,
            )
        }.getOrNull() ?: runCatching { appInfo.loadIcon(pm) }.getOrNull()
    }

    private fun toBitmap(drawable: Drawable): Bitmap {
        val bounds = drawable.intrinsicWidth to drawable.intrinsicHeight
        val sourceW = bounds.first.takeIf { it > 0 } ?: 192
        val sourceH = bounds.second.takeIf { it > 0 } ?: 192
        val scale = min(1f, min(256f / sourceW, 256f / sourceH))
        val width = max(1, (sourceW * scale).toInt())
        val height = max(1, (sourceH * scale).toInt())
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
    }

    private fun dominantColor(source: Bitmap): Int {
        val sample = Bitmap.createScaledBitmap(source, 24, 24, true)
        val counts = HashMap<Int, Int>()
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                if (Color.alpha(pixel) < 160) continue
                val r = Color.red(pixel) / 32
                val g = Color.green(pixel) / 32
                val b = Color.blue(pixel) / 32
                val bucket = (r shl 16) or (g shl 8) or b
                counts[bucket] = (counts[bucket] ?: 0) + 1
            }
        }
        val common = counts.maxByOrNull(Map.Entry<Int, Int>::value)?.key ?: return 0xFF222943.toInt()
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                if (Color.alpha(pixel) < 160) continue
                val bucket = (Color.red(pixel) / 32 shl 16) or
                    (Color.green(pixel) / 32 shl 8) or
                    (Color.blue(pixel) / 32)
                if (bucket != common) continue
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
            }
        }
        if (count == 0) return 0xFF222943.toInt()
        val color = Color.rgb(
            (red / count).toInt().coerceIn(24, 220),
            (green / count).toInt().coerceIn(24, 220),
            (blue / count).toInt().coerceIn(24, 220),
        )
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
    }
}

