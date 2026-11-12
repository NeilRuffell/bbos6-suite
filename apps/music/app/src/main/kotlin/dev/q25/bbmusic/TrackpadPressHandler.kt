package dev.q25.bbmusic

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import java.util.function.BooleanSupplier

/**
 * Kotlin Q25 confirm-key handler. Only one press and one long press are
 * delivered. This is native KeyEvent policy, not an alternate UI focus engine.
 */
internal class TrackpadPressHandler(
    private val click: Runnable,
    private val longClick: Runnable,
    private val longPressEnabled: BooleanSupplier
) {
    private val handler = Handler(Looper.getMainLooper())
    private val state = TrackpadPressState()
    private val timeout = Runnable {
        if (state.fireLongPress(longPressEnabled.getAsBoolean())) {
            longClick.run()
        }
    }

    companion object {
        @JvmStatic
        fun isConfirmKey(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }

    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!isConfirmKey(code)) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    if (state.isHeld()) cancel()
                    if (state.press(code)) {
                        handler.postDelayed(timeout, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(timeout)
                if (event.isCanceled) {
                    state.reset()
                } else if (state.release(code)) {
                    click.run()
                }
            }
            KeyEvent.ACTION_MULTIPLE -> Unit
            else -> cancel()
        }
        return true
    }

    fun cancel() {
        handler.removeCallbacks(timeout)
        state.reset()
    }
}
