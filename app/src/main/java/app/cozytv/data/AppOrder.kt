package app.cozytv.data

object AppOrder {
    fun home(
        apps: List<TvApp>,
        favorites: Set<String>,
        recents: List<String>,
        limit: Int = 5,
    ): List<TvApp> {
        val byKey = apps.associateBy(TvApp::key)
        val seen = HashSet<String>()
        return buildList {
            favorites.forEach { key ->
                byKey[key]?.let {
                    add(it)
                    seen += key
                }
            }
            recents.forEach { key ->
                byKey[key]?.let {
                    if (seen.add(key)) add(it)
                }
            }
        }.take(limit)
    }
}

