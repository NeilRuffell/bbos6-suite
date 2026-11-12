package dev.q25.bbos6.ui.compose

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import kotlin.math.roundToInt

/**
 * Compose-backed counterpart of the original shared Bbos6EmptyStateView.
 *
 * Java-callable methods preserve the host's existing content/artwork contract.
 * This is not a selectable list row; the host continues to own empty-state
 * visibility, placement, focus, scrolling and all application behavior.
 *
 * The renderer itself is the canonical [Bbos6EmptyState] Composable.
 */
class Bbos6EmptyStateComposeView(context: Context) : AbstractComposeView(context) {
    private var heading by mutableStateOf<CharSequence?>(null)
    private var description by mutableStateOf<CharSequence?>(null)
    private var artworkResourceId by mutableIntStateOf(0)
    private var suppliedArtwork by mutableStateOf<Drawable?>(null)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    /** Null or blank text leaves that line absent, as with the native helper. */
    fun setContent(title: CharSequence?, message: CharSequence?) {
        heading = title
        description = message
    }

    /** A zero resource clears artwork without synthesizing a placeholder. */
    fun setArtworkResource(resourceId: Int) {
        artworkResourceId = resourceId
        suppliedArtwork = null
    }

    /** Null clears artwork; non-null caller drawables are never replaced. */
    fun setArtwork(drawable: Drawable?) {
        suppliedArtwork = drawable
        artworkResourceId = 0
    }

    @Composable
    override fun Content() {
        val supplied = suppliedArtwork
        val image = if (supplied != null) {
            remember(supplied) { Bbos6NativeDrawablePainter(supplied) }
        } else {
            artworkResourceId.takeIf { it != 0 }?.let { painterResource(id = it) }
        }
        Bbos6EmptyState(
            heading = heading?.toString(),
            description = description?.toString(),
            artwork = image,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Renders caller-supplied Android Drawable assets inside Compose without a
 * second Android View, icon substitution, or resampling to a forced bitmap.
 * Intrinsic dimensions preserve Image(contentScale = Fit) aspect ratio.
 */
class Bbos6NativeDrawablePainter(private val drawable: Drawable) : Painter() {
    override val intrinsicSize: Size =
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        } else {
            Size.Unspecified
        }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            try {
                drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                drawable.draw(native)
            } finally {
                native.restoreToCount(checkpoint)
            }
        }
    }
}
