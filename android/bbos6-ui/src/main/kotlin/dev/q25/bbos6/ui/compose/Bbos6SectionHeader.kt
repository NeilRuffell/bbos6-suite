package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Non-selectable BBOS6 settings/list section heading.
 *
 * Appearance matches the pre-existing 36dp Music Settings header View.
 * Selection, list position, focus and tap actions remain with the caller.
 * This component never requests focus or intercepts keyboard/trackpad input.
 */
@Composable
fun Bbos6SectionHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF4B4E53), Color(0xFF292C31))
                )
            )
            .border(1.dp, Color(0xFF5A5D63), RectangleShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = Color(0xFFE8EBEF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
