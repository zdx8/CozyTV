package app.cozytv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private const val GALLERY_META_FILE = "bing-gallery-meta.json"
    private const val IMAGE_PREFIX = "bing-"

    suspend fun load(
        context: Context,
        force: Boolean = false,
        archiveIndex: Int = 0,
    ): BingWallpaper? = loadGallery(
        context = context,
        count = 1,
        force = force,
        startIndex = archiveIndex,
    ).firstOrNull()

    suspend fun loadGallery(
        context: Context,
        count: Int = 3,
        force: Boolean = false,
        startIndex: Int = 0,
    ): List<BingWallpaper> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "bing").apply { mkdirs() }
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val safeCount = count.coerceIn(1, 3)
        val stored = readStoredGallery(directory)
        val cached = decodeStoredGallery(directory, stored)
        if (!force &&
            cached.size >= safeCount &&
            cached.any { it.date == today }
        ) {
            return@withContext cached.take(safeCount)
        }

        val freshEntries = mutableListOf<StoredWallpaper>()
        val freshWallpapers = mutableListOf<BingWallpaper>()
        var offset = 0
        while (freshWallpapers.size < safeCount && offset < 8) {
            val index = Math.floorMod(startIndex + offset, 8)
            runCatching {
                val metadata = fetchMetadata(index)
                val identity = Integer.toHexString(metadata.imageUrl.hashCode())
                val imageFile = File(directory, "$IMAGE_PREFIX${metadata.date}-$identity.jpg")
                if (!imageFile.exists()) download(metadata.imageUrl, imageFile)
                decodeWallpaper(
                    file = imageFile,
                    metadata = metadata,
                    stale = metadata.date != today,
                )?.let { wallpaper ->
                    if (freshWallpapers.none { it.date == wallpaper.date }) {
                        freshEntries += StoredWallpaper(metadata, imageFile.name)
                        freshWallpapers += wallpaper
                    }
                }
            }
            offset += 1
        }

        if (freshEntries.isNotEmpty()) {
            writeStoredGallery(
                directory,
                (freshEntries + stored).distinctBy { it.metadata.date }.take(16),
            )
            directory.listFiles { entry ->
                entry.isFile && entry.name.startsWith(IMAGE_PREFIX) && entry.name.endsWith(".jpg")
            }?.sortedByDescending(File::lastModified)
                ?.drop(16)
                ?.forEach(File::delete)
        }

        (freshWallpapers + cached)
            .distinctBy(BingWallpaper::date)
            .take(safeCount)
            .ifEmpty { listOfNotNull(readLatestCached(directory)) }
    }

    private data class Metadata(
        val date: String,
        val title: String,
        val copyright: String,
        val imageUrl: String,
    )

    private data class StoredWallpaper(
        val metadata: Metadata,
        val fileName: String,
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
                    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
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

    private fun readStoredGallery(directory: File): List<StoredWallpaper> = runCatching {
        val json = JSONArray(File(directory, GALLERY_META_FILE).readText(Charsets.UTF_8))
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(
                    StoredWallpaper(
                        metadata = Metadata(
                            date = item.getString("date"),
                            title = item.getString("title"),
                            copyright = item.getString("copyright"),
                            imageUrl = item.optString("imageUrl"),
                        ),
                        fileName = item.getString("fileName"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeStoredGallery(directory: File, entries: List<StoredWallpaper>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject()
                    .put("date", entry.metadata.date)
                    .put("title", entry.metadata.title)
                    .put("copyright", entry.metadata.copyright)
                    .put("imageUrl", entry.metadata.imageUrl)
                    .put("fileName", entry.fileName),
            )
        }
        File(directory, GALLERY_META_FILE).writeText(json.toString(), Charsets.UTF_8)
    }

    private fun decodeStoredGallery(
        directory: File,
        entries: List<StoredWallpaper>,
    ): List<BingWallpaper> {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return entries.mapNotNull { entry ->
            val file = File(directory, entry.fileName)
            if (!file.exists()) return@mapNotNull null
            decodeWallpaper(
                file = file,
                metadata = entry.metadata,
                stale = entry.metadata.date != today,
            )
        }
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
