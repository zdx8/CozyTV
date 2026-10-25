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

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
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
        if (target.exists() && !target.delete()) {
            throw IOException("无法清理旧的更新包")
        }
        try {
            val connection = openConnection(info.downloadUrl).apply {
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

                val expected = info.sha256?.lowercase()
                if (!expected.isNullOrBlank()) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actual != expected) {
                        throw IOException("更新包校验失败")
                    }
                }
                validateApk(context, target, info)
                target
            } finally {
                connection.disconnect()
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
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
        val remoteParts = versionParts(remote)
        val localParts = versionParts(local)
        repeat(maxOf(remoteParts.size, localParts.size)) { index ->
            val difference = remoteParts.getOrElse(index) { 0 } -
                localParts.getOrElse(index) { 0 }
            if (difference != 0) return difference.sign()
        }
        return 0
    }

    internal fun chooseReleaseAsset(names: List<String>): String? =
        names.firstOrNull { it.endsWith(RELEASE_ASSET_SUFFIX, ignoreCase = true) }

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
            val payload = connection.inputStream.bufferedReader().use {
                readLimited(it, MAX_MANIFEST_BYTES)
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
                    assets.optJSONObject(index)?.optString("name")?.let(::add)
                }
            }
            val selectedName = chooseReleaseAsset(assetNames) ?: return null
            val asset = (0 until assets.length())
                .mapNotNull(assets::optJSONObject)
                .firstOrNull { it.optString("name") == selectedName }
                ?: return null
            val version = json.optString("tag_name").removePrefix("v").trim()
            if (version.isEmpty()) return null
            val digest = asset.optString("digest")
                .removePrefix("sha256:")
                .takeIf(String::isNotBlank)
            return UpdateInfo(
                versionName = version,
                downloadUrl = asset.optString("browser_download_url"),
                sizeBytes = asset.optLong("size", 0L),
                notes = json.optString("body").trim(),
                sha256 = digest,
            ).takeIf { isAllowedDownloadUrl(it.downloadUrl) }
        }

        val version = json.optString("versionName").removePrefix("v").trim()
        val url = json.optString("url").trim()
        if (version.isEmpty() || !isAllowedDownloadUrl(url)) return null
        return UpdateInfo(
            versionName = version,
            downloadUrl = url,
            sizeBytes = json.optLong("size", 0L),
            notes = json.optString("notes").trim(),
            sha256 = json.optString("sha256").takeIf(String::isNotBlank),
        )
    }

    private fun validateApk(context: Context, apk: File, info: UpdateInfo) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: throw IOException("下载文件不是有效 APK")
        if (archive.packageName != context.packageName) {
            throw IOException("更新包属于其他应用")
        }
        val archiveVersion = archive.versionName.orEmpty()
        if (archiveVersion.isNotBlank() &&
            compareVersions(archiveVersion, info.versionName) != 0
        ) {
            throw IOException("更新包版本与发布信息不一致")
        }
        if (!hasSameSigner(context, manager, apk)) {
            throw IOException("更新包签名与当前安装版本不一致")
        }
    }

    @Suppress("DEPRECATION")
    private fun hasSameSigner(
        context: Context,
        manager: PackageManager,
        apk: File,
    ): Boolean = runCatching {
        val current = manager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES,
        ).signatures.orEmpty().map { signatureFingerprint(it.toByteArray()) }.toSet()
        val incoming = manager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNATURES,
        )?.signatures.orEmpty().map { signatureFingerprint(it.toByteArray()) }.toSet()
        current.isNotEmpty() && incoming.isNotEmpty() && current == incoming
    }.getOrDefault(false)

    private fun signatureFingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
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

    private fun readLimited(reader: java.io.Reader, maxBytes: Int): String {
        val result = StringBuilder()
        val buffer = CharArray(8 * 1024)
        while (true) {
            val read = reader.read(buffer)
            if (read <= 0) break
            result.append(buffer, 0, read)
            if (result.length > maxBytes) throw IOException("更新清单过大")
        }
        return result.toString()
    }

    private fun versionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .split(Regex("[^0-9]+"))
            .filter(String::isNotBlank)
            .map { it.toIntOrNull() ?: 0 }

    private fun Int.sign(): Int = when {
        this < 0 -> -1
        this > 0 -> 1
        else -> 0
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is UnknownHostException -> "无法连接更新服务器，请检查电视网络"
        is SocketTimeoutException -> "更新服务器响应超时，请稍后重试"
        is IOException -> error.message ?: "检查更新失败"
        else -> "检查更新失败：${error.message ?: error.javaClass.simpleName}"
    }
}
