package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared presentation only: the caller owns selection and keyboard/trackpad focus.
 *
 * These faces reproduce the color stops of the existing native BBOS6 helpers;
 * the context-selection and popup-button gradients intentionally differ.
 */
fun Modifier.bbos6ContextSelectionFace(cornerRadius: Dp = 7.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .background(
            Brush.verticalGradient(
                listOf(Color(0xFF138FE4), Color(0xFF0974CB), Color(0xFF0750A8))
            ),
            shape
        )
        .border(Dp.Hairline, Color(0xFF2368A0), shape)
}

/**
 * Selection must only be true when the owning button group actually has focus.
 * The caller retains its selected index separately from the active focus owner.
 */
fun Modifier.bbos6PopupButtonFace(highlighted: Boolean): Modifier {
    val shape = RoundedCornerShape(4.dp)
    val colors = if (highlighted) {
        listOf(
            Color(0xFF0A8FEF), Color(0xFF1080EA), Color(0xFF1766DB),
            Color(0xFF1766DB), Color(0xFF1766DB)
        )
    } else {
        listOf(
            Color(0xFFD8D8D9), Color(0xFFCCCDCF), Color(0xFFAFB1B4),
            Color(0xFFAFB1B4), Color(0xFFAFB1B4)
        )
    }
    return this
        .background(Brush.verticalGradient(colors), shape)
        .border(
            Dp.Hairline,
            if (highlighted) Color(0xFF3980C4) else Color(0xFF959AA2),
            shape
        )
}
