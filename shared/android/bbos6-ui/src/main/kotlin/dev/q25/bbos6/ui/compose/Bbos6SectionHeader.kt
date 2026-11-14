package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared header appearances. Defaults preserve existing Music/settings styling. */
enum class Bbos6SectionHeaderStyle {
    BEVELED,
    FLAT_BLACK_COMPACT
}

/**
 * Non-selectable BBOS6 section heading.
 *
 * BEVELED preserves the existing 36dp settings header.
 * FLAT_BLACK_COMPACT renders a 28dp flat black path bar with 2dp horizontal
 * text inset and no border, matching the user-supplied BBOS Files reference.
 * The caller owns window insets; this helper never adds status-bar padding.
 * The header does not request focus or intercept keyboard/trackpad input.
 * [trailingContent] is an optional, caller-owned passive status area. Without
 * it, layout and typography remain identical to the original section header.
 */
@Composable
fun Bbos6SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    style: Bbos6SectionHeaderStyle = Bbos6SectionHeaderStyle.BEVELED,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val compact = style == Bbos6SectionHeaderStyle.FLAT_BLACK_COMPACT
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 28.dp else 36.dp)
            .then(
                if (compact) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF4B4E53), Color(0xFF292C31))
                            )
                        )
                        .border(1.dp, Color(0xFF5A5D63), RectangleShape)
                }
            )
            .padding(horizontal = if (compact) 2.dp else 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val titleStyle = TextStyle(
            color = if (compact) Color.White else Color(0xFFE8EBEF),
            fontSize = if (compact) 15.sp else 14.sp,
            fontWeight = if (compact) FontWeight.Normal else FontWeight.Bold
        )
        if (trailingContent == null) {
            BasicText(
                text = title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                trailingContent()
            }
        }
    }
}
