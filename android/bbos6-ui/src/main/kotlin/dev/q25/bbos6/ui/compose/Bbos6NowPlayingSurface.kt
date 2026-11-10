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
import kotlin.math.roundToInt

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
    // Match the original 2513.png renderer: only the 1dp top rule is
    // continuously bright. The seven-dp *bevel* has a centre-weighted
    // horizontal highlight, diminishing nonlinearly on every physical row.
    // A constant-alpha 7dp strip causes the unwanted thick top glow.
    val rule = max(1, 1.dp.toPx().roundToInt()).toFloat()
    val top = rule.toInt()
    val bottom = minOf(
        size.height.roundToInt() - rule.toInt(),
        top + 7.dp.toPx().roundToInt()
    )
    val bevelHeight = bottom - top
    if (bevelHeight > 0) {
        for (offset in 0 until bevelHeight) {
            val alpha = bbos6ModeBevelAlpha(offset, bevelHeight)
            if (alpha == 0) continue
            val white = Color.White.copy(alpha = alpha / 255f)
            val transparentWhite = Color.White.copy(alpha = 0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to transparentWhite,
                    0.43f to white,
                    0.57f to white,
                    1f to transparentWhite
                ),
                topLeft = Offset(0f, (top + offset).toFloat()),
                size = Size(size.width, 1f)
            )
        }
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
    val rule = max(1, 1.dp.toPx().roundToInt()).toFloat()
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color(0xFF393839),
            0.23f to Color(0xFF4A494A),
            0.51f to Color(0xFF5A5D5A),
            1f to Color(0xFF5A6163)
        ),
        size = Size(size.width, rule)
    )
    // 2486.png: a centre-weighted lower reflection grows with the *cube*
    // of row depth. The previous flat 52-alpha 5dp strip made a thick glow.
    // Leave the original one-dp silver separator independently visible.
    val end = size.height.roundToInt() - rule.toInt()
    val start = max(rule.toInt(), end - 5.dp.toPx().roundToInt())
    val bevelHeight = end - start
    if (bevelHeight > 0) {
        for (offset in 0 until bevelHeight) {
            val alpha = bbos6SeekBevelAlpha(offset, bevelHeight)
            if (alpha == 0) continue
            val white = Color.White.copy(alpha = alpha / 255f)
            val transparentWhite = Color.White.copy(alpha = 0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to transparentWhite,
                    0.21f to transparentWhite,
                    0.50f to white,
                    0.79f to transparentWhite,
                    1f to transparentWhite
                ),
                topLeft = Offset(0f, (start + offset).toFloat()),
                size = Size(size.width, 1f)
            )
        }
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
 * Original native upper bevel intensity, converted one physical pixel at a
 * time. Pure functions are unit-testable without Android or Compose views.
 */
internal fun bbos6ModeBevelAlpha(row: Int, height: Int): Int {
    require(height > 0 && row in 0 until height)
    val progress = (row + 0.5f) / height
    val strength = Math.pow((1f - progress).toDouble(), 1.55).toFloat()
    return (42f * strength).roundToInt()
}

/** Original native seek bevel: nearly invisible until the bottom rows. */
internal fun bbos6SeekBevelAlpha(row: Int, height: Int): Int {
    require(height > 0 && row in 0 until height)
    val rise = (row + 1).toFloat() / height
    val strength = Math.pow(rise.toDouble(), 3.2).toFloat()
    return (52f * strength).roundToInt()
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
