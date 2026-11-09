package app.cozytv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

enum class SourceKind {
    TvLive,
    Hdmi,
    DisplayPort,
    Tuner,
    Composite,
    Other,
}

data class TvSource(
    val id: String,
    val packageName: String,
    val label: String,
    val kind: SourceKind,
    val connected: Boolean,
) {
    fun launch(context: Context) {
        val uri = if (kind == SourceKind.Tuner) {
            TvContract.buildChannelsUriForInput(id)
        } else {
            TvContract.buildChannelUriForPassthroughInput(id)
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
    }
}

object SourceRepository {
    const val TV_LIVE_ID = "cozytv://tvlive"

    fun tvLive(): TvSource = TvSource(
        id = TV_LIVE_ID,
        packageName = "app.cozytv",
        label = "TVLive",
        kind = SourceKind.TvLive,
        connected = true,
    )

    fun load(context: Context): List<TvSource> {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val inputs = manager.tvInputList.mapNotNull { info ->
            runCatching {
                val kind = when (info.type) {
                    TvInputInfo.TYPE_HDMI -> SourceKind.Hdmi
                    TvInputInfo.TYPE_DISPLAY_PORT -> SourceKind.DisplayPort
                    TvInputInfo.TYPE_TUNER -> SourceKind.Tuner
                    TvInputInfo.TYPE_COMPOSITE -> SourceKind.Composite
                    else -> SourceKind.Other
                }
                val state = runCatching { manager.getInputState(info.id) }.getOrDefault(
                    TvInputManager.INPUT_STATE_DISCONNECTED,
                )
                TvSource(
                    id = info.id,
                    packageName = info.serviceInfo.packageName,
                    label = info.loadLabel(context).toString().ifBlank {
                        info.serviceInfo.loadLabel(context.packageManager).toString()
                    }.ifBlank {
                        kind.name
                    },
                    kind = kind,
                    connected = state == TvInputManager.INPUT_STATE_CONNECTED ||
                        state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY,
                )
            }.getOrNull()
        }.sortedWith(compareBy<TvSource> { it.kind.ordinal }.thenBy { it.label })
        return listOf(tvLive()) + inputs
    }
}
