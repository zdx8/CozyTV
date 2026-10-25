package app.cozytv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun `compares each numeric version segment`() {
        assertTrue(AppUpdater.compareVersions("v0.10.0", "0.9.9") > 0)
        assertTrue(AppUpdater.compareVersions("0.2", "0.2.1") < 0)
        assertEquals(0, AppUpdater.compareVersions("v0.2.0", "0.2.0"))
    }

    @Test
    fun `selects release apk and ignores debug or source assets`() {
        val selected = AppUpdater.chooseReleaseAsset(
            listOf(
                "CozyTV-v0.3.0-debug.apk",
                "source.zip",
                "CozyTV-v0.3.0-release.apk",
            ),
        )
        assertEquals("CozyTV-v0.3.0-release.apk", selected)
    }

    @Test
    fun `does not install an arbitrary apk asset`() {
        assertNull(
            AppUpdater.chooseReleaseAsset(
                listOf("CozyTV-v0.3.0-debug.apk", "CozyTV-v0.3.0.apk"),
            ),
        )
    }
}
