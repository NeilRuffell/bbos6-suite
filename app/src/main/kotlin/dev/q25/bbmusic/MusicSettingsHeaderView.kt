package dev.q25.bbmusic

import android.content.Context
import android.view.ViewGroup
import android.widget.AbsListView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import dev.q25.bbos6.ui.compose.Bbos6SectionHeader
import kotlin.math.roundToInt

/**
 * Transitional Compose presentation for the existing non-selectable
 * Settings header. The existing Java adapter still calls bind(title).
 * No list focus, click, navigation or application behavior is implemented here.
 */
class MusicSettingsHeaderView(context: Context) : AbstractComposeView(context) {
    private var heading by mutableStateOf("")

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        layoutParams = AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (36f * resources.displayMetrics.density).roundToInt()
        )
    }

    @Composable
    override fun Content() {
        Bbos6SectionHeader(heading, modifier = Modifier.fillMaxSize())
    }

    /** Existing ListView adapter contract; a null title remains empty. */
    fun bind(title: String?) {
        heading = title.orEmpty()
    }
}
