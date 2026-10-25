package app.cozytv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaylistTest {
    @Test
    fun `parses channel names and stream urls`() {
        val channels = LivePlaylistRepository.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" group-title="News",CCTV 1
            https://example.com/one.m3u8
            #EXTINF:-1,财经频道
            http://example.com/two.m3u8
            """.trimIndent(),
        )
        assertEquals(listOf("CCTV 1", "财经频道"), channels.map(LiveChannel::name))
        assertEquals("https://example.com/one.m3u8", channels.first().url)
    }

    @Test
    fun `ignores comments and non stream lines`() {
        val channels = LivePlaylistRepository.parse(
            """
            #EXTM3U
            # a comment
            not a url
            """.trimIndent(),
        )
        assertTrue(channels.isEmpty())
    }

    @Test
    fun `uses fallback name when extinf has no title`() {
        val channel = LivePlaylistRepository.parse(
            """
            #EXTINF:-1 tvg-id="news"
            https://example.com/news.m3u8
            """.trimIndent(),
        ).single()
        assertEquals("电视频道", channel.name)
    }
}
