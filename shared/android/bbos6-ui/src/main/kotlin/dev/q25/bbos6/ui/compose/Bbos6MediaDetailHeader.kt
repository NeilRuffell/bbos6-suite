package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared, presentation-only media detail header.
 *
 * Four caller-supplied lines sit beside a square image, followed by a
 * full-width rule. The caller owns artwork lookup, text formatting,
 * navigation, data and when this header is displayed. This composable is
 * not selectable and never owns keyboard, touch, or trackpad focus.
 */
@Composable
fun Bbos6MediaDetailHeader(
    artwork: Painter,
    artworkDescription: String,
    title: String,
    secondLine: String,
    thirdLine: String,
    fourthLine: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(89.dp)
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(88.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = artwork,
                contentDescription = artworkDescription,
                modifier = Modifier.size(88.dp),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Bbos6MediaDetailLine(title, 16, Color.White)
                Bbos6MediaDetailLine(secondLine, 12, Color(0xFFC9C9C9))
                Bbos6MediaDetailLine(thirdLine, 12, Color(0xFFA9A9A9))
                Bbos6MediaDetailLine(fourthLine, 12, Color(0xFFC9C9C9))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF515251)))
    }
}

@Composable
private fun Bbos6MediaDetailLine(text: String, fontSizeSp: Int, color: Color) {
    BasicText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(color = color, fontSize = fontSizeSp.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
