package dev.q25.bbmusic

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.viewinterop.AndroidView
import dev.q25.bbos6.ui.Bbos6FocusPolicy

/**
 * The application-level Kotlin/Compose screen host. Music's page domain,
 * ListView selection and Media3 playback remain with their existing owners.
 *
 * Swaps mounted Android children rather than reconstructing them: preserving
 * search text, native scroll offsets, artwork caches and Q25 trackpad focus.
 * Compose owns which screen is visible, the black screen canvas, and mount
 * ordering; it does not synthesize DPAD or replace native focus semantics.
 */
class MusicAppShellView(
    context: Context,
    private val library: View
) : AbstractComposeView(context) {
    private class Screen(val view: View, val attached: Runnable?)

    private var screen by mutableStateOf(Screen(library, null))

    init {
        setBackgroundColor(Color.BLACK)
        fitsSystemWindows = true
        clipChildren = true
        clipToPadding = true
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        Bbos6FocusPolicy.forManagedContainers(this)
    }

    fun showNowPlaying(player: View, afterAttached: Runnable) {
        screen = Screen(player, afterAttached)
    }

    fun showLibrary(afterAttached: Runnable?) {
        screen = Screen(library, afterAttached)
    }

    @Composable
    override fun Content() {
        val current = screen
        val enqueued = remember(current) { booleanArrayOf(false) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    clipChildren = true
                    clipToPadding = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    Bbos6FocusPolicy.forManagedContainers(this)
                }
            },
            update = { host ->
                if (host.childCount != 1 || host.getChildAt(0) !== current.view) {
                    host.removeAllViews()
                    (current.view.parent as? ViewGroup)?.removeView(current.view)
                    host.addView(
                        current.view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    // A native ViewGroup must never show Android's lighter
                    // default focus overlay over BBOS6's selection gradient.
                    Bbos6FocusPolicy.forManagedContainers(host)
                }
                if (!enqueued[0]) {
                    enqueued[0] = true
                    current.attached?.let { callback ->
                        host.post {
                            if (current.view.parent === host) callback.run()
                        }
                    }
                }
            }
        )
    }
}
