package dev.q25.bbmusic

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import dev.q25.bbos6.ui.compose.Bbos6MediaDetailHeader

/**
 * Album-page presentation only. The existing library-side artwork loader,
 * cache, request-key handling, and stale-binding guard remain unchanged.
 * The Activity still owns all metadata, selection and navigation.
 */
class MusicAlbumHeaderView internal constructor(
    context: Context,
    private val list: MusicListView
) : AbstractComposeView(context) {
    private var albumTitle by mutableStateOf("")
    private var albumArtist by mutableStateOf("")
    private var albumGenre by mutableStateOf("")
    private var albumCount by mutableStateOf("")
    private var coverBitmap by mutableStateOf<Bitmap?>(null)
    private var bindingGeneration = 0

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    @Composable
    override fun Content() {
        val artwork = coverBitmap?.let { BitmapPainter(it.asImageBitmap()) }
            ?: painterResource(id = R.drawable.bbos6_album_no_art)

        Bbos6MediaDetailHeader(
            artwork = artwork,
            artworkDescription = "Album artwork",
            title = albumTitle,
            secondLine = albumArtist,
            thirdLine = albumGenre,
            fourthLine = albumCount
        )
    }

    fun bind(
        title: String?,
        artist: String?,
        genre: String?,
        songCount: Int,
        artworkKey: String?,
        albumTracks: List<Uri>?
    ) {
        val binding = ++bindingGeneration
        albumTitle = title.orEmpty()
        albumArtist = artist.orEmpty()
        val genreLabel = if (genre.isNullOrBlank() ||
            genre.equals("Unknown Genre", ignoreCase = true)) {
            "Unknown"
        } else {
            genre
        }
        albumGenre = "($genreLabel)"
        albumCount = "$songCount" + if (songCount == 1) " Song" else " Songs"
        coverBitmap = null

        if (artworkKey != null && !albumTracks.isNullOrEmpty()) {
            list.loadAlbumArtwork(artworkKey, albumTracks) { bitmap ->
                if (binding != bindingGeneration) return@loadAlbumArtwork
                coverBitmap = bitmap
            }
        }
    }

    /** Do not let artwork callbacks from a previous page repopulate the header. */
    fun clear() {
        bindingGeneration++
        coverBitmap = null
    }

    companion object {
        /** Retain the exact Java caller's allocation: 88dp content + 1dp rule. */
        @JvmStatic
        fun heightDp(): Int = 89
    }
}
