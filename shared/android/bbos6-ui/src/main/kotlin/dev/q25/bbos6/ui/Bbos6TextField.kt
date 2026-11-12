package dev.q25.bbos6.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.widget.EditText

/**
 * Kotlin implementation of the existing Q25-verified Android EditText
 * appearance and native editor behavior. No separate custom text engine.
 */
class Bbos6TextField(context: Context) : EditText(context) {
    private companion object {
        const val HEIGHT_DP = 43
        const val CORNER_DP = 5
    }

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        applyAppearance()
    }

    private fun applyAppearance() {
        val rim = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xff4e555b.toInt(), 0xff6b7074.toInt())
        ).apply { cornerRadius = dp(CORNER_DP).toFloat() }

        val stops = intArrayOf(
            0xffd6d7d6.toInt(), 0xffe7e7e7.toInt(),
            0xffefefef.toInt(), 0xfff7f7f7.toInt(),
            Color.WHITE, Color.WHITE
        )
        val inset = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, stops
        ).apply {
            setColors(stops, floatArrayOf(0f, 0.04f, 0.08f, 0.12f, 0.18f, 1f))
            cornerRadius = dp(CORNER_DP - 1).toFloat()
        }

        val face = LayerDrawable(arrayOf(rim, inset))
        face.setLayerInset(1, dp(1), dp(1), dp(1), dp(1))
        background = face
        setTextColor(0xff24272b.toInt())
        setHintTextColor(0xffb6b7bb.toInt())
        setPadding(dp(12), 0, dp(12), 0)
        minHeight = dp(HEIGHT_DP)
    }

    private fun dp(value: Int): Int =
        Math.round(resources.displayMetrics.density * value)
}
