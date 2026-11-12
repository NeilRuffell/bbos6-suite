package dev.q25.bbos6.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** Shared native button face kept in Kotlin for Q25 mixed View/Compose popup focus. */
class Bbos6PopupButtonView(context: Context) : TextView(context) {
    companion object {
        const val HEIGHT_DP = 30
        private const val CORNER_RADIUS_DP = 4
        private const val FIXED_WIDTH_PADDING_DP = 6
        private const val CONTENT_WIDTH_PADDING_DP = 14
        private const val MIN_CONTENT_WIDTH_DP = 48
    }

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        gravity = Gravity.CENTER
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        isFocusable = false
        setContentSized(false)
        setBbos6Highlighted(false)
    }

    fun setContentSized(contentSized: Boolean) {
        val padding = if (contentSized) CONTENT_WIDTH_PADDING_DP else FIXED_WIDTH_PADDING_DP
        setPadding(dp(padding), 0, dp(padding), 0)
        minWidth = if (contentSized) dp(MIN_CONTENT_WIDTH_DP) else 0
    }

    fun setBbos6Highlighted(highlighted: Boolean) {
        background = if (highlighted) {
            Bbos6SelectionBackground.popupButtonBlue(context, CORNER_RADIUS_DP)
        } else {
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xffd8d8d9.toInt(), 0xffcccdcf.toInt(),
                    0xffafb1b4.toInt(), 0xffafb1b4.toInt(), 0xffafb1b4.toInt()
                )
            ).apply {
                cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                setStroke(1, 0xff959aa2.toInt())
            }
        }
        setTextColor(if (highlighted) Color.WHITE else 0xff242629.toInt())
    }

    private fun dp(value: Int): Int = Math.round(resources.displayMetrics.density * value)
}
