package app.cozytv.update

import androidx.core.content.FileProvider

/**
 * Dedicated provider for downloaded update APKs. TVLive also declares a
 * FileProvider in the merged manifest, so CozyTV uses its own class and
 * authority to keep both update paths independent.
 */
class CozyUpdateFileProvider : FileProvider()
