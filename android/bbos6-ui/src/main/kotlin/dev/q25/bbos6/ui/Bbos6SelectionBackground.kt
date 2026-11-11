package dev.q25.bbos6.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import kotlin.math.roundToInt

/**
 * Native popup/menu gradient compatibility until all consumers are Compose.
 * The two independently verified blue palettes must never be interchanged.
 */
internal object Bbos6SelectionBackground {
    @JvmStatic
    fun blue(context: Context): GradientDrawable = blue(context, 7)

    @JvmStatic
    fun blue(context: Context, cornerRadiusDp: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xff138fe4.toInt(), 0xff0974cb.toInt(), 0xff0750a8.toInt())
        ).apply {
            cornerRadius = dp(context, cornerRadiusDp).toFloat()
            setStroke(maxOf(1, dp(context, 1)), 0xff2368a0.toInt())
        }

    @JvmStatic
    fun popupButtonBlue(context: Context, cornerRadiusDp: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xff0a8fef.toInt(), 0xff1080ea.toInt(),
                0xff1766db.toInt(), 0xff1766db.toInt(), 0xff1766db.toInt()
            )
        ).apply {
            cornerRadius = dp(context, cornerRadiusDp).toFloat()
            setStroke(1, 0xff3980c4.toInt())
        }

    private fun dp(context: Context, size: Int): Int =
        (context.resources.displayMetrics.density * size).roundToInt()
}
