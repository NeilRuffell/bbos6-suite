package dev.q25.bbos6.ui.compose

import android.content.Context
import android.view.MotionEvent
import android.view.View
import java.util.function.BooleanSupplier
import androidx.compose.ui.platform.AbstractComposeView
import kotlin.math.roundToInt

/**
 * Native ListView-compatible host for a Compose-painted BBOS6 media row.
 *
 * The host routes ordinary row gestures through Android's standard
 * View.onTouchEvent click/long-click lifecycle instead of the nested
 * AndroidComposeView. This is essential: returning false from a Compose
 * child does NOT guarantee an AbsListView parent will generate a click.
 *
 * The owning native list supplies onTap/onLongPress during each row bind,
 * and remains responsible for data, selection and item action semantics.
 * ListView can intercept scrolling and deliver ACTION_CANCEL, preventing
 * taps on flings. Only the explicit trailing action uses Compose input.
 *
 * This behavior is reusable across applications; callers own action logic.
 */
abstract class Bbos6MediaListRowTouchHost(context: Context) : AbstractComposeView(context) {
    private var trailingActionEnabled = false
    private var routingTrailingAction = false

    /**
     * Bind after every native adapter recycle. Ordinary taps and long presses
     * use the platform's View click/long-click lifecycle, including the
     * standard long-press timeout, pressed state, and gesture cancellation.
     * Disabled rows have no action and remain scrollable.
     */
    protected fun setNativeRowActions(
        onTap: Runnable?,
        onLongPress: BooleanSupplier?
    ) {
        setOnClickListener(
            if (onTap == null) null else View.OnClickListener { onTap.run() }
        )
        setOnLongClickListener(
            if (onLongPress == null) null else View.OnLongClickListener {
                onLongPress.getAsBoolean()
            }
        )
        isClickable = onTap != null
        isLongClickable = onLongPress != null
    }

    protected fun setTrailingActionTouchEnabled(enabled: Boolean) {
        trailingActionEnabled = enabled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            routingTrailingAction = trailingActionEnabled &&
                Bbos6MediaListTouchGeometry.hitTrailingAction(
                    event.x, event.y, width, height, resources.displayMetrics.density
                )
        }
        if (!routingTrailingAction) {
            // Bypass AndroidComposeView: it may consume ACTION_DOWN without
            // providing a native ListView click. The standard View touch
            // handler owns normal taps/long presses, with ListView still
            // permitted to intercept MOVE for scrolling.
            return onTouchEvent(event)
        }
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
