package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

private val searchChromeFace = Brush.verticalGradient(
    listOf(Color(0xFF4B4E53), Color(0xFF292C31))
)

/**
 * BBOS6 search-header chrome only (charcoal body and 1dp top rule).
 * Hosts own text entry, focus, inset placement and search business logic.
 * This helper does not intercept any input.
 */
@Composable
fun Bbos6SearchChrome(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize()
            .background(searchChromeFace)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = Color(0xFF5A5D63),
                    start = Offset(0f, strokeWidth / 2f),
                    end = Offset(size.width, strokeWidth / 2f),
                    strokeWidth = strokeWidth
                )
            }
    )
}
