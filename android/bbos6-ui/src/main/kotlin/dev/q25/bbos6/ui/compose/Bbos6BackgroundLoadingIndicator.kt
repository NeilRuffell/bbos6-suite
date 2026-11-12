package dev.q25.bbos6.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Non-blocking BBOS6 background-work indicator, based on a 643x481 screenshot
 * whose green, beveled bottom-edge strip is approximately 14 pixels high.
 *
 * The caller owns visibility and the lifecycle of its background operation.
 * The indicator is not an interactive control, focus owner, progress estimate
 * or navigation target. Composition disposal stops its animation.
 */
@Composable
fun Bbos6BackgroundLoadingIndicator(
    modifier: Modifier = Modifier,
    accessibilityLabel: String = "Background work in progress"
) {
    val transition = rememberInfiniteTransition(label = "bbos6-background-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = Bbos6BackgroundLoadingMetrics.PERIOD_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rightward-stripes"
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(Bbos6BackgroundLoadingMetrics.HEIGHT_DP.dp)
            .clip(RoundedCornerShape(3.dp))
            .semantics {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF0C9100), // green upper edge
                    Color(0xFF0A7900), // middle face
                    Color(0xFF075700)  // darker lower edge
                ),
                startY = 0f,
                endY = size.height
            )
        )

        val spacing = Bbos6BackgroundLoadingMetrics.STRIPE_SPACING_DP.dp.toPx()
        val skew = Bbos6BackgroundLoadingMetrics.STRIPE_SKEW_DP.dp.toPx()
        val stripeBrush = Brush.verticalGradient(
            listOf(Color(0x99D3FFD0), Color(0x4DD3FFD0)),
            startY = 0f,
            endY = size.height
        )
        var x = -spacing - skew + phase * spacing
        while (x < size.width + spacing) {
            // Rising diagonals: bottom-left to top-right. Increasing phase
            // translates the entire repeating stripe pattern to the right.
            drawLine(
                brush = stripeBrush,
                start = Offset(x, size.height),
                end = Offset(x + skew, 0f),
                strokeWidth = 1.dp.toPx()
            )
            x += spacing
        }

        // Fine bevel edges are part of this shared renderer, not a host overlay.
        drawLine(
            color = Color(0x8858DB3B),
            start = Offset(0f, 0.5.dp.toPx()),
            end = Offset(size.width, 0.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xBB052900),
            start = Offset(0f, size.height - 0.5.dp.toPx()),
            end = Offset(size.width, size.height - 0.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** Shared dimensions and timing; not a Music-specific progress contract. */
internal object Bbos6BackgroundLoadingMetrics {
    const val HEIGHT_DP = 14
    const val STRIPE_SPACING_DP = 10
    const val STRIPE_SKEW_DP = 8
    const val PERIOD_MS = 650
}
