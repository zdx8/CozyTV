package app.cozytv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.cozytv.data.WallpaperStyle

private data class Scene(
    val skyTop: Color,
    val skyBottom: Color,
    val glow: Color,
    val glowAt: Offset,
    val ridgeFar: Color,
    val ridgeMid: Color,
    val ridgeNear: Color,
)

@Composable
fun AmbientBackground(
    style: WallpaperStyle,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
) {
    Canvas(modifier.fillMaxSize()) {
        val scene = when (style) {
            WallpaperStyle.Dawn -> Scene(
                skyTop = Color(0xFF657EB9),
                skyBottom = Color(0xFFF2B39F),
                glow = Color(0xFFFFE0BD),
                glowAt = Offset(size.width * 0.68f, size.height * 0.34f),
                ridgeFar = Color(0xFF576B91),
                ridgeMid = Color(0xFF2D3C60),
                ridgeNear = Color(0xFF151C36),
            )
            WallpaperStyle.Aurora -> Scene(
                skyTop = Color(0xFF10152E),
                skyBottom = Color(0xFF26375B),
                glow = Color(0xFF75E3CE),
                glowAt = Offset(size.width * 0.58f, size.height * 0.25f),
                ridgeFar = Color(0xFF243B55),
                ridgeMid = Color(0xFF172943),
                ridgeNear = Color(0xFF0B1228),
            )
            WallpaperStyle.Ember -> Scene(
                skyTop = Color(0xFF3A2737),
                skyBottom = Color(0xFFD27460),
                glow = Color(0xFFFFC184),
                glowAt = Offset(size.width * 0.35f, size.height * 0.38f),
                ridgeFar = Color(0xFF6D4451),
                ridgeMid = Color(0xFF402B3C),
                ridgeNear = Color(0xFF1C1727),
            )
        }

        drawRect(
            Brush.verticalGradient(
                listOf(scene.skyTop, scene.skyBottom),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scene.glow.copy(alpha = 0.58f), Color.Transparent),
                center = scene.glowAt,
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
            center = scene.glowAt,
        )

        when (style) {
            WallpaperStyle.Aurora -> {
                drawAurora(
                    Color(0xFF67E0C8).copy(alpha = 0.22f),
                    size.width * 0.2f,
                    size.height * 0.08f,
                )
                drawAurora(
                    Color(0xFF879BFF).copy(alpha = 0.18f),
                    size.width * 0.55f,
                    size.height * 0.18f,
                )
            }
            WallpaperStyle.Dawn -> {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFFF9DCCD).copy(alpha = 0.24f),
                            Color.Transparent,
                        ),
                        startY = size.height * 0.38f,
                        endY = size.height * 0.7f,
                    ),
                )
            }
            WallpaperStyle.Ember -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFDAA6).copy(alpha = 0.35f), Color.Transparent),
                        center = scene.glowAt,
                        radius = size.minDimension * 0.24f,
                    ),
                    center = scene.glowAt,
                    radius = size.minDimension * 0.24f,
                )
            }
        }

        drawRidge(
            color = scene.ridgeFar,
            baseline = size.height * 0.58f,
            amplitude = size.height * 0.12f,
            phase = 0.02f,
        )
        drawRidge(
            color = scene.ridgeMid,
            baseline = size.height * 0.72f,
            amplitude = size.height * 0.16f,
            phase = 0.31f,
        )
        drawRidge(
            color = scene.ridgeNear,
            baseline = size.height * 0.9f,
            amplitude = size.height * 0.2f,
            phase = 0.64f,
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF070A16).copy(alpha = 0.76f)),
                startY = size.height * 0.42f,
            ),
        )
        drawRect(
            Brush.horizontalGradient(
                listOf(Color(0xFF060914).copy(alpha = 0.32f), Color.Transparent),
                endX = size.width * 0.55f,
            ),
        )
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.8f)))
    }
}

private fun DrawScope.drawRidge(
    color: Color,
    baseline: Float,
    amplitude: Float,
    phase: Float,
) {
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, baseline + amplitude * (0.2f - phase))
        cubicTo(
            size.width * 0.12f,
            baseline - amplitude * (0.72f + phase),
            size.width * 0.24f,
            baseline + amplitude * (0.22f - phase),
            size.width * 0.38f,
            baseline - amplitude * 0.2f,
        )
        cubicTo(
            size.width * 0.52f,
            baseline - amplitude * (0.7f + phase),
            size.width * 0.64f,
            baseline + amplitude * 0.05f,
            size.width * 0.76f,
            baseline - amplitude * (0.45f - phase),
        )
        cubicTo(
            size.width * 0.87f,
            baseline - amplitude * 0.9f,
            size.width * 0.95f,
            baseline + amplitude * 0.1f,
            size.width,
            baseline - amplitude * 0.32f,
        )
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawAurora(color: Color, x: Float, y: Float) {
    val width = size.width * 0.82f
    val path = Path().apply {
        moveTo(x, y)
        cubicTo(
            x + width * 0.2f,
            y - size.height * 0.1f,
            x + width * 0.45f,
            y + size.height * 0.15f,
            x + width * 0.68f,
            y + size.height * 0.02f,
        )
        cubicTo(
            x + width * 0.76f,
            y - size.height * 0.03f,
            x + width * 0.91f,
            y + size.height * 0.01f,
            x + width,
            y - size.height * 0.04f,
        )
        cubicTo(
            x + width * 0.72f,
            y + size.height * 0.18f,
            x + width * 0.4f,
            y + size.height * 0.2f,
            x,
            y + size.height * 0.03f,
        )
        close()
    }
    drawPath(
        path,
        Brush.linearGradient(
            listOf(Color.Transparent, color, Color.Transparent),
            start = Offset(x, y),
            end = Offset(x + width, y + size.height * 0.1f),
        ),
    )
}
