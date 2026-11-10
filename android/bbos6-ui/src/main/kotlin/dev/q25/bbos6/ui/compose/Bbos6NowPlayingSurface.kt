package dev.q25.bbos6.ui.compose

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** The four native BBOS6 Now Playing background regions. */
enum class Bbos6NowPlayingPanel {
    MODE, ARTWORK, SEEK, TRANSPORT
}

/**
 * App-neutral Compose chrome. No media state, touch or focus handling.
 *
 * Keep original source color stops, relative heights, the right-anchored
 * half-radial artwork lighting and the seek panel's top/bottom accent rules.
 * Native button highlights, SeekBar rail/thumb, foreground separators and
 * icons remain caller-owned until each is separately parity-tested.
 */
@Composable
fun Bbos6NowPlayingSurface(
    panel: Bbos6NowPlayingPanel,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        when (panel) {
            Bbos6NowPlayingPanel.MODE -> drawModeChrome()
            Bbos6NowPlayingPanel.ARTWORK -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF393C4A), Color.Black),
                        center = Offset(size.width, size.height / 2f),
                        radius = max(1f, size.width * 0.56f)
                    )
                )
            }
            Bbos6NowPlayingPanel.SEEK -> drawSeekChrome()
            Bbos6NowPlayingPanel.TRANSPORT -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF6B6D73),
                        0.10f to Color(0xFF63656B),
                        0.17f to Color(0xFF5A5D63),
                        0.25f to Color(0xFF4A515A),
                        0.34f to Color(0xFF42454A),
                        0.42f to Color(0xFF393C42),
                        0.51f to Color(0xFF313839),
                        0.67f to Color(0xFF293031),
                        0.82f to Color(0xFF212829),
                        1f to Color(0xFF212421)
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawModeChrome() {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF212021),
            0.20f to Color(0xFF212021),
            0.48f to Color(0xFF181818),
            0.67f to Color(0xFF181818),
            0.90f to Color(0xFF101010),
            1f to Color(0xFF101010)
        )
    )
    val rule = 1.dp.toPx()
    val sheenHeight = minOf(7.dp.toPx(), max(0f, size.height - 2f * rule))
    if (sheenHeight > 0f) {
        // Shallow, subdued highlight beneath the stronger silver top rule.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0x2AFFFFFF),
                1f to Color(0x00FFFFFF)
            ),
            topLeft = Offset(0f, rule),
            size = Size(size.width, sheenHeight)
        )
    }
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color(0xFF9C9E9C),
            0.45f to Color.White,
            0.55f to Color.White,
            1f to Color(0xFFA5A2A5)
        ),
        size = Size(size.width, rule)
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color(0xFF212021),
            0.50f to Color(0xFF212421),
            1f to Color(0xFF212021)
        ),
        topLeft = Offset(0f, max(0f, size.height - rule)),
        size = Size(size.width, rule)
    )
}

private fun DrawScope.drawSeekChrome() {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF212421),
            0.28f to Color(0xFF212021),
            0.53f to Color(0xFF181C18),
            0.68f to Color(0xFF181818),
            1f to Color(0xFF181818)
        )
    )
    val rule = 1.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color(0xFF393839),
            0.23f to Color(0xFF4A494A),
            0.51f to Color(0xFF5A5D5A),
            1f to Color(0xFF5A6163)
        ),
        size = Size(size.width, rule)
    )
    val bevelHeight = minOf(5.dp.toPx(), max(0f, size.height - 2f * rule))
    if (bevelHeight > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.21f to Color.Transparent,
                0.50f to Color(0x34FFFFFF),
                0.79f to Color.Transparent,
                1f to Color.Transparent
            ),
            topLeft = Offset(0f, max(0f, size.height - rule - bevelHeight)),
            size = Size(size.width, bevelHeight)
        )
    }
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color(0xFF949294),
            0.36f to Color(0xFFF7F7F7),
            0.45f to Color.White,
            0.64f to Color(0xFFF7F7F7),
            1f to Color(0xFF9C9A9C)
        ),
        topLeft = Offset(0f, max(0f, size.height - rule)),
        size = Size(size.width, rule)
    )
}

/**
 * A non-interactive Compose surface, for layouts keeping native button
 * and SeekBar controls during staged migration.
 */
class Bbos6NowPlayingSurfaceView(
    context: Context,
    private val panel: Bbos6NowPlayingPanel
) : AbstractComposeView(context) {
    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Composable
    override fun Content() {
        Bbos6NowPlayingSurface(panel)
    }
}
