package app.cozytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class LiveChannel(
    val name: String,
    val url: String,
    val referer: String? = null,
)

data class LivePlaylist(
    val channel: LiveChannel,
    val custom: Boolean,
)

object LivePlaylistRepository {
    private const val DEFAULT_STREAM = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    fun demoChannel(): LiveChannel = LiveChannel("CozyTV 演示频道", DEFAULT_STREAM)

    suspend fun load(context: Context, playlistUrl: String): LivePlaylist =
        LivePlaylist(
            channel = loadChannels(context, playlistUrl).first(),
            custom = playlistUrl.isNotBlank(),
        )

    suspend fun loadChannels(context: Context, playlistUrl: String): List<LiveChannel> =
        withContext(Dispatchers.IO) {
            if (playlistUrl.isBlank()) {
                return@withContext listOf(demoChannel())
            }
            val text = fetch(playlistUrl)
            val parsed = parse(text, playlistUrl)
            if (parsed.isNotEmpty()) {
                return@withContext parsed
            }
            if (text.contains("#EXT-X-") || playlistUrl.substringBefore('?').endsWith(".m3u8")) {
                return@withContext listOf(
                    LiveChannel(
                        name = "电视直播",
                        url = playlistUrl,
                        referer = playlistUrl,
                    ),
                )
            }
            error("播放列表中没有可播放频道")
        }

    fun parse(text: String, referer: String? = null): List<LiveChannel> {
        val lines = text.removePrefix("\uFEFF").lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val channels = mutableListOf<LiveChannel>()
        var pendingName: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingName = line.substringAfter(',', "").trim().ifBlank { "电视频道" }
                }
                line.startsWith("#") -> Unit
                line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) -> {
                    channels += LiveChannel(
                        name = pendingName ?: "电视频道",
                        url = line,
                        referer = referer,
                    )
                    pendingName = null
                }
            }
        }
        return channels
    }

    private fun fetch(url: String): String {
        val parsed = URL(url)
        require(parsed.protocol == "http" || parsed.protocol == "https") {
            "直播源必须是 HTTP 或 HTTPS 地址"
        }
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CozyTV/0.2")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream
            else connection.errorStream
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
