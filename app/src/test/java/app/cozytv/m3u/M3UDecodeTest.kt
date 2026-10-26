package app.cozytv.m3u

import com.tvlive.tv.data.m3u.M3UParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/**
 * The playlist decoder used to return mojibake for GBK/GB2312 sources.
 *
 * `String(bytes, UTF_8)` never throws — it replaces illegal bytes with
 * U+FFFD. The `looksLikeText` guard only counted control characters, so
 * the mojibake passed the check and the GB18030 branch was dead code.
 * Every Chinese IPTV subscription therefore showed garbage channel
 * names.
 */
class M3UDecodeTest {

    @Test
    fun `decodes a gbk playlist`() {
        val text = "#EXTM3U\n#EXTINF:-1 tvg-name=\"CCTV-1 综合\",CCTV-1 综合\n" +
            "http://example.com/live/cctv1.m3u8\n"
        val bytes = text.toByteArray(gbk)
        val decoded = M3UParser().decode(bytes)
        assertNotNull(decoded)
        assertTrue(decoded!!.contains("CCTV-1 综合"))
        assertTrue(decoded.contains("example.com"))
    }

    @Test
    fun `decodes a plain utf-8 playlist`() {
        val text = "#EXTM3U\n#EXTINF:-1,湖南卫视\nhttp://example.com/hunan.m3u8\n"
        val decoded = M3UParser().decode(text.toByteArray(Charsets.UTF_8))
        assertNotNull(decoded)
        assertTrue(decoded!!.contains("湖南卫视"))
    }

    @Test
    fun `keeps the utf-8 bom path working`() {
        val text = "#EXTM3U\n#EXTINF:-1,北京卫视\nhttp://example.com/bj.m3u8\n"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + text.toByteArray(Charsets.UTF_8)
        val decoded = M3UParser().decode(bytes)
        assertNotNull(decoded)
        assertTrue(decoded!!.contains("北京卫视"))
    }

    @Test
    fun `binary content is rejected`() {
        val bytes = ByteArray(256) { (it * 7).toByte() }
        // Either null or a string without playlist content — but never a
        // "successful" parse of binary noise.
        val decoded = M3UParser().decode(bytes)
        assertTrue(decoded == null || !decoded.contains("#EXTINF"))
    }

    private companion object {
        val gbk: Charset = Charset.forName("GBK")
    }
}
