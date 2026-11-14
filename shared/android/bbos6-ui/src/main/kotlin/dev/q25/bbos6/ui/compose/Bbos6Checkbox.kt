package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Matches the geometry of Vector's existing BB6NativeQsPanel.StatusCheckbox. */
internal object Bbos6CheckboxMetrics {
    const val CELL_WIDTH_DP = 24
    const val CELL_HEIGHT_DP = 26
    const val BOX_EDGE_DP = 19f
    const val RADIUS_DP = 2f
    const val TICK_STROKE_DP = 2.3f

    data class Bounds(val left: Float, val top: Float, val edge: Float)

    fun bounds(widthPx: Float, heightPx: Float, density: Float): Bounds {
        require(widthPx >= 0f && heightPx >= 0f) { "Canvas dimensions must be nonnegative" }
        require(density > 0f) { "Density must be positive" }
        val edge = minOf(widthPx, heightPx, BOX_EDGE_DP * density)
        return Bounds(
            left = (widthPx - edge) * 0.5f,
            top = (heightPx - edge) * 0.5f,
            edge = edge
        )
    }
}

/**
 * Canonical BBOS6 checkbox face, ported from Vector's native QS status indicator.
 *
 * With no [onCheckedChange], the checkbox is a non-focusable, non-clickable
 * status indicator. A parent that owns selection passes [highlighted] only
 * while it actually owns focus.
 *
 * With [onCheckedChange], this becomes an independently focusable checkbox
 * using Compose's native toggleable semantics, keyboard/DPAD activation,
 * checked state and disabled state. The border follows the checkbox's own
 * focus; the caller still owns and updates [checked]. No application-specific
 * state or business logic lives here.
 *
 * The original QS code defines no disabled colour treatment: [enabled] only
 * controls interaction/semantics, not the face. Default geometry is a 19dp
 * rounded square centered in a 24x26dp slot.
 */
@Composable
fun Bbos6Checkbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    var hasFocus by remember { mutableStateOf(false) }
    val interactive = onCheckedChange != null
    val focusBorder = if (interactive) hasFocus else highlighted

    val inputModifier = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
    }

    Canvas(
        modifier = modifier
            .size(Bbos6CheckboxMetrics.CELL_WIDTH_DP.dp, Bbos6CheckboxMetrics.CELL_HEIGHT_DP.dp)
            .then(inputModifier)
    ) {
        val bounds = Bbos6CheckboxMetrics.bounds(size.width, size.height, density)
        val topLeft = Offset(bounds.left, bounds.top)
        val boxSize = Size(bounds.edge, bounds.edge)
        val cornerRadius = CornerRadius(Bbos6CheckboxMetrics.RADIUS_DP * density)

        // Preserve the original native QS fill, border and stroke colours.
        drawRoundRect(
            color = Color(0xFFF3F5F7),
            topLeft = topLeft,
            size = boxSize,
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = if (focusBorder) Color(0xFFE1EFFF) else Color(0xFF84909E),
            topLeft = topLeft,
            size = boxSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = maxOf(1f, density))
        )

        if (checked) {
            val tick = Path().apply {
                moveTo(bounds.left + bounds.edge * 0.19f, bounds.top + bounds.edge * 0.52f)
                lineTo(bounds.left + bounds.edge * 0.41f, bounds.top + bounds.edge * 0.74f)
                lineTo(bounds.left + bounds.edge * 0.83f, bounds.top + bounds.edge * 0.26f)
            }
            drawPath(
                path = tick,
                color = Color(0xFF176BD1),
                style = Stroke(
                    width = Bbos6CheckboxMetrics.TICK_STROKE_DP * density,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
