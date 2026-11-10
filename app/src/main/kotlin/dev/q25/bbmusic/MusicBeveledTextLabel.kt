package dev.q25.bbmusic

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import dev.q25.bbos6.ui.compose.Bbos6MetadataPlate

/**
 * Compose presentation of a content-width album/artist label.
 * Now Playing continues to own focus, selection and onClick navigation.
 */
class MusicBeveledTextLabel(
    context: Context,
    private val textSizeSp: Float
) : AbstractComposeView(context) {
    private var value by mutableStateOf("")
    private var metadataFocused by mutableStateOf(false)

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        clipChildren = false
        clipToPadding = false
    }

    /** Existing Java Now Playing bind contract. */
    fun setText(text: CharSequence?) {
        value = text?.toString().orEmpty()
    }

    /** Selection is still determined by Now Playing's native zone state. */
    fun setMetadataFocused(focused: Boolean) {
        metadataFocused = focused
    }

    @Composable
    override fun Content() {
        Bbos6MetadataPlate(
            text = value,
            textSizeSp = textSizeSp,
            highlighted = metadataFocused
        )
    }
}
