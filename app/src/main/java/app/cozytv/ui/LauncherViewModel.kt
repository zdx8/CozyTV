package app.cozytv.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cozytv.R
import app.cozytv.data.AppOrder
import app.cozytv.data.BingWallpaper
import app.cozytv.data.BingWallpaperRepository
import app.cozytv.data.LauncherPreferences
import app.cozytv.data.LauncherRepository
import app.cozytv.data.LiveChannel
import app.cozytv.data.LivePlaylistRepository
import app.cozytv.data.SourceRepository
import app.cozytv.data.SourceKind
import app.cozytv.data.TvApp
import app.cozytv.data.TvSource
import app.cozytv.data.WallpaperStyle
import app.cozytv.data.WeatherInfo
import app.cozytv.data.WeatherRepository
import app.cozytv.guard.HomeAccessibilityControl
import com.tvlive.tv.TVLiveEmbedded
import com.tvlive.tv.data.store.SubscriptionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.cozytv.system.XiaomiBrightness
import app.cozytv.system.DefaultDesktop
import app.cozytv.update.AppUpdateState
import app.cozytv.update.AppUpdater
import app.cozytv.update.InstallLaunchResult
import app.cozytv.update.UpdateCheckResult

private val YOUTUBE_PACKAGES = setOf(
    "com.google.android.youtube.tv",
    "com.google.android.youtube.tvunplugged",
    "com.google.android.youtube",
)
private const val TV_SETTINGS = "com.android.tv.settings"
private const val TV_REMOTE_SERVICE = "com.google.android.tv.remote.service"

/**
 * Prefix of the subscription names CozyTV writes into TVLive's store.
 * Entries carrying it are owned by CozyTV and are pruned when the user
 * removes the source here. (Kept identical to the names used before
 * the reconciliation logic so existing installs clean up as well.)
 */
private const val COZY_SUBSCRIPTION_PREFIX = "CozyTV 源 "
private val HOME_DEFAULT_EXCLUDED = setOf(
    TV_SETTINGS,
    TV_REMOTE_SERVICE,
    "com.xiaomi.mitv.settings",
    "com.xiaomi.mitv.appstore",
    "com.cleanyourtv.appstore",
)

enum class LauncherScreen {
    Home,
    Apps,
    Inputs,
    DeviceInfo,
    LiveSource,
    Settings,
}

/**
 * A transient toast shown on the home screen.
 *
 * The [id] makes every notice a distinct state value. With a plain
 * String the second identical message (e.g. "已收藏" twice in a row)
 * was swallowed by the structural equality of `mutableStateOf` and the
 * user never saw the second confirmation.
 */
data class LauncherNotice(val id: Long, val text: String)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = LauncherPreferences(application)
    private var loadJob: Job? = null
    private var wallpaperJob: Job? = null
    private var wallpaperGalleryStart = prefs.wallpaperGalleryStart
    private var weatherJob: Job? = null
    private var liveJob: Job? = null
    private var liveRetryJob: Job? = null
    private var updateJob: Job? = null
    private var brightnessJob: Job? = null
    private var liveGroups: List<List<LiveChannel>> = emptyList()
    private var liveGroupUrls: List<String> = emptyList()
    private var liveChannelIndex = 0
    private var defaultSourceLaunchHandled = false

    var screen by mutableStateOf(LauncherScreen.Home)
        private set
    var apps by mutableStateOf<List<TvApp>>(emptyList())
        private set
    var sources by mutableStateOf<List<TvSource>>(emptyList())
        private set
    var defaultSourceId by mutableStateOf(prefs.defaultSourceId)
        private set
    var bingWallpaper by mutableStateOf<BingWallpaper?>(null)
        private set
    var bingWallpaperGallery by mutableStateOf<List<BingWallpaper>>(emptyList())
        private set
    var weather by mutableStateOf<WeatherInfo?>(null)
        private set
    var liveChannel by mutableStateOf<LiveChannel?>(null)
        private set
    var liveLoading by mutableStateOf(true)
        private set
    var liveMessage by mutableStateOf<String?>(null)
        private set

    /** Save summary waiting to be merged into the next [liveMessage]. */
    private var pendingSaveSummary: String? = null
    var liveSourceIndex by mutableIntStateOf(0)
        private set
    var liveSourceCount by mutableIntStateOf(0)
        private set
    var liveAttempt by mutableIntStateOf(0)
        private set
    var liveSourceRevision by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var favorites by mutableStateOf(prefs.favorites)
        private set
    var recents by mutableStateOf(prefs.recents)
        private set
    var wallpaper by mutableStateOf(prefs.wallpaper)
        private set
    var showNames by mutableStateOf(prefs.showNames)
        private set
    var favoritesGrid by mutableStateOf(prefs.favoritesGrid)
        private set
    var hiddenKeys by mutableStateOf(prefs.hiddenApps)
        private set
    var showHiddenApps by mutableStateOf(prefs.showHiddenApps)
        private set
    var clock24Hour by mutableStateOf(prefs.clock24Hour)
        private set
    var homeGuardEnabled by mutableStateOf(
        HomeAccessibilityControl.isEnabled(application),
    )
        private set
    var brightness by mutableFloatStateOf(prefs.brightness)
        private set
    var brightnessHardware by mutableStateOf(false)
        private set
    var controlCenterOpen by mutableStateOf(false)
        private set
    var updateState by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
        private set
    var notice by mutableStateOf<LauncherNotice?>(null)
        private set
    private var noticeSeq = 0L

    private fun notify(text: String) {
        noticeSeq += 1
        notice = LauncherNotice(noticeSeq, text)
    }
    val appVersionName: String = runCatching {
        getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0)
            .versionName
    }.getOrNull().orEmpty()

    /**
     * Derived lists. `derivedStateOf` keeps the filter/sort work out of
     * the recomposition path: the home screen recomposes every 30 s for
     * the clock, and the old plain getters re-sorted every installed app
     * on each of those passes.
     */
    val visibleApps: List<TvApp> by derivedStateOf {
        apps.filter { it.key !in hiddenKeys }
    }

    val displayedApps: List<TvApp> by derivedStateOf {
        apps.filter { it.key !in hiddenKeys || showHiddenApps }
    }

    val homeApps: List<TvApp> by derivedStateOf {
        val limit = if (favoritesGrid) 8 else 4
        val rawPicked = AppOrder.home(
            apps = visibleApps,
            favorites = favorites,
            recents = recents,
            limit = limit,
        )
        val picked = rawPicked.filter {
            it.key in favorites || it.packageName !in HOME_DEFAULT_EXCLUDED
        }
        // The grid mode shows 8 tiles, so the home row must be able to
        // fill all 8. The old hard-coded 4 made the second row show
        // placeholder tiles even when the TV had dozens of apps.
        if (picked.size >= limit) return@derivedStateOf picked.take(limit)

        val seen = picked.mapTo(mutableSetOf()) { it.key }
        val defaults = visibleApps
            .filter { it.packageName !in HOME_DEFAULT_EXCLUDED }
            .sortedWith(compareByDescending<TvApp> { it.isLeanback }.thenBy { it.label })
        val fill = defaults
            .filter { seen.add(it.key) }
            .take((limit - picked.size).coerceAtLeast(0))
        (picked + fill).take(limit)
    }

    init {
        weather = WeatherRepository.cached(getApplication())
        viewModelScope.launch {
            val hardwareLevel = withContext(Dispatchers.Default) {
                XiaomiBrightness.level(getApplication())
            }
            if (hardwareLevel != null) {
                brightness = hardwareLevel
                brightnessHardware = true
            }
        }
        refreshBingWallpapers()
        refreshWeather(force = true)
        refreshLiveSource()
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(30 * 60 * 1000L)
                refreshBingWallpapers()
                refreshWeather()
            }
        }
    }

    fun refreshDailyContent() {
        refreshBingWallpapers()
        refreshWeather()
    }

    fun refreshBingWallpapers(force: Boolean = false) {
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            val gallery = BingWallpaperRepository.loadGallery(
                context = getApplication(),
                count = 3,
                force = force,
                startIndex = wallpaperGalleryStart,
            )
            if (gallery.isNotEmpty()) {
                bingWallpaperGallery = gallery
                val selected = gallery.firstOrNull {
                    it.date == prefs.selectedWallpaperDate
                } ?: gallery.first()
                bingWallpaper = selected
                if (selected.date.isNotBlank()) {
                    prefs.selectedWallpaperDate = selected.date
                }
            }
        }
    }

    fun rotateBingWallpaper() {
        wallpaperGalleryStart = (wallpaperGalleryStart + 3) % 8
        prefs.wallpaperGalleryStart = wallpaperGalleryStart
        refreshBingWallpapers(force = true)
    }

    fun selectBingWallpaper(wallpaper: BingWallpaper) {
        bingWallpaper = wallpaper
        prefs.selectedWallpaperDate = wallpaper.date
    }

    fun refreshWeather(force: Boolean = false) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            WeatherRepository.load(getApplication(), force)?.let {
                weather = it
            }
        }
    }

    fun refreshLiveSource() {
        liveJob?.cancel()
        liveRetryJob?.cancel()
        liveLoading = true
        liveMessage = null
        liveGroups = emptyList()
        liveGroupUrls = emptyList()
        liveSourceCount = 0
        liveSourceIndex = 0
        liveChannelIndex = 0
        liveJob = viewModelScope.launch {
            val urls = prefs.livePlaylistUrls
            val groups = mutableListOf<List<LiveChannel>>()
            val groupUrls = mutableListOf<String>()
            var failed = 0
            var firstError: String? = null
            if (urls.isEmpty()) {
                groups += listOf(LivePlaylistRepository.demoChannel())
                groupUrls += ""
            } else {
                val loaded = coroutineScope {
                    urls.map { url ->
                        async {
                            // runCatching also catches CancellationException,
                            // which would keep a cancelled download alive
                            // until its socket timeout expired.
                            try {
                                Result.success(
                                    LivePlaylistRepository.loadChannels(getApplication(), url).take(12),
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                        }
                    }.awaitAll()
                }
                loaded.forEachIndexed { index, result ->
                    result.onSuccess { channels ->
                        if (channels.isNotEmpty()) {
                            groups += channels
                            groupUrls += urls[index]
                        }
                        else {
                            failed += 1
                            firstError = firstError ?: "播放列表为空"
                        }
                    }.onFailure { error ->
                        failed += 1
                        if (firstError == null) firstError = error.message
                    }
                }
            }
            val loadMessage: String? = if (groups.isEmpty()) {
                groups += listOf(LivePlaylistRepository.demoChannel())
                // Keep liveGroups and liveGroupUrls aligned: the active
                // TVLive subscription is picked with getOrNull(index).
                groupUrls += ""
                "所有直播源都不可用，已切换到演示频道" +
                    (firstError?.let { " · $it" } ?: "")
            } else if (failed > 0) {
                "已跳过 $failed 个不可用的直播源"
            } else {
                null
            }
            // The save summary and the load summary are merged here.
            // Writing the save message directly used to race with this
            // coroutine, and which one survived was a coin flip.
            liveMessage = listOfNotNull(pendingSaveSummary, loadMessage)
                .joinToString(" · ")
                .ifEmpty { null }
            pendingSaveSummary = null
            liveGroups = groups
            liveGroupUrls = groupUrls
            liveSourceCount = groups.size
            liveSourceIndex = 0
            liveChannelIndex = 0
            liveChannel = groups.first().first()
            liveAttempt += 1
            liveLoading = false
            // Push the (reconciled) subscription list and the active
            // source into TVLive now that the groups are known — the
            // previous call happened before the refresh and therefore
            // pointed at the previous selection.
            syncTvLiveSubscriptions()
        }
    }

    fun onLivePlaybackError() {
        val group = liveGroups.getOrNull(liveSourceIndex) ?: return
        when {
            liveChannelIndex + 1 < group.size -> {
                liveChannelIndex += 1
                liveChannel = group[liveChannelIndex]
                liveAttempt += 1
                liveMessage = "当前频道不可用，正在尝试同一来源的其他频道"
            }
            liveSourceIndex + 1 < liveGroups.size -> {
                liveSourceIndex += 1
                liveChannelIndex = 0
                liveChannel = liveGroups[liveSourceIndex].first()
                liveAttempt += 1
                liveMessage = "直播源 ${liveSourceIndex} 不可用，已切换到源 ${liveSourceIndex + 1}"
            }
            else -> {
                liveMessage = "所有直播源暂不可用，8 秒后重试"
                liveRetryJob?.cancel()
                liveRetryJob = viewModelScope.launch {
                    delay(8_000)
                    if (liveGroups.isNotEmpty()) {
                        liveSourceIndex = 0
                        liveChannelIndex = 0
                        liveChannel = liveGroups.first().first()
                        liveAttempt += 1
                        liveMessage = "正在重新连接直播源"
                    }
                }
            }
        }
    }

    fun saveLiveSources(urls: List<String>, navigateHome: Boolean = true): Boolean {
        val clean = urls.map(String::trim).filter(String::isNotEmpty).distinct()
        if (clean.any { !it.startsWith("http://") && !it.startsWith("https://") }) {
            liveMessage = "直播源必须以 http:// 或 https:// 开头"
            return false
        }
        prefs.livePlaylistUrls = clean
        syncTvLiveSubscriptions()
        liveSourceRevision += 1
        // The summary is merged with the load result inside
        // refreshLiveSource(), so both messages survive.
        pendingSaveSummary = if (clean.isEmpty()) {
            "已使用内置演示源"
        } else {
            "已保存 ${clean.size} 个直播源"
        }
        refreshLiveSource()
        if (navigateHome) screen = LauncherScreen.Home
        return true
    }

    fun saveLiveSource(url: String): Boolean = saveLiveSources(listOf(url))

    val liveSourceUrl: String
        get() = prefs.livePlaylistUrl

    val liveSourceUrls: List<String>
        get() = prefs.livePlaylistUrls

    fun showLiveSource() {
        screen = LauncherScreen.LiveSource
    }

    fun refresh() {
        loadJob?.cancel()
        loading = true
        loadJob = viewModelScope.launch {
            apps = LauncherRepository.load(getApplication())
            // TvInputManagerService calls are synchronous binder IPCs —
            // they must not run on the main thread.
            sources = withContext(Dispatchers.IO) {
                SourceRepository.load(getApplication())
            }
            defaultSourceId = sources.firstOrNull { it.id == prefs.defaultSourceId }?.id
                ?: SourceRepository.TV_LIVE_ID
            maybeLaunchDefaultSource()
            if (!prefs.favoritesInitialized) {
                val defaults = apps
                    .filter { app -> app.packageName in YOUTUBE_PACKAGES }
                    .mapTo(linkedSetOf(), TvApp::key)
                favorites = defaults
                prefs.favorites = defaults
                prefs.favoritesInitialized = true
                prefs.favoritesCurated = true
            } else if (!prefs.favoritesCurated) {
                // Older builds auto-added the first three leanback apps. On stock
                // Android TV that meant TV Settings and the remote service.
                val legacyPackages = setOf(TV_SETTINGS, TV_REMOTE_SERVICE)
                val looksLikeLegacyDefaults = favorites.size == 3 &&
                    favorites.any { key -> YOUTUBE_PACKAGES.any { key.startsWith("$it/") } } &&
                    favorites.any { key -> legacyPackages.any { key.startsWith("$it/") } }
                if (looksLikeLegacyDefaults) {
                    val pruned = favorites
                        .filterNot { key -> legacyPackages.any { key.startsWith("$it/") } }
                        .toSet()
                    favorites = pruned
                    prefs.favorites = pruned
                }
                prefs.favoritesCurated = true
            }
            loading = false
        }
    }

    fun showHome() {
        screen = LauncherScreen.Home
        controlCenterOpen = false
    }

    fun showApps() {
        screen = LauncherScreen.Apps
    }

    fun showSettings() {
        screen = LauncherScreen.Settings
    }

    fun openStockDesktop() {
        if (!DefaultDesktop.openStock(getApplication())) {
            notify("未找到可用的原厂桌面")
        }
    }

    fun openHomeGuardSettings() {
        if (launchIfResolved(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) return
        openSystemSettings()
    }

    fun refreshHomeGuard() {
        homeGuardEnabled = HomeAccessibilityControl.isEnabled(getApplication())
    }

    fun restoreStockDesktop() {
        if (!DefaultDesktop.restoreStock(getApplication())) {
            notify("未找到可用的原厂桌面")
        }
    }

    fun showInputs() {
        screen = LauncherScreen.Inputs
    }

    fun openTvLive() {
        TVLiveEmbedded.active = true
        syncTvLiveSubscriptions()
        runCatching {
            getApplication<Application>().startActivity(
                Intent()
                    .setClassName(getApplication(), "com.tvlive.tv.ui.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(TVLiveEmbedded.EXTRA_EMBEDDED, true),
            )
        }.onFailure {
            notify("TVLive 启动失败")
        }
    }

    /**
     * Mirror CozyTV's live sources into TVLive's subscription store.
     *
     * The old version only ever *added* entries, so a source the user
     * deleted in CozyTV kept playing inside TVLive. Now the store is
     * reconciled: stale CozyTV entries are dropped and the currently
     * selected source is marked active (empty when there is none).
     */
    private fun syncTvLiveSubscriptions() {
        runCatching {
            val context = getApplication<Application>()
            val target = prefs.livePlaylistUrls
            val known = SubscriptionStore.list(context)
            known.filter { entry ->
                entry.name.startsWith(COZY_SUBSCRIPTION_PREFIX) && entry.url !in target
            }.forEach { entry ->
                SubscriptionStore.remove(context, entry.url)
            }
            target.forEachIndexed { index, url ->
                if (known.none { it.url == url }) {
                    SubscriptionStore.add(
                        context,
                        url,
                        "$COZY_SUBSCRIPTION_PREFIX${index + 1}",
                    )
                }
            }
            val active = liveGroupUrls.getOrNull(liveSourceIndex)
            SubscriptionStore.setActive(context, active ?: "")
        }
    }

    fun showDeviceInfo() {
        screen = LauncherScreen.DeviceInfo
    }

    fun launch(app: TvApp) {
        runCatching { app.launch(getApplication()) }
            .onSuccess {
                recents = (listOf(app.key) + recents.filterNot { it == app.key }).take(12)
                prefs.recents = recents
                screen = LauncherScreen.Home
            }
            .onFailure {
                Toast.makeText(getApplication(), app.label, Toast.LENGTH_SHORT).show()
            }
    }

    fun playSource(source: TvSource) {
        defaultSourceId = source.id
        prefs.defaultSourceId = source.id
        prefs.defaultSourceLastLaunchAt = System.currentTimeMillis()
        if (source.kind == SourceKind.TvLive) {
            screen = LauncherScreen.Home
        } else {
            runCatching { source.launch(getApplication()) }
                .onFailure {
                    Toast.makeText(getApplication(), source.label, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun maybeLaunchDefaultSource() {
        if (defaultSourceLaunchHandled) return
        defaultSourceLaunchHandled = true
        val source = sources.firstOrNull { it.id == defaultSourceId } ?: return
        if (source.kind == SourceKind.TvLive) return
        val elapsed = System.currentTimeMillis() - prefs.defaultSourceLastLaunchAt
        if (elapsed < 30_000L) return
        prefs.defaultSourceLastLaunchAt = System.currentTimeMillis()
        viewModelScope.launch {
            delay(900)
            if (screen == LauncherScreen.Home) {
                runCatching { source.launch(getApplication()) }
            }
        }
    }

    fun toggleFavorite(app: TvApp) {
        val updated = favorites.toMutableSet()
        val added = if (updated.remove(app.key)) false else {
            updated += app.key
            true
        }
        favorites = updated
        prefs.favorites = updated
        notify(
            getApplication<Application>().getString(
                if (added) R.string.favorite_added else R.string.favorite_removed,
            ),
        )
    }

    fun dismissNotice() {
        notice = null
    }

    fun applyWallpaper(value: WallpaperStyle) {
        wallpaper = value
        prefs.wallpaper = value
    }

    fun updateShowNames(value: Boolean) {
        showNames = value
        prefs.showNames = value
    }

    fun updateFavoritesGrid(value: Boolean) {
        favoritesGrid = value
        prefs.favoritesGrid = value
    }

    fun isHidden(app: TvApp): Boolean = app.key in hiddenKeys

    fun toggleHidden(app: TvApp) {
        val next = hiddenKeys.toMutableSet()
        val hidden = if (!next.add(app.key)) {
            next.remove(app.key)
            false
        } else {
            true
        }
        hiddenKeys = next
        prefs.hiddenApps = next
        notify(if (hidden) "已隐藏 ${app.label}" else "已取消隐藏 ${app.label}")
    }

    fun updateShowHiddenApps(value: Boolean) {
        showHiddenApps = value
        prefs.showHiddenApps = value
    }

    fun uninstall(app: TvApp) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            notify("无法打开卸载界面")
        }
    }

    fun updateBrightness(value: Float) {
        brightness = value.coerceIn(0.01f, 1f)
        prefs.brightness = brightness
    }

    fun adjustBrightness(delta: Int) {
        val hardware = XiaomiBrightness.adjust(getApplication(), delta)
        if (hardware != null) {
            brightness = hardware.coerceIn(0.01f, 1f)
            brightnessHardware = true
            prefs.brightness = brightness
            return
        }
        val base = if (brightness < 0f) 0.8f else brightness
        val next = (base + delta * 0.05f).coerceIn(0.01f, 1f)
        brightness = next
        brightnessHardware = false
        prefs.brightness = next
    }

    fun refreshSystemBrightness() {
        // The control centre polls this every second. Without cancelling
        // the previous job each poll added another coroutine that stayed
        // blocked in the vendor binder transaction.
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            val hardware = withContext(Dispatchers.Default) {
                XiaomiBrightness.level(getApplication())
            }
            if (hardware != null) {
                brightness = hardware.coerceIn(0.01f, 1f)
                brightnessHardware = true
            }
        }
    }

    fun checkForUpdates() {
        updateJob?.cancel()
        updateState = AppUpdateState.Checking
        updateJob = viewModelScope.launch {
            updateState = when (val result = AppUpdater.check()) {
                is UpdateCheckResult.Available -> AppUpdateState.Available(result.info)
                is UpdateCheckResult.UpToDate -> AppUpdateState.UpToDate(result.remoteVersion)
                is UpdateCheckResult.Failed -> AppUpdateState.Failed(result.message)
            }
        }
    }

    fun downloadUpdate() {
        val available = updateState as? AppUpdateState.Available ?: return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            try {
                val file = AppUpdater.download(
                    context = getApplication(),
                    info = available.info,
                ) { percent ->
                    updateState = AppUpdateState.Downloading(available.info, percent)
                }
                val ready = AppUpdateState.Ready(available.info, file)
                updateState = ready
                launchReadyInstaller(ready)
            } catch (cancelled: CancellationException) {
                updateState = AppUpdateState.Idle
                throw cancelled
            } catch (error: Throwable) {
                updateState = AppUpdateState.Failed(
                    error.message ?: "下载更新失败，请稍后重试",
                )
            }
        }
    }

    fun openUpdateInstaller() {
        val ready = updateState as? AppUpdateState.Ready ?: return
        launchReadyInstaller(ready)
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        updateState = AppUpdateState.Idle
    }

    fun dismissUpdate() {
        if (updateState is AppUpdateState.Checking ||
            updateState is AppUpdateState.Downloading
        ) {
            return
        }
        updateState = AppUpdateState.Idle
    }

    private fun launchReadyInstaller(ready: AppUpdateState.Ready) {
        when (AppUpdater.launchInstaller(getApplication(), ready.file)) {
            InstallLaunchResult.Started -> updateState = AppUpdateState.Idle
            InstallLaunchResult.PermissionRequired ->
                updateState = ready.copy(permissionRequired = true)
            InstallLaunchResult.Failed ->
                updateState = AppUpdateState.Failed("无法打开系统安装器")
        }
    }

    fun openControlCenter() {
        controlCenterOpen = true
    }

    fun closeControlCenter() {
        controlCenterOpen = false
    }

    fun updateClock24Hour(value: Boolean) {
        clock24Hour = value
        prefs.clock24Hour = value
    }

    fun openHomeSettings() {
        if (launchIfResolved(Intent(Settings.ACTION_HOME_SETTINGS))) return
        if (launchIfResolved(Intent(Settings.ACTION_SETTINGS))) return
        notify("系统没有提供默认桌面设置页")
    }

    fun openSystemSettings() {
        if (launchIfResolved(Intent(Settings.ACTION_SETTINGS))) return
        if (launchIfResolved(
                Intent("com.xiaomi.mitv.settings.SETTINGS")
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setClassName(
                        "com.xiaomi.mitv.settings",
                        "com.xiaomi.mitv.settings.entry.MainActivity",
                    ),
            )
        ) {
            return
        }
        notify("无法打开系统设置")
    }

    fun openWifiSettings() {
        if (launchIfResolved(Intent(Settings.ACTION_WIFI_SETTINGS))) return
        if (launchIfResolved(Intent(Settings.ACTION_WIRELESS_SETTINGS))) return
        if (launchIfResolved(
                Intent("com.xiaomi.mitv.settings.WIFI_SETTINGS")
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setClassName(
                        "com.xiaomi.mitv.settings",
                        "com.xiaomi.mitv.settings.wifi.MitvWifiActivity",
                    ),
            )
        ) {
            return
        }
        openSystemSettings()
    }

    private fun launchIfResolved(intent: Intent): Boolean {
        val app = getApplication<Application>()
        if (intent.component == null) {
            if (runCatching {
                    app.packageManager.resolveActivity(intent, 0)
                }.getOrNull() == null
            ) {
                return false
            }
        }
        return runCatching {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun openScreensaverSettings() {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            openSystemSettings()
        }
    }
}

