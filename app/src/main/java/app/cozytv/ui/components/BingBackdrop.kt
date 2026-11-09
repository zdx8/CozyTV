package app.cozytv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.cozytv.data.BingWallpaper
import app.cozytv.data.WallpaperStyle

@Composable
fun BingBackdrop(
    wallpaper: BingWallpaper?,
    fallbackStyle: WallpaperStyle,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (wallpaper == null) {
            AmbientBackground(style = fallbackStyle, dim = dim)
        } else {
            val image = remember(wallpaper.image) { wallpaper.image.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = wallpaper.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
                )
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.46f), Color.Transparent),
                        endX = size.width * 0.58f,
                    ),
                )
                if (dim > 0f) {
                    drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.8f)))
                }
            }
        }
    }
}
