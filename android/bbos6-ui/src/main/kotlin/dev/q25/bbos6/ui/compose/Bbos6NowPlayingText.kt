package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

enum class Bbos6NowPlayingTextAlignment { START, CENTER, END }

/**
 * Caller-formatted, non-interactive Now Playing text.
 *
 * Callers own metadata, time/queue computations, trackpad focus and updates.
 * Font padding and ellipsis preserve the established Android label rules.
 */
@Composable
fun Bbos6NowPlayingText(
    value: String,
    fontSizeSp: Float,
    color: Color,
    alignment: Bbos6NowPlayingTextAlignment,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val contentAlignment = when (alignment) {
        Bbos6NowPlayingTextAlignment.START -> Alignment.CenterStart
        Bbos6NowPlayingTextAlignment.CENTER -> Alignment.Center
        Bbos6NowPlayingTextAlignment.END -> Alignment.CenterEnd
    }
    val textAlignment = when (alignment) {
        Bbos6NowPlayingTextAlignment.START -> TextAlign.Start
        Bbos6NowPlayingTextAlignment.CENTER -> TextAlign.Center
        Bbos6NowPlayingTextAlignment.END -> TextAlign.End
    }
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        BasicText(
            text = value,
            style = TextStyle(
                fontSize = fontSizeSp.sp,
                color = color,
                textAlign = textAlignment,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            maxLines = maxLines.coerceAtLeast(1),
            overflow = TextOverflow.Ellipsis
        )
    }
}
