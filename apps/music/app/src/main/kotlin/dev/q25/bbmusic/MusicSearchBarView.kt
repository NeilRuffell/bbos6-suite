package dev.q25.bbmusic

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import dev.q25.bbos6.ui.Bbos6TextField
import dev.q25.bbos6.ui.compose.Bbos6SearchChrome
import kotlin.math.roundToInt

/**
 * Search is still the original native Bbos6TextField with all keyboard,
 * text selection, IME, shortcuts and TextWatcher behavior unchanged.
 * The surrounding charcoal panel is now canonical shared Compose.
 */
class MusicSearchBarView(context: Context, input: Bbos6TextField) : FrameLayout(context) {
    init {
        isFocusable = false
        isFocusableInTouchMode = false
        clipChildren = false

        val face = ComposeView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            setContent { Bbos6SearchChrome() }
        }
        addView(face, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val inputParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            leftMargin = dp(6f)
            topMargin = dp(6f)
            rightMargin = dp(6f)
            bottomMargin = dp(5f)
        }
        addView(input, inputParams)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        @JvmStatic
        fun heightDp(): Int = 54
    }
}
