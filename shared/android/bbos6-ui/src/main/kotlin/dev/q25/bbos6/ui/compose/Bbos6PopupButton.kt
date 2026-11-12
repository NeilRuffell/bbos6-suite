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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BBOS6 popup action face. No focus or click ownership: the containing
 * popup manages selection, keyboard navigation and callbacks.
 */
@Composable
fun Bbos6PopupButton(
    label: String,
    highlighted: Boolean,
    contentSized: Boolean,
    modifier: Modifier = Modifier,
    shortcutIndex: Int? = null
) {
    val caption: AnnotatedString = buildAnnotatedString {
        append(label)
        if (shortcutIndex != null && shortcutIndex in label.indices) {
            addStyle(
                SpanStyle(textDecoration = TextDecoration.Underline),
                shortcutIndex,
                shortcutIndex + 1
            )
        }
    }
    Box(
        modifier = modifier
            .then(if (contentSized) Modifier.defaultMinSize(minWidth = 48.dp) else Modifier)
            .height(30.dp)
            .bbos6PopupButtonFace(highlighted)
            .padding(horizontal = if (contentSized) 14.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = caption,
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
