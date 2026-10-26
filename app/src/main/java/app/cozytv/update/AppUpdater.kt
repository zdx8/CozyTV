package app.cozytv.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import app.cozytv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

private const val TAG = "CozyTV.Update"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 20_000
private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
private const val MAX_APK_BYTES = 200L * 1024 * 1024
private const val RELEASE_ASSET_SUFFIX = "-release.apk"
private const val UPDATE_APK_NAME = "cozytv-update.apk"
private const val MAX_REDIRECTS = 5

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val apiDownloadUrl: String? = null,
    val sizeBytes: Long,
    val notes: String,
    val sha256: String?,
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data class UpToDate(val remoteVersion: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

enum class InstallLaunchResult {
    Started,
    PermissionRequired,
    Failed,
}

/**
 * Manual update flow adapted from TVLive: GitHub Releases -> APK download ->
 * Android package installer. Android does not allow a regular launcher to
 * install an update silently, so the final system confirmation remains.
 */
object AppUpdater {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (endpoint.isEmpty()) {
            return@withContext UpdateCheckResult.Failed("更新通道尚未配置")
        }
        try {
            val info = fetchManifest(endpoint)
            if (compareVersions(info.versionName, BuildConfig.VERSION_NAME) > 0) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate(info.versionName)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.i(TAG, "Update check failed", error)
            UpdateCheckResult.Failed(friendlyError(error))
        }
    }

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: suspend (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, UPDATE_APK_NAME)
        val candidates = listOfNotNull(info.apiDownloadUrl, info.downloadUrl)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        var lastError: Throwable? = null
        for (url in candidates) {
            // Every attempt downloads into its own temporary file. The
            // old code wrote straight into the final name, so a cancelled
            // download that was still blocked in socket read could delete
            // the file a newer attempt was writing — the update then
            // failed with "下载文件不是有效 APK".
            val part = File(context.cacheDir, "$UPDATE_APK_NAME.${System.nanoTime()}.part")
            try {
                downloadFrom(context, info, url, part, onProgress)
                if (target.exists() && !target.delete()) {
                    throw IOException("无法清理旧的更新包")
                }
                if (!part.renameTo(target)) {
                    throw IOException("无法保存更新包")
                }
                return@withContext target
            } catch (cancelled: CancellationException) {
                part.delete()
                throw cancelled
            } catch (error: Throwable) {
                part.delete()
                lastError = error
                Log.w(TAG, "Update download failed from ${safeHost(url)}", error)
            }
        }
        throw lastError ?: IOException("没有可用的更新下载地址")
    }

    private suspend fun downloadFrom(
        context: Context,
        info: UpdateInfo,
        url: String,
        target: File,
        onProgress: suspend (Int) -> Unit,
    ) {
        val connection = openConnection(url).apply {
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "CozyTV/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("下载服务器返回 HTTP ${connection.responseCode}")
            }
            val declaredSize = connection.contentLength.toLong().takeIf { it > 0 }
                ?: info.sizeBytes.takeIf { it > 0 }
                ?: 0L
            if (declaredSize > MAX_APK_BYTES) {
                throw IOException("更新包大小异常")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastPercent = -1
            onProgress(0)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw IOException("更新包超过大小限制")
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        if (declaredSize > 0) {
                            val percent = (downloaded * 100 / declaredSize)
                                .toInt()
                                .coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (downloaded == 0L) throw IOException("下载到的更新包为空")
            onProgress(100)

            val expected = info.sha256
            if (!expected.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw IOException("更新包校验失败")
                }
            }
            validateApk(context, target, info)
        } finally {
            connection.disconnect()
        }
    }

    fun launchInstaller(context: Context, apk: File): InstallLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = startFirstResolved(
                context,
                listOf(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                    Intent("com.xiaomi.mitv.settings.SETTINGS").setClassName(
                        "com.xiaomi.mitv.settings",
                        "com.xiaomi.mitv.settings.entry.SecurityActivity",
                    ),
                    Intent("com.xiaomi.mitv.settings.SETTINGS").setClassName(
                        "com.xiaomi.mitv.settings",
                        "com.xiaomi.mitv.settings.entry.MainActivity",
                    ),
                ),
            )
            return if (opened) {
                InstallLaunchResult.PermissionRequired
            } else {
                InstallLaunchResult.Failed
            }
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.updatefileprovider",
                apk,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            InstallLaunchResult.Started
        }.onFailure {
            Log.e(TAG, "Unable to launch package installer", it)
        }.getOrDefault(InstallLaunchResult.Failed)
    }

    internal fun compareVersions(remote: String, local: String): Int {
        val a = parseVersion(remote)
        val b = parseVersion(local)
        val size = maxOf(a.release.size, b.release.size)
        for (index in 0 until size) {
            val x = a.release.getOrElse(index) { 0L }
            val y = b.release.getOrElse(index) { 0L }
            if (x != y) return if (x > y) 1 else -1
        }
        // SemVer rule: a pre-release is *older* than the matching release.
        // The old parser turned "0.4.0-rc.1" into [0,4,0,0,1] and
        // "0.4.0" into [0,4,0], so the RC looked newer and the release
        // looked older — users were pushed to "downgrade" and real
        // updates were never offered.
        return when {
            a.pre.isEmpty() && b.pre.isEmpty() -> 0
            a.pre.isEmpty() -> 1
            b.pre.isEmpty() -> -1
            else -> comparePrerelease(a.pre, b.pre)
        }
    }

    internal fun chooseReleaseAsset(names: List<String>): String? =
        names.firstOrNull { it.endsWith(RELEASE_ASSET_SUFFIX, ignoreCase = true) }

    /**
     * Split a version string into its numeric release part and its
     * pre-release identifiers. Handles a leading "v" in either case
     * ("V1.2.3" used to parse as 0.2.3), build metadata ("+sha") and
     * overflow-safe numeric segments.
     */
    private fun parseVersion(raw: String): ParsedVersion {
        var text = raw.trim()
        if (text.startsWith("v", ignoreCase = true)) text = text.substring(1)
        val plus = text.indexOf('+')
        if (plus >= 0) text = text.substring(0, plus)
        val dash = text.indexOf('-')
        val releaseText = if (dash >= 0) text.substring(0, dash) else text
        val preText = if (dash >= 0) text.substring(dash + 1) else ""
        val release = releaseText
            .split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .map(::parseNumericSegment)
        val pre = preText.split('.').filter { it.isNotEmpty() }
        return ParsedVersion(release, pre)
    }

    private fun parseNumericSegment(part: String): Long {
        part.toLongOrNull()?.let { return it }
        // Digits that do not fit into a Long are bigger than any real
        // version — clamp instead of silently parsing them as 0.
        if (part.all(Char::isDigit)) return Long.MAX_VALUE
        return 0L
    }

    private fun comparePrerelease(a: List<String>, b: List<String>): Int {
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val x = a.getOrNull(index) ?: return -1
            val y = b.getOrNull(index) ?: return 1
            val xn = x.toLongOrNull()
            val yn = y.toLongOrNull()
            val comparison = when {
                xn != null && yn != null -> xn.compareTo(yn)
                // Numeric identifiers always have lower precedence.
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (comparison != 0) return if (comparison > 0) 1 else -1
        }
        return 0
    }

    private data class ParsedVersion(
        val release: List<Long>,
        val pre: List<String>,
    )

    private fun fetchManifest(endpoint: String): UpdateInfo {
        val connection = openConnection(endpoint).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CozyTV/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException(
                    if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        "更新通道尚未上线（HTTP 404）"
                    } else {
                        "更新服务器返回 HTTP ${connection.responseCode}"
                    },
                )
            }
            val payload = connection.inputStream.use {
                String(readLimited(it, MAX_MANIFEST_BYTES), Charsets.UTF_8)
            }
            parseManifest(payload) ?: throw IOException("更新清单中没有可用的安装包")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseManifest(payload: String): UpdateInfo? {
        val json = JSONObject(payload)
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            val assetNames = buildList {
                for (index in 0 until assets.length()) {
                    assets.optJSONObject(index)?.stringOrNull("name")?.let(::add)
                }
            }
            val selectedName = chooseReleaseAsset(assetNames) ?: return null
            val asset = (0 until assets.length())
                .mapNotNull(assets::optJSONObject)
                .firstOrNull { it.stringOrNull("name") == selectedName }
                ?: return null
            val version = json.stringOrNull("tag_name")
                ?.trim()
                ?.removePrefix("v")
                ?.removePrefix("V")
                ?.trim()
                .orEmpty()
            if (version.isEmpty()) return null
            return UpdateInfo(
                versionName = version,
                downloadUrl = asset.stringOrNull("browser_download_url").orEmpty(),
                apiDownloadUrl = asset.stringOrNull("url")?.takeIf(::isGitHubAssetApiUrl),
                sizeBytes = asset.optLong("size", 0L),
                notes = json.stringOrNull("body").orEmpty().trim(),
                sha256 = normalizeSha256(asset.stringOrNull("digest")),
            ).takeIf { isAllowedDownloadUrl(it.downloadUrl) }
        }

        val version = json.stringOrNull("versionName")
            ?.trim()
            ?.removePrefix("v")
            ?.removePrefix("V")
            ?.trim()
            .orEmpty()
        val url = json.stringOrNull("url")?.trim().orEmpty()
        if (version.isEmpty() || !isAllowedDownloadUrl(url)) return null
        return UpdateInfo(
            versionName = version,
            downloadUrl = url,
            sizeBytes = json.optLong("size", 0L),
            notes = json.stringOrNull("notes").orEmpty().trim(),
            sha256 = normalizeSha256(json.stringOrNull("sha256")),
        )
    }

    /**
     * Read a string field, treating a JSON `null` as "absent".
     *
     * `JSONObject.optString` returns the literal text "null" for a JSON
     * null, so `takeIf(String::isNotBlank)` happily accepted it — a
     * missing digest became the string "null" and every download failed
     * the checksum comparison with a misleading message.
     */
    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key)
        if (value.isBlank()) return null
        if (value.equals("null", ignoreCase = true)) return null
        return value
    }

    /**
     * Normalise a SHA-256 digest: strip the optional "sha256:" prefix
     * (both the GitHub asset field and the custom manifest used it,
     * but only one of the two paths stripped it), reject the "null"
     * sentinel and anything that is not a 64 character hex string.
     */
    internal fun normalizeSha256(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.equals("null", ignoreCase = true)) return null
        val hex = value.removePrefix("sha256:").removePrefix("SHA256:").trim()
        if (hex.length != 64) return null
        if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return hex.lowercase()
    }

    private fun validateApk(context: Context, apk: File, info: UpdateInfo) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: throw IOException("下载文件不是有效 APK")
        if (archive.packageName != context.packageName) {
            throw IOException("更新包属于其他应用")
        }
        val archiveVersion = archive.versionName.orEmpty()
        if (archiveVersion.isBlank()) {
            // An APK without a version name cannot be verified against
            // the release info — fail instead of skipping the check.
            throw IOException("更新包缺少版本号，无法校验")
        }
        if (compareVersions(archiveVersion, info.versionName) != 0) {
            throw IOException("更新包版本与发布信息不一致")
        }
        // versionCode is what Android actually compares when installing.
        // Without this check a release with a lower versionCode reaches
        // the system installer and fails with INSTALL_FAILED_VERSION_DOWNGRADE.
        val installed = manager.getPackageInfo(context.packageName, 0)
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            installed.versionCode.toLong()
        }
        if (archiveCode <= installedCode) {
            throw IOException("更新包版本号未高于当前版本")
        }
        if (!hasSameSigner(context, manager, apk)) {
            throw IOException("更新包签名与当前安装版本不一致")
        }
    }

    /**
     * Compare the signing certificates of the installed package and the
     * downloaded APK.
     *
     * `GET_SIGNATURES` is deprecated since API 28 and is not filled in
     * for modern targets, which made the check fail (or, with key
     * rotation, compare the *oldest* certificate against the new one and
     * never match). `GET_SIGNING_CERTIFICATES` plus the certificate
     * history keeps rotation working.
     */
    private fun hasSameSigner(
        context: Context,
        manager: PackageManager,
        apk: File,
    ): Boolean = runCatching {
        val current = signingFingerprints(
            manager.getPackageInfo(context.packageName, signingFlags()),
        )
        val incoming = signingFingerprints(
            manager.getPackageArchiveInfo(apk.absolutePath, signingFlags()),
        )
        current.isNotEmpty() && incoming.isNotEmpty() &&
            current.intersect(incoming).isNotEmpty()
    }.getOrDefault(false)

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signingFingerprints(info: android.content.pm.PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            if (signing != null) {
                val certificates = if (signing.hasMultipleSigners()) {
                    signing.apkContentsSigners
                } else {
                    signing.signingCertificateHistory
                }
                if (certificates.isNotEmpty()) {
                    return certificates
                        .map { signatureFingerprint(it.toByteArray()) }
                        .toSet()
                }
            }
        }
        return info.signatures
            ?.map { signatureFingerprint(it.toByteArray()) }
            ?.toSet()
            .orEmpty()
    }

    private fun signatureFingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Open a connection, following redirects manually.
     *
     * `HttpURLConnection` refuses to follow a redirect that changes the
     * protocol (http → https or the other way round), which is exactly
     * what a CDN or a self-hosted manifest does. The old code reported
     * "更新服务器返回 HTTP 301" instead of following the hop.
     */
    private fun openConnection(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            val code = runCatching { connection.responseCode }.getOrDefault(-1)
            val location = connection.getHeaderField("Location")
            if (code in 300..399 && !location.isNullOrBlank() && attempt < MAX_REDIRECTS) {
                connection.disconnect()
                current = URL(URL(current), location).toString()
                return@repeat
            }
            return connection
        }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
    }

    private fun startFirstResolved(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val resolved = if (intent.component == null) {
                context.packageManager.resolveActivity(intent, 0) != null
            } else {
                true
            }
            if (!resolved) continue
            val started = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Log.w(TAG, "Unable to open installer settings", it)
            }.isSuccess
            if (started) return true
        }
        return false
    }

    private fun isAllowedDownloadUrl(url: String): Boolean =
        url.startsWith("https://") || (BuildConfig.DEBUG && url.startsWith("http://"))

    private fun isGitHubAssetApiUrl(url: String): Boolean =
        url.startsWith("https://api.github.com/repos/") &&
            "/releases/assets/" in url

    private fun safeHost(url: String): String =
        runCatching { URL(url).host }.getOrDefault("update server")

    /**
     * Read at most [maxBytes] from a stream. The limit is counted in
     * bytes — the old implementation counted characters, so a Chinese
     * manifest could be three times larger than the announced cap.
     */
    private fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val result = java.io.ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            if (result.size() + read > maxBytes) throw IOException("更新清单过大")
            result.write(buffer, 0, read)
        }
        return result.toByteArray()
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is UnknownHostException -> "无法连接更新服务器，请检查电视网络"
        is SocketTimeoutException -> "更新服务器响应超时，请稍后重试"
        is IOException -> error.message ?: "检查更新失败"
        else -> "检查更新失败：${error.message ?: error.javaClass.simpleName}"
    }
}
