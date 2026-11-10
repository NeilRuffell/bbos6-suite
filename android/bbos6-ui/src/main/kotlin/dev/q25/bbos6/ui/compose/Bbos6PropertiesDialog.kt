package dev.q25.bbos6.ui.compose

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full Compose Properties modal: titleless, centered, no additional dimming,
 * outside touches ignored, Back dismisses, long content scrolls within 90%
 * of the available viewport. Caller owns rows and state.
 */
@Composable
fun Bbos6PropertiesDialog(
    items: List<Bbos6PropertyRow>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val view = LocalView.current
        val configuration = LocalConfiguration.current
        SideEffect {
            bbos6DialogWindow(view)?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        Box(
            modifier = modifier
                .width(configuration.screenWidthDp.dp * 0.90f)
                .heightIn(max = configuration.screenHeightDp.dp * 0.90f)
        ) {
            Bbos6PropertiesPanel(items, Modifier.fillMaxWidth())
        }
    }
}
