package dev.q25.bbos6.ui.compose

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Visual styles only: the caller still owns button focus and activation. */
enum class Bbos6NowPlayingControlKind { MODE, TRANSPORT }

private val transportSelection = Brush.verticalGradient(
    listOf(
        Color(0xFF0871CE), Color(0xFF0871CE), Color(0xFF086DC6),
        Color(0xFF086DC6), Color(0xFF0869BD), Color(0xFF0865BD),
        Color(0xFF0861B5), Color(0xFF105DB5), Color(0xFF1059AD),
        Color(0xFF1055A5), Color(0xFF1055A5)
    )
)

/**
 * Transparent unless the caller's *actual* focus state highlights this slot.
 *
 * MODE uses the canonical shared media-list selection gradient, inset 1dp
 * vertically to preserve the mode header's original silver edge rules.
 * TRANSPORT uses the original BBOS6 transport selection stops with no insets.
 * No listener, cursor, focus target or application state is created.
 */
@Composable
fun Bbos6NowPlayingControlSelection(
    kind: Bbos6NowPlayingControlKind,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (highlighted) {
            when (kind) {
                Bbos6NowPlayingControlKind.MODE ->
                    Box(
                        Modifier.fillMaxSize().padding(vertical = 1.dp)
                            .bbos6MediaListSelectionFace(true)
                    )
                Bbos6NowPlayingControlKind.TRANSPORT ->
                    Box(Modifier.fillMaxSize().background(transportSelection))
            }
        }
    }
}

/** Existing Android button overlays supply touch and accessibility events. */
class Bbos6NowPlayingControlSelectionView(
    context: Context,
    private val kind: Bbos6NowPlayingControlKind
) : AbstractComposeView(context) {
    private var faceVisible by mutableStateOf(false)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setHighlighted(highlighted: Boolean) {
        faceVisible = highlighted
    }

    @Composable
    override fun Content() {
        Bbos6NowPlayingControlSelection(kind, faceVisible)
    }
}

/**
 * The original MusicPlayerChrome.seekRailBackground rendered an unpadded
 * full-width rail, independently of the native SeekBar thumb's padded travel.
 * This Compose canvas uses exactly the same geometry and colors.
 */
@Composable
fun Bbos6NowPlayingSeekRail(
    progress: Int,
    maximum: Int,
    endGuard: Dp,
    trackHeight: Dp,
    modifier: Modifier = Modifier
) {
    val fraction = bbos6NowPlayingRailFraction(progress, maximum)
    Canvas(modifier.fillMaxSize()) {
        val guard = endGuard.toPx().roundToInt()
        val railHeight = trackHeight.toPx().roundToInt()
        val width = size.width - 2f * guard
        if (width <= 0f || railHeight <= 0) return@Canvas
        val railTop = size.height.roundToInt() / 2 - railHeight / 2
        val upperHeight = railHeight / 2
        val left = guard.toFloat()

        if (upperHeight > 0) {
            drawRect(
                color = Color(0xFF101010),
                topLeft = Offset(left, railTop.toFloat()),
                size = Size(width, upperHeight.toFloat())
            )
        }
        drawRect(
            color = Color(0xFF393839),
            topLeft = Offset(left, (railTop + upperHeight).toFloat()),
            size = Size(width, (railHeight - upperHeight).toFloat())
        )
        if (fraction > 0f) {
            drawRect(
                color = Color.White,
                topLeft = Offset(left, railTop.toFloat()),
                size = Size(width * fraction, railHeight.toFloat())
            )
        }
    }
}

/** A safe, testable progress normalization rule, not playback logic. */
internal fun bbos6NowPlayingRailFraction(progress: Int, maximum: Int): Float =
    if (maximum <= 0) 0f else (progress.toFloat() / maximum).coerceIn(0f, 1f)

/**
 * Passive Compose rail. The original native SeekBar remains above it,
 * controlling the thumb, touch events and accessibility.
 */
class Bbos6NowPlayingSeekRailView(
    context: Context,
    private val maximum: Int,
    private val guardDp: Int,
    private val trackHeightDp: Int
) : AbstractComposeView(context) {
    private var progressValue by mutableIntStateOf(0)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun updateProgress(value: Int) {
        progressValue = value
    }

    @Composable
    override fun Content() {
        Bbos6NowPlayingSeekRail(
            progress = progressValue,
            maximum = maximum,
            endGuard = guardDp.dp,
            trackHeight = trackHeightDp.dp
        )
    }
}

/** Original FIT_CENTER album art, with 6dp breathing room on every side. */
@Composable
fun Bbos6NowPlayingArtwork(
    painter: Painter?,
    contentDescription: String?,
    padding: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentScale = ContentScale.Fit
            )
        }
    }
}
