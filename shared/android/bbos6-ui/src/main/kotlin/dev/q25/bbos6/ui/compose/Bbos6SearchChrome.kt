package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import android.content.Context
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

/**
 * Passive, non-selectable search empty feedback. Search criteria and
 * visibility stay with the caller; no adapter row or focus target is added.
 */
@Composable
fun Bbos6SearchEmptyFeedback(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center) {
        BasicText(
            message,
            style = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            )
        )
    }
}

/** Legacy View host for the same shared search-empty Compose appearance. */
class Bbos6SearchEmptyFeedbackView(
    context: Context,
    initialMessage: String
) : AbstractComposeView(context) {
    private var textContent by mutableStateOf(initialMessage)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setMessage(message: String) {
        textContent = message
    }

    @Composable
    override fun Content() {
        Bbos6SearchEmptyFeedback(textContent)
    }
}
