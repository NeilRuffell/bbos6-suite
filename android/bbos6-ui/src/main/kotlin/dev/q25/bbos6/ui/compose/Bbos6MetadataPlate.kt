package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip

private val metadataRim = Brush.verticalGradient(
    listOf(Color(0xFF50545B), Color(0xFF353942), Color(0xFF191C21))
)
private val metadataFace = Brush.verticalGradient(
    listOf(Color(0xFF30343A), Color(0xFF2C3036), Color(0xFF272B30), Color(0xFF24272B))
)
private val metadataSelection = Brush.verticalGradient(
    listOf(
        Color(0xFF088EEF), Color(0xFF088AEF), Color(0xFF0886EF),
        Color(0xFF0882EF), Color(0xFF087DE7), Color(0xFF0879E7),
        Color(0xFF1075E7), Color(0xFF1071E7), Color(0xFF106DDE),
        Color(0xFF1069DE), Color(0xFF1065DE)
    )
)

/** Intrinsically text-sized BBOS6 metadata plate for Now Playing links. */
@Composable
fun Bbos6MetadataPlate(
    text: String,
    textSizeSp: Float,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .background(if (highlighted) metadataSelection else metadataRim)
            .then(if (highlighted) Modifier.border(1.dp, Color(0xFFB4E2FB), shape) else Modifier)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (highlighted) metadataSelection else metadataFace)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = textSizeSp.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                shadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 0.5f), blurRadius = 0.8f),
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            )
        )
    }
}
