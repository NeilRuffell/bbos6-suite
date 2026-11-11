package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Presentation-only empty-state content; not a selectable row or focus target.
 * Its caller owns the empty-data condition, placement, optional original
 * artwork and resource mapping. Nothing is synthesized when fields are blank.
 */
@Composable
fun Bbos6EmptyState(
    heading: String?,
    description: String?,
    artwork: Painter? = null,
    artworkContentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val hasHeading = !heading.isNullOrBlank()
    val hasDescription = !description.isNullOrBlank()
    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (artwork != null) {
            Image(
                painter = artwork,
                contentDescription = artworkContentDescription,
                modifier = Modifier.size(144.dp),
                contentScale = ContentScale.Fit
            )
            if (hasHeading || hasDescription) Spacer(Modifier.height(12.dp))
        }
        if (hasHeading) {
            BasicText(
                text = heading!!,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            )
            if (hasDescription) Spacer(Modifier.height(16.dp))
        }
        if (hasDescription) {
            BasicText(
                text = description!!,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
