package dev.q25.bbmusic

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingArtwork

/**
 * Music-only binding adapter; the actual FIT_CENTER artwork renderer is
 * shared. Existing Java media-loader and stale-key behavior is unchanged.
 */
class MusicNowPlayingArtworkView(context: Context) : AbstractComposeView(context) {
    private var coverBitmap by mutableStateOf<Bitmap?>(null)
    private var resourceId by mutableIntStateOf(R.drawable.bbos6_album_no_art)
    private var showImage by mutableStateOf(true)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    fun setImageResource(resId: Int) {
        coverBitmap = null
        resourceId = resId
        showImage = resId != 0
    }

    fun setImageBitmap(bitmap: Bitmap?) {
        coverBitmap = bitmap
        showImage = bitmap != null
    }

    fun clear() {
        coverBitmap = null
        resourceId = 0
        showImage = false
    }

    @Composable
    override fun Content() {
        val art: Painter? = if (!showImage) null else {
            val image = coverBitmap
            if (image != null) {
                remember(image) { BitmapPainter(image.asImageBitmap()) }
            } else {
                resourceId.takeIf { it != 0 }?.let { painterResource(id = it) }
            }
        }
        Bbos6NowPlayingArtwork(
            painter = art,
            contentDescription = "Album artwork"
        )
    }
}
