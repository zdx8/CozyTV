package app.cozytv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BingWallpaper(
    val image: Bitmap,
    val title: String,
    val copyright: String,
    val date: String,
    val stale: Boolean,
)

object BingWallpaperRepository {
    private const val ARCHIVE_URL = "https://www.bing.com/HPImageArchive.aspx"
    private const val META_FILE = "bing-meta.txt"
    private const val IMAGE_PREFIX = "bing-"

    suspend fun load(
        context: Context,
        force: Boolean = false,
        archiveIndex: Int = 0,
    ): BingWallpaper? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "bing").apply { mkdirs() }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val metaFile = File(directory, META_FILE)
        val cached = readCached(directory, metaFile)
        if (!force && cached != null && !cached.stale) return@withContext cached

        runCatching {
            val metadata = fetchMetadata(archiveIndex.coerceIn(0, 7))
            val identity = Integer.toHexString(metadata.imageUrl.hashCode())
            val imageFile = File(directory, "$IMAGE_PREFIX${metadata.date}-$identity.jpg")
            download(metadata.imageUrl, imageFile)
            metaFile.writeText(
                listOf(metadata.date, metadata.title, metadata.copyright, imageFile.name)
                    .joinToString("\n"),
                Charsets.UTF_8,
            )
            directory.listFiles { entry ->
                entry.isFile && entry.name.startsWith(IMAGE_PREFIX) && entry.name.endsWith(".jpg")
            }?.sortedByDescending(File::lastModified)
                ?.drop(16)
                ?.forEach(File::delete)
            decodeWallpaper(imageFile, metadata, stale = false)
        }.getOrElse {
            cached ?: readLatestCached(directory)
        }
    }

    private data class Metadata(
        val date: String,
        val title: String,
        val copyright: String,
        val imageUrl: String,
    )

    private fun fetchMetadata(index: Int): Metadata {
        val url = "$ARCHIVE_URL?format=js&idx=$index&n=1&mkt=zh-CN"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "CozyTV/0.2")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val image = JSONObject(body).getJSONArray("images").getJSONObject(0)
            val base = image.getString("urlbase")
            return Metadata(
                date = image.optString("startdate").ifBlank {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                },
                title = image.optString("title").ifBlank { "Bing 每日精选" },
                copyright = image.optString("copyright").ifBlank { "Bing Wallpaper" },
                imageUrl = "https://www.bing.com${base}_UHD.jpg",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CozyTV/0.2")
        }
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            check(connection.responseCode in 200..299) { "Bing image HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Bing image is not a bitmap" }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun readCached(directory: File, metaFile: File): BingWallpaper? {
        if (!metaFile.exists()) return null
        val parts = metaFile.readLines(Charsets.UTF_8)
        if (parts.size < 4) return null
        val file = File(directory, parts[3])
        if (!file.exists()) return null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val metadata = Metadata(parts[0], parts[1], parts[2], "")
        return decodeWallpaper(
            file = file,
            metadata = metadata,
            stale = parts[0] != today,
        )
    }

    private fun readLatestCached(directory: File): BingWallpaper? {
        val file = directory.listFiles { entry ->
            entry.isFile && entry.name.startsWith(IMAGE_PREFIX) && entry.name.endsWith(".jpg")
        }?.maxByOrNull(File::lastModified) ?: return null
        return decodeWallpaper(
            file,
            Metadata("", "Bing 每日精选", "Bing Wallpaper", ""),
            stale = true,
        )
    }

    private fun decodeWallpaper(
        file: File,
        metadata: Metadata,
        stale: Boolean,
    ): BingWallpaper? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 1440) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return BingWallpaper(
            image = bitmap,
            title = metadata.title,
            copyright = metadata.copyright,
            date = metadata.date,
            stale = stale,
        )
    }
}
