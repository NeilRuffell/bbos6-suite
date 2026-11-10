package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val fieldRim = Brush.verticalGradient(
    listOf(Color(0xFF4E555B), Color(0xFF6B7074))
)
private val fieldInset = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFFD6D7D6), 0.04f to Color(0xFFE7E7E7),
        0.08f to Color(0xFFEFEFEF), 0.12f to Color(0xFFF7F7F7),
        0.18f to Color.White, 1.00f to Color.White
    )
)
private val fieldTextStyle = TextStyle(color = Color(0xFF24272B), fontSize = 17.sp)

/**
 * Canonical Compose BBOS6 text input. No custom text engine or business logic.
 * Optional focus integration permits a popup to hand focus between the input
 * and the popup button owner while preserving the remembered button index.
 */
@Composable
fun Bbos6TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    minimumHeight: Dp = 43.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onInputFocusChanged: (Boolean) -> Unit = {}
) {
    Box(
        modifier = modifier.defaultMinSize(minHeight = minimumHeight)
            .clip(RoundedCornerShape(5.dp))
            .background(fieldRim)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fieldInset),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { onInputFocusChanged(it.isFocused) }
                .padding(horizontal = 11.dp),
            singleLine = true,
            textStyle = fieldTextStyle,
            cursorBrush = SolidColor(Color(0xFF24272B)),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty() && !hint.isNullOrEmpty()) {
                        BasicText(text = hint,
                            style = fieldTextStyle.copy(color = Color(0xFFB6B7BB)))
                    }
                    innerTextField()
                }
            }
        )
    }
}
