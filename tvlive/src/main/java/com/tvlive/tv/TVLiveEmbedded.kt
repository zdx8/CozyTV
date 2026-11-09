package com.tvlive.tv

/**
 * Runtime switch used when TVLive is launched from CozyTV's launcher.
 * It hides the standalone self-update entry and keeps the embedded app
 * focused on playback and subscription management.
 */
object TVLiveEmbedded {
    const val EXTRA_EMBEDDED = "com.tvlive.tv.extra.EMBEDDED"

    @Volatile
    var active: Boolean = false
}
