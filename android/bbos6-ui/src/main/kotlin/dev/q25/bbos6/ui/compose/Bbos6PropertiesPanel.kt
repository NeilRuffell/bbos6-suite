package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.q25.bbos6.ui.Bbos6PropertiesItem

private val propertiesRim = Brush.verticalGradient(
    listOf(Color(0xFF73797D), Color(0xFF4B5055))
)
private val propertiesText = TextStyle(
    color = Color.White,
    fontSize = 15.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * Read-only Compose replacement for the shared BBOS6 Properties content.
 *
 * Caller owns the dialog window (90% available width/height, centered,
 * outside-touch disabled, Back dismissal, no dim) and the item list.
 * No filesystem, metadata, storage or application navigation is accessed.
 *
 * Pass the existing Bbos6PropertiesItem models unchanged; while migrating,
 * the native Java popup remains usable until full Q25 input parity is tested.
 */
@Composable
fun Bbos6PropertiesPanel(
    items: List<Bbos6PropertiesItem>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(propertiesRim)
            .padding(1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF212429))
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            items.forEach { item ->
                when (item.kind()) {
                    Bbos6PropertiesItem.Kind.SPACER -> {
                        if (item.spaceDp() > 0) Spacer(Modifier.height(item.spaceDp().dp))
                    }
                    Bbos6PropertiesItem.Kind.TRAILING_PROPERTY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(item.label(), style = propertiesText)
                            Spacer(Modifier.width(6.dp))
                            BasicText(
                                item.value(),
                                modifier = Modifier.weight(1f),
                                style = propertiesText.copy(textAlign = TextAlign.End)
                            )
                        }
                    }
                    Bbos6PropertiesItem.Kind.INLINE_PROPERTY -> {
                        PropertiesLine(
                            item.label() + if (item.value().isEmpty()) "" else " " + item.value()
                        )
                    }
                    Bbos6PropertiesItem.Kind.TEXT -> PropertiesLine(item.label())
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun PropertiesLine(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(text, style = propertiesText)
    }
}
