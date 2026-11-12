package dev.q25.bbos6.ui.compose

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

/** Bounded selection policy, independent of app data or Android input dispatch. */
internal object Bbos6PageListNavigation {
    fun move(current: Int, direction: Int, count: Int): Int {
        if (count <= 0) return -1
        return (current.coerceIn(0, count - 1) + direction).coerceIn(0, count - 1)
    }
}

/**
 * Reusable, full-width Compose list for BBOS6 pages.
 *
 * This controller owns exactly one focus zone and handles DPAD/trackpad
 * movement, Enter/center activation on key release, touch activation,
 * selection retention, scrolling and optional long-press/Menu action dispatch. The\n * caller owns row IDs, context actions and screen navigation.
 * Presentation delegates to the canonical Bbos6MediaListRow.
 *
 * Use Bbos6SectionHeader separately for non-selectable page headings.
 */
@Composable
fun Bbos6PageList(
    items: List<Bbos6ListSelectionItem>,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier,
    rowHeightDp: Int = 52,
    onLongPress: ((String) -> Unit)? = null,
    onMenu: ((String?) -> Unit)? = null,
    artworkForId: (String) -> Painter? = { null },
    textHorizontalPaddingDp: Int = 16,
    artworkSlotSizeDp: Int = rowHeightDp
) {
    require(rowHeightDp > 0) { "Row height must be positive" }
    require(artworkSlotSizeDp > 0 && artworkSlotSizeDp <= rowHeightDp) {
        "Artwork slot must fit inside row height"
    }
    require(textHorizontalPaddingDp >= 0) { "Text padding must not be negative" }
    require(items.map { it.id }.toSet().size == items.size) {
        "Duplicate list item ID"
    }
    Bbos6ManagedFocusEffect()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    var heldConfirm by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }
    var longPressTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(items) {
        selected = if (items.isEmpty()) 0 else selected.coerceIn(0, items.lastIndex)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(selected, items.size) {
        if (items.isNotEmpty()) {
            val layout = listState.layoutInfo
            val visible = layout.visibleItemsInfo
            val current = visible.firstOrNull { it.index == selected }
            if (current == null || current.offset < layout.viewportStartOffset ||
                current.offset + current.size > layout.viewportEndOffset) {
                if (visible.isEmpty() || selected <= visible.first().index) {
                    listState.scrollToItem(selected)
                } else {
                    val fullyVisibleCount = visible.count {
                        it.offset >= layout.viewportStartOffset &&
                            it.offset + it.size <= layout.viewportEndOffset
                    }.coerceAtLeast(1)
                    listState.scrollToItem((selected - fullyVisibleCount + 1).coerceAtLeast(0))
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // A blank list still needs a touch/long-press path to the
            // enclosing page's context menu (e.g. New Folder).
            .then(
                if (items.isEmpty() && onMenu != null) {
                    Modifier
                        .pointerInput(onMenu) {
                            detectTapGestures(onLongPress = { onMenu(null) })
                        }
                        .semantics {
                            onLongClick {
                                onMenu(null)
                                true
                            }
                        }
                } else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused }
            .onPreviewKeyEvent { key ->
                val event = key.nativeKeyEvent
                val down = event.action == AndroidKeyEvent.ACTION_DOWN
                when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down) {
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            selected = Bbos6PageListNavigation.move(
                                selected,
                                if (event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) -1 else 1,
                                items.size
                            ).coerceAtLeast(0)
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (down && event.repeatCount == 0) {
                            heldConfirm = event.keyCode
                            longPressTriggered = false
                        }
                        if (down && event.repeatCount > 0 && event.isLongPress &&
                            !longPressTriggered && onLongPress != null) {
                            longPressTriggered = true
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            items.getOrNull(selected)?.let { onLongPress(it.id) }
                        }
                        if (!down) {
                            val invoke = heldConfirm == event.keyCode && !event.isCanceled &&
                                !longPressTriggered
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            longPressTriggered = false
                            if (invoke) items.getOrNull(selected)?.let { onActivate(it.id) }
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU -> {
                        if (!down && !event.isCanceled && onMenu != null) {
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            onMenu(items.getOrNull(selected)?.id)
                        }
                        onMenu != null
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val itemArtwork = artworkForId(item.id)
            Bbos6MediaListRow(
                title = item.label,
                subtitle = null,
                duration = null,
                trackNumber = null,
                showArtwork = itemArtwork != null,
                artwork = itemArtwork,
                artworkContentDescription = "", // Decorative; row label announces the item.
                menuArtwork = true,
                highlighted = hasFocus && selected == index,
                showRemoveAction = false,
                onRemove = null,
                modifier = Modifier
                    .pointerInput(item.id, onLongPress) {
                        detectTapGestures(
                            onTap = {
                                selected = index
                                focusRequester.requestFocus()
                                onActivate(item.id)
                            },
                            onLongPress = if (onLongPress != null) ({
                                selected = index
                                focusRequester.requestFocus()
                                onLongPress(item.id)
                            }) else null
                        )
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = item.label
                        onClick {
                            selected = index
                            focusRequester.requestFocus()
                            onActivate(item.id)
                            true
                        }
                        if (onLongPress != null) onLongClick {
                            selected = index
                            focusRequester.requestFocus()
                            onLongPress(item.id)
                            true
                        }
                    },
                rowHeightDp = rowHeightDp,
                artworkSlotSizeDp = artworkSlotSizeDp,
                textHorizontalPaddingDp = textHorizontalPaddingDp
            )
        }
    }
}
