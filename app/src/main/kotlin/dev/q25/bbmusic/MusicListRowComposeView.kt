package dev.q25.bbmusic

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import dev.q25.bbos6.ui.compose.Bbos6MediaListRowTouchHost
import androidx.compose.ui.res.painterResource
import dev.q25.bbos6.ui.compose.Bbos6MediaListRow
import dev.q25.bbos6.ui.compose.Bbos6NativeDrawablePainter

/**
 * Kotlin presentation adapter for the existing native MusicListView.
 * ListView continues to own recycling, scrolling, row activation,
 * long-press, trackpad/DPAD focus, selection and artwork cache requests.
 */
class MusicListRowComposeView(context: Context) : Bbos6MediaListRowTouchHost(context) {
    private var rowTitle by mutableStateOf("")
    private var rowSubtitle by mutableStateOf<String?>(null)
    private var rowDuration by mutableStateOf<String?>(null)
    private var rowTrackNumber by mutableStateOf<String?>(null)
    private var rowHighlightState by mutableStateOf(false)
    private var removeVisible by mutableStateOf(false)
    private var removeCallback by mutableStateOf<Runnable?>(null)

    private var artworkMode by mutableStateOf(0)
    private var rowArtworkImage by mutableStateOf<Bitmap?>(null)
    private var artworkDescription by mutableStateOf("Album artwork")
    private var imageBindingKey: String? = null
    private val missingCover = MusicCoverPlaceholderDrawable()

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    /** The existing Java adapter supplies its unmodified row presentation data. */
    fun bindText(
        title: String?,
        subtitle: String?,
        duration: String?,
        trackNumber: String?,
        showRemove: Boolean,
        onRemove: Runnable?
    ) {
        rowTitle = title.orEmpty()
        rowSubtitle = subtitle
        rowDuration = duration
        rowTrackNumber = trackNumber
        removeVisible = showRemove
        removeCallback = onRemove
        // Delegate only the actual trailing Remove touch target to Compose.
        // All ordinary row taps/long presses must reach the native ListView.
        setTrailingActionTouchEnabled(showRemove && onRemove != null)
    }

    fun setHighlighted(isHighlighted: Boolean) {
        rowHighlightState = isHighlighted
    }

    fun getArtworkBindingKey(): String? = imageBindingKey

    fun setArtworkBindingKey(key: String?) {
        imageBindingKey = key
    }

    fun hideArtwork() {
        artworkMode = 0
        rowArtworkImage = null
    }

    fun setMenuIconBitmap(image: Bitmap?, description: String) {
        rowArtworkImage = image
        artworkDescription = description
        artworkMode = 2
    }

    fun setArtworkBitmap(image: Bitmap) {
        rowArtworkImage = image
        artworkDescription = "Album artwork"
        artworkMode = 1
    }

    fun showAlbumFallback() {
        rowArtworkImage = null
        artworkDescription = "Album artwork"
        artworkMode = 3
    }

    fun showGenericFallback() {
        rowArtworkImage = null
        artworkDescription = "Album artwork"
        artworkMode = 4
    }

    @Composable
    override fun Content() {
        val picture: Painter? = when (artworkMode) {
            1, 2 -> rowArtworkImage?.let { image ->
                androidx.compose.runtime.remember(image) {
                    BitmapPainter(image.asImageBitmap())
                }
            }
            3 -> painterResource(R.drawable.bbos6_album_no_art)
            4 -> androidx.compose.runtime.remember(missingCover) {
                Bbos6NativeDrawablePainter(missingCover)
            }
            else -> null
        }
        val removal = removeCallback
        Bbos6MediaListRow(
            title = rowTitle,
            subtitle = rowSubtitle,
            duration = rowDuration,
            trackNumber = rowTrackNumber,
            showArtwork = artworkMode != 0,
            artwork = picture,
            artworkContentDescription = artworkDescription,
            menuArtwork = artworkMode == 2,
            highlighted = rowHighlightState,
            showRemoveAction = removeVisible,
            onRemove = if (removal == null) null else { { removal.run() } }
        )
    }
}
