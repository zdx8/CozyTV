package app.cozytv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the version comparison and the SHA-256 handling
 * of the update channel. Both used to break in ways the old three
 * happy-path assertions could not catch:
 *
 *  - "0.4.0-rc.1" parsed as [0,4,0,0,1] and was treated as *newer*
 *    than "0.4.0" (which parses as [0,4,0]) — users were pushed to
 *    "downgrade" to an RC and real releases were never offered.
 *  - "V1.2.3" (capital V) parsed as [0,2,3], so every release looked
 *    older than the installed one and no update was ever shown.
 */
class AppUpdaterVersionTest {

    @Test
    fun `prerelease is older than the matching release`() {
        assertTrue(AppUpdater.compareVersions("0.4.0-rc.1", "0.4.0") < 0)
        assertTrue(AppUpdater.compareVersions("0.4.0", "0.4.0-rc.1") > 0)
        assertTrue(AppUpdater.compareVersions("0.4.0-rc.2", "0.4.0-rc.1") > 0)
        assertEquals(0, AppUpdater.compareVersions("0.4.0-rc.1", "0.4.0-rc.1"))
    }

    @Test
    fun `leading v is accepted in either case`() {
        assertEquals(0, AppUpdater.compareVersions("V1.2.3", "1.2.3"))
        assertEquals(0, AppUpdater.compareVersions("v1.2.3", "V1.2.3"))
        assertTrue(AppUpdater.compareVersions("v1.2.4", "1.2.3") > 0)
    }

    @Test
    fun `build metadata is ignored`() {
        assertEquals(0, AppUpdater.compareVersions("1.2.3+build.7", "1.2.3"))
    }

    @Test
    fun `huge numeric segments do not collapse to zero`() {
        assertTrue(AppUpdater.compareVersions("1.0.99999999999", "1.0.0") > 0)
    }

    @Test
    fun `missing segments count as zero`() {
        assertTrue(AppUpdater.compareVersions("1.1", "1.0.9") > 0)
        assertTrue(AppUpdater.compareVersions("1.0", "1.0.1") < 0)
    }

    @Test
    fun `sha256 accepts the prefix and rejects junk`() {
        val hex = "a".repeat(64)
        assertEquals(hex, AppUpdater.normalizeSha256(hex))
        assertEquals(hex, AppUpdater.normalizeSha256("sha256:$hex"))
        assertEquals(hex, AppUpdater.normalizeSha256("SHA256:${hex.uppercase()}"))
        // The literal string "null" is what JSONObject.optString returns
        // for a JSON null — it must never be treated as a digest.
        assertNull(AppUpdater.normalizeSha256("null"))
        assertNull(AppUpdater.normalizeSha256(null))
        assertNull(AppUpdater.normalizeSha256(""))
        assertNull(AppUpdater.normalizeSha256("deadbeef"))
        assertNull(AppUpdater.normalizeSha256("z".repeat(64)))
    }
}
