package app.cozytv.data

import android.content.Context

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("cozy_launcher", Context.MODE_PRIVATE)

    var wallpaper: WallpaperStyle
        get() = runCatching {
            WallpaperStyle.valueOf(prefs.getString(KEY_WALLPAPER, WallpaperStyle.Dawn.name)!!)
        }.getOrDefault(WallpaperStyle.Dawn)
        set(value) = prefs.edit().putString(KEY_WALLPAPER, value.name).apply()

    var showNames: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NAMES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NAMES, value).apply()

    var favoritesGrid: Boolean
        get() = prefs.getBoolean(KEY_FAVORITES_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_FAVORITES_GRID, value).apply()

    var clock24Hour: Boolean
        get() = prefs.getBoolean(KEY_CLOCK_24, true)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK_24, value).apply()

    var brightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, -1f)
        set(value) = prefs.edit().putFloat(KEY_BRIGHTNESS, value).apply()

    var hiddenApps: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_APPS, value).apply()

    var showHiddenApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN_APPS, value).apply()

    var favoritesInitialized: Boolean
        get() = prefs.getBoolean(KEY_FAVORITES_INITIALIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_FAVORITES_INITIALIZED, value).apply()

    var favoritesCurated: Boolean
        get() = prefs.getBoolean(KEY_FAVORITES_CURATED, false)
        set(value) = prefs.edit().putBoolean(KEY_FAVORITES_CURATED, value).apply()

    var favorites: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_FAVORITES, value).apply()

    var recents: List<String>
        get() = prefs.getString(KEY_RECENTS, "")
            .orEmpty()
            .split('|')
            .filter(String::isNotBlank)
        set(value) = prefs.edit().putString(KEY_RECENTS, value.take(12).joinToString("|")).apply()

    var livePlaylistUrl: String
        get() = livePlaylistUrls.firstOrNull().orEmpty()
        set(value) {
            livePlaylistUrls = listOf(value.trim()).filter(String::isNotEmpty)
        }

    var livePlaylistUrls: List<String>
        get() = (prefs.getString(KEY_LIVE_PLAYLISTS, null)
            ?: prefs.getString(KEY_LIVE_PLAYLIST, ""))
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        set(value) {
            val clean = value.map(String::trim).filter(String::isNotEmpty).distinct()
            prefs.edit()
                .putString(KEY_LIVE_PLAYLISTS, clean.joinToString("\n"))
                .putString(KEY_LIVE_PLAYLIST, clean.firstOrNull().orEmpty())
                .apply()
        }

    var defaultSourceId: String
        get() = prefs.getString(KEY_DEFAULT_SOURCE, SourceRepository.TV_LIVE_ID)
            .orEmpty()
            .ifBlank { SourceRepository.TV_LIVE_ID }
        set(value) = prefs.edit().putString(KEY_DEFAULT_SOURCE, value).apply()

    var defaultSourceLastLaunchAt: Long
        get() = prefs.getLong(KEY_DEFAULT_SOURCE_LAST_LAUNCH, 0L)
        set(value) = prefs.edit().putLong(KEY_DEFAULT_SOURCE_LAST_LAUNCH, value).apply()

    private companion object {
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_SHOW_NAMES = "show_names"
        const val KEY_FAVORITES_GRID = "favorites_grid"
        const val KEY_CLOCK_24 = "clock_24"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_HIDDEN_APPS = "hidden_apps"
        const val KEY_SHOW_HIDDEN_APPS = "show_hidden_apps"
        const val KEY_FAVORITES_INITIALIZED = "favorites_initialized"
        const val KEY_FAVORITES_CURATED = "favorites_curated"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_LIVE_PLAYLIST = "live_playlist"
        const val KEY_LIVE_PLAYLISTS = "live_playlists"
        const val KEY_DEFAULT_SOURCE = "default_source"
        const val KEY_DEFAULT_SOURCE_LAST_LAUNCH = "default_source_last_launch"
    }
}

