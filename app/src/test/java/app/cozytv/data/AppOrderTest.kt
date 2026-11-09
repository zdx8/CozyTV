package app.cozytv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    private fun app(name: String) = TvApp(
        packageName = name,
        activityName = "$name.Main",
        label = name,
        isLeanback = true,
        icon = null,
        banner = null,
        accentColor = 0,
    )

    @Test
    fun favoritesComeFirstFollowedByRecentsWithoutUnrelatedApps() {
        val apps = listOf(app("A"), app("B"), app("C"), app("D"))
        val result = AppOrder.home(
            apps,
            setOf(apps[2].key),
            listOf(apps[1].key, apps[2].key, apps[0].key),
            limit = 4,
        )
        assertEquals(listOf("C", "B", "A"), result.map(TvApp::label))
    }

    @Test
    fun remainingAppsAreNotAutoAddedToFavorites() {
        val apps = listOf(app("YouTube"), app("Settings"), app("Remote"))
        val result = AppOrder.home(apps, setOf(apps[0].key), emptyList(), limit = 3)
        assertEquals(listOf("YouTube"), result.map(TvApp::label))
    }

    @Test
    fun limitIsAppliedAfterDeduplication() {
        val apps = listOf(app("A"), app("B"), app("C"))
        val result = AppOrder.home(apps, setOf(apps[1].key), listOf(apps[1].key, apps[0].key), limit = 2)
        assertEquals(listOf("B", "A"), result.map(TvApp::label))
    }
}
