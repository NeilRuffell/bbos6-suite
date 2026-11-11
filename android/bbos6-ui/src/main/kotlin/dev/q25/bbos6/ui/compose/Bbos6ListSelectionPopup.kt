package dev.q25.bbos6.ui.compose

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stable caller-defined item ID and a single-line caption; no media data. */
data class Bbos6ListSelectionItem(val id: String, val label: String) {
    init {
        require(id.isNotBlank()) { "Choice ID must not be blank" }
        require(label.isNotBlank()) { "Choice label must not be blank" }
    }
}

/** A vertical list stops at either end, rather than wrapping its selection. */
internal object Bbos6ListSelectionNavigation {
    fun move(current: Int, direction: Int, count: Int): Int {
        require(count > 0) { "Choice count must be positive" }
        return (current + direction).coerceIn(0, count - 1)
    }
}

/**
 * Reusable BBOS6 single-line selection panel. Uses Bbos6MediaListRow with
 * compact 40dp geometry and no artwork. Only the enclosing focus owner draws
 * the selected blue face, while Compose LazyColumn owns scroll and touch.
 *
 * No playlist, storage or application action logic belongs in this helper.
 */
@Composable
fun Bbos6ListSelectionPanel(
    title: String,
    items: List<Bbos6ListSelectionItem>,
    onSelected: (String) -> Unit,
    onEscape: () -> Unit,
    modifier: Modifier = Modifier
) {
    require(items.isNotEmpty()) { "At least one list choice is required" }
    require(items.map { it.id }.toSet().size == items.size) {
        "Duplicate list choice ID"
    }
    val configuration = LocalConfiguration.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selected by remember(items) { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    var heldConfirm by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }

    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    LaunchedEffect(selected) {
        val layout = listState.layoutInfo
        val row = layout.visibleItemsInfo.firstOrNull { it.index == selected }
        if (row == null || row.offset < layout.viewportStartOffset ||
            row.offset + row.size > layout.viewportEndOffset) {
            listState.scrollToItem(selected)
        }
    }

    Box(
        modifier = modifier
            .width(minOf(320, configuration.screenWidthDp).dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF535960))
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF212429))
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused }
            .onPreviewKeyEvent { key ->
                val event = key.nativeKeyEvent
                val down = event.action == AndroidKeyEvent.ACTION_DOWN
                when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down) {
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            selected = Bbos6ListSelectionNavigation.move(
                                selected,
                                if (event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) -1 else 1,
                                items.size
                            )
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (down && event.repeatCount == 0) heldConfirm = event.keyCode
                        if (!down) {
                            val invoke = heldConfirm == event.keyCode && !event.isCanceled
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            if (invoke) onSelected(items[selected].id)
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_ESCAPE -> {
                        heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        if (!down && !event.isCanceled) onEscape()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        Column {
            BasicText(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(
                    start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White, fontSize = 18.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = (configuration.screenHeightDp * 0.72f).dp),
                state = listState
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    Bbos6MediaListRow(
                        title = item.label,
                        subtitle = null,
                        duration = null,
                        trackNumber = null,
                        showArtwork = false,
                        artwork = null,
                        artworkContentDescription = "",
                        menuArtwork = false,
                        highlighted = hasFocus && selected == index,
                        showRemoveAction = false,
                        onRemove = null,
                        modifier = Modifier
                            .pointerInput(item.id) {
                                detectTapGestures(onTap = {
                                    selected = index
                                    onSelected(item.id)
                                })
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = item.label
                                onClick {
                                    selected = index
                                    onSelected(item.id)
                                    true
                                }
                            },
                        rowHeightDp = 40
                    )
                }
            }
        }
    }
}
