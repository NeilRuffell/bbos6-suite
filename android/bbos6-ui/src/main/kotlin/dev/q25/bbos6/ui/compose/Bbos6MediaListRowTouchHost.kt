package dev.q25.bbos6.ui.compose

import android.content.Context
import android.view.MotionEvent
import androidx.compose.ui.platform.AbstractComposeView
import kotlin.math.roundToInt

/**
 * Native ListView-compatible host for a Compose-painted BBOS6 media row.
 *
 * The parent list owns normal row touch, selection, activation, long-press,
 * scroll and Q25 trackpad. Returning false on ordinary ACTION_DOWN events
 * lets Android's ListView handle the entire gesture as before migration.
 * Only the explicit trailing action keeps its Compose pointer handler.
 *
 * This behavior is reusable across applications; callers own action logic.
 */
abstract class Bbos6MediaListRowTouchHost(context: Context) : AbstractComposeView(context) {
    private var trailingActionEnabled = false
    private var routingTrailingAction = false

    protected fun setTrailingActionTouchEnabled(enabled: Boolean) {
        trailingActionEnabled = enabled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            routingTrailingAction = trailingActionEnabled &&
                Bbos6MediaListTouchGeometry.hitTrailingAction(
                    event.x, event.y, width, height, resources.displayMetrics.density
                )
            if (!routingTrailingAction) return false
        }
        if (!routingTrailingAction) return false
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) {
            routingTrailingAction = false
        }
        return handled
    }
}

/** Shared Compose face and native touch hitbox must use the same geometry. */
object Bbos6MediaListTouchGeometry {
    const val ACTION_WIDTH_DP = 76
    const val ACTION_HEIGHT_DP = 36
    const val ACTION_START_GAP_DP = 4
    const val ACTION_END_GAP_DP = 10

    @JvmStatic
    fun hitTrailingAction(
        x: Float, y: Float, rowWidthPx: Int, rowHeightPx: Int, density: Float
    ): Boolean {
        if (density <= 0f || rowWidthPx <= 0 || rowHeightPx <= 0) return false
        val widthPx = (ACTION_WIDTH_DP * density).roundToInt()
        val heightPx = (ACTION_HEIGHT_DP * density).roundToInt()
        val endPx = (ACTION_END_GAP_DP * density).roundToInt()
        val left = rowWidthPx - endPx - widthPx
        val top = (rowHeightPx - heightPx) / 2
        return x >= left && x < rowWidthPx - endPx &&
            y >= top && y < top + heightPx
    }
}
