package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BBOS6 popup action face. Contains no click handler, focus owner or navigation
 * state: the enclosing popup supplies those, exactly as in the native helper.
 *
 * For vertical popup layouts the caller supplies the width (176dp in the
 * existing popup). Horizontal groups use [contentSized] with intrinsic width.
 */
@Composable
fun Bbos6PopupButton(
    label: String,
    highlighted: Boolean,
    contentSized: Boolean,
    modifier: Modifier = Modifier
) {
    val width = if (contentSized) Modifier.defaultMinSize(minWidth = 48.dp) else Modifier
    Box(
        modifier = modifier
            .then(width)
            .height(30.dp)
            .bbos6PopupButtonFace(highlighted)
            .padding(horizontal = if (contentSized) 14.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (highlighted) Color.White else Color(0xFF242629),
                fontSize = 17.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
