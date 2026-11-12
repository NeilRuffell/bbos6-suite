package dev.q25.bbmusic

import android.content.Context
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingText
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingTextAlignment

/**
 * Dynamic Compose text for the existing Java player. This host owns no
 * playback, queue, time formatting, zone selection or input handlers.
 */
class MusicPlayerTextView(
    context: Context,
    initialText: String,
    private val fontSizeSp: Float,
    private val argbColor: Int,
    private val alignment: Bbos6NowPlayingTextAlignment,
    private val maximumLines: Int,
    private val minimumWidthDp: Int,
    private val stretchWidth: Boolean,
    private val stretchHeight: Boolean
) : AbstractComposeView(context) {
    private var displayText by mutableStateOf(initialText)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    /** Same Java-facing update contract as the previous TextView label. */
    fun setText(value: CharSequence?) {
        displayText = value?.toString().orEmpty()
    }

    @Composable
    override fun Content() {
        var sizing: Modifier = Modifier
        if (minimumWidthDp > 0) {
            sizing = sizing.defaultMinSize(minWidth = minimumWidthDp.dp)
        }
        sizing = when {
            stretchWidth && stretchHeight -> sizing.fillMaxSize()
            stretchWidth -> sizing.fillMaxWidth()
            stretchHeight -> sizing.fillMaxHeight()
            else -> sizing
        }
        Bbos6NowPlayingText(
            value = displayText,
            fontSizeSp = fontSizeSp,
            color = Color(argbColor),
            alignment = alignment,
            maxLines = maximumLines,
            modifier = sizing
        )
    }
}
