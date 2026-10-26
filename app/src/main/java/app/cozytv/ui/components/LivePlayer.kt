package app.cozytv.ui.components

import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cozytv.BuildConfig
import app.cozytv.R
import app.cozytv.data.LiveChannel
import app.cozytv.ui.theme.CozyColors
import app.cozytv.ui.theme.CozyType

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun LivePlayer(
    channel: LiveChannel?,
    modifier: Modifier = Modifier,
    attempt: Int = 0,
    onPlaybackError: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    // The lifecycle observer below is installed once (its keys never
    // change), so it must not capture the `channel` value of the first
    // composition — at that point the channel is still null and the
    // preview would never resume after the app came back from the
    // background. rememberUpdatedState always yields the latest value.
    val currentChannel by rememberUpdatedState(channel)
    val httpFactory = remember(context) {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(PLAYER_USER_AGENT)
    }
    val player = remember(context) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            }
    }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var errorReported by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    Log.i(PLAYER_LOG_TAG, "HOME_PLAYER_READY")
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.errorCodeName
                playbackState = Player.STATE_IDLE
                if (!errorReported) {
                    errorReported = true
                    currentOnPlaybackError()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (currentChannel != null) player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(channel?.url, attempt) {
        errorReported = false
        val target = channel ?: return@LaunchedEffect
        playbackError = null
        playbackState = Player.STATE_BUFFERING
        httpFactory.setDefaultRequestProperties(
            buildMap {
                put("User-Agent", PLAYER_USER_AGENT)
                target.referer?.takeIf(String::isNotBlank)?.let {
                    put("Referer", it)
                }
            },
        )
        val item = MediaItem.Builder()
            .setUri(target.url)
            .apply {
                if (looksLikeHls(target.url)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
    ) {
        AndroidView(
            factory = {
                val view = if (BuildConfig.TEXTURE_VIDEO_FOR_SCREENSHOTS) {
                    LayoutInflater.from(context)
                        .inflate(R.layout.view_live_player_texture, null) as PlayerView
                } else {
                    PlayerView(context)
                }
                view.apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                ),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp)
                .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(Color(0xFF35D07F), RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                "LIVE",
                style = CozyType.Caption.copy(
                    color = Color.White,
                    letterSpacing = 1.2.sp,
                ),
            )
        }
        val status = when {
            playbackError != null -> "播放失败 · ${playbackError.orEmpty()} · 可在设置中更换 M3U 订阅"
            playbackState == Player.STATE_BUFFERING -> "正在缓冲…"
            else -> null
        }
        status?.let {
            BasicText(
                it,
                style = CozyType.Caption.copy(color = CozyColors.Paper.copy(alpha = 0.72f)),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 17.dp, bottom = 54.dp),
            )
        }
    }
}

/**
 * IPTV subscriptions often use extensionless redirects that resolve to HLS.
 * Declare the MIME type explicitly; only known progressive media suffixes
 * are treated as non-HLS.
 */
private fun looksLikeHls(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val progressive = listOf(
        ".mp4", ".mkv", ".flv", ".avi", ".mov", ".webm", ".wmv", ".m4v",
        ".mp3", ".aac", ".m4a", ".flac", ".wav", ".ts",
    )
    return progressive.none { path.endsWith(it) }
}

private const val PLAYER_USER_AGENT = "VLC/3.0.18 LibVLC/3.0.18"
private const val PLAYER_LOG_TAG = "CozyTV.LivePlayer"
