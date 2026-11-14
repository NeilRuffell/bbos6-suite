package dev.q25.bbfiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Passive Files-specific badge; the shared section header owns its placement. */
@Composable
internal fun ClipboardStatusIndicator(cut: Boolean, count: Int) {
    val action = if (cut) "Cut" else "Copy"
    val blue = Color(0xFF98CAFF)
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier = Modifier
            .background(Color(0xFF263C58), shape)
            .border(1.dp, Color(0xFF4D759F), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "$count ${if (count == 1) "item" else "items"} pending ${action.lowercase()}"
            }
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.3.dp.toPx()
            if (cut) {
                val w = size.width
                val h = size.height
                drawLine(
                    blue, Offset(w * 0.33f, h * 0.72f), Offset(w * 0.86f, h * 0.13f),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
                drawLine(
                    blue, Offset(w * 0.67f, h * 0.72f), Offset(w * 0.14f, h * 0.13f),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
                drawCircle(
                    blue, radius = 2.dp.toPx(), center = Offset(w * 0.27f, h * 0.79f),
                    style = Stroke(stroke)
                )
                drawCircle(
                    blue, radius = 2.dp.toPx(), center = Offset(w * 0.73f, h * 0.79f),
                    style = Stroke(stroke)
                )
            } else {
                val unit = 1.dp.toPx()
                drawRoundRect(
                    color = blue,
                    topLeft = Offset(1 * unit, 1 * unit),
                    size = Size(8 * unit, 9 * unit),
                    cornerRadius = CornerRadius(unit),
                    style = Stroke(stroke)
                )
                drawRoundRect(
                    color = blue,
                    topLeft = Offset(4 * unit, 4 * unit),
                    size = Size(9 * unit, 9 * unit),
                    cornerRadius = CornerRadius(unit),
                    style = Stroke(stroke)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        BasicText(
            text = "$action · $count",
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}
