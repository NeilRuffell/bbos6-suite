package dev.q25.bbos6.ui.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.q25.bbos6.ui.Bbos6ContextMenuItem
import kotlin.math.min
import kotlin.math.roundToInt

private val rim = Brush.verticalGradient(listOf(Color(0xFF92A0AD), Color(0xFF6F8294), Color(0xFF3A4D5F)))
private val face = Brush.verticalGradient(listOf(Color(0xFF445D79), Color(0xFF425A75), Color(0xFF3A4D61)))
private val divider = Brush.verticalGradient(listOf(Color(0xFF72869B), Color(0xFF32495D)))

/** Compose dialog host for the shared, caller-defined context menu. */
@Composable
fun Bbos6ContextMenuDialog(
    groups: List<List<Bbos6ContextMenuItem>>,
    onDismissRequest: () -> Unit,
    onAction: (Bbos6ContextMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    initialSelectionId: String? = null,
    dismissOnAction: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true,
            dismissOnClickOutside = true, usePlatformDefaultWidth = false)
    ) {
        val localView = LocalView.current
        SideEffect {
            bbos6DialogWindow(localView)?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        Bbos6ContextMenuGrid(groups, onAction = { item ->
            if (dismissOnAction) onDismissRequest()
            onAction(item)
        }, modifier = modifier, columns = columns,
            initialSelectionId = initialSelectionId, onEscape = onDismissRequest)
    }
}

/**
 * App-neutral Compose grid. One focus owner, DPAD movement with disabled-cell
 * skipping, touch selection, and confirm-on-key-up (no held-key repeats).
 * Commands, resource mappings and app actions remain in the caller.
 */
@Composable
fun Bbos6ContextMenuGrid(
    groups: List<List<Bbos6ContextMenuItem>>,
    onAction: (Bbos6ContextMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    initialSelectionId: String? = null,
    onEscape: () -> Unit = {}
) {
    require(columns in 1..6) { "columns must be 1..6" }
    require(groups.isNotEmpty() && groups.all { it.isNotEmpty() }) {
        "At least one nonempty group is required"
    }
    val items = groups.flatten()
    require(items.map { it.id() }.toSet().size == items.size) { "Duplicate menu action ID" }
    val visibleColumns = min(columns, groups.maxOf { it.size })
    val cells = remember(groups, visibleColumns) {
        buildList {
            var row = 0
            groups.forEach { group ->
                group.chunked(visibleColumns).forEach { chunk ->
                    chunk.forEachIndexed { column, item ->
                        add(Bbos6GridCell(row, column, item.enabled()))
                    }
                    row++
                }
            }
        }
    }
    val initial = remember(items, initialSelectionId) {
        items.indexOfFirst { it.id() == initialSelectionId && it.enabled() }
            .takeIf { it >= 0 } ?: Bbos6GridNavigation.firstEnabled(cells)
    }
    var selected by remember(groups, visibleColumns, initialSelectionId) { mutableIntStateOf(initial) }
    var pressed by remember { mutableIntStateOf(-1) }
    var heldConfirm by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }
    val focusOwner = remember { FocusRequester() }
    val requesters = remember(cells) { List(cells.size) { BringIntoViewRequester() } }
    val screen = LocalConfiguration.current
    val panelWidth = minOf(when (visibleColumns) {
        1 -> 112.dp
        2 -> 212.dp
        else -> 312.dp
    }, screen.screenWidthDp.dp)
    LaunchedEffect(focusOwner) { focusOwner.requestFocus() }
    LaunchedEffect(selected, requesters) {
        requesters.getOrNull(selected)?.bringIntoView()
    }
    fun activate() {
        items.getOrNull(selected)?.takeIf { it.enabled() }?.let(onAction)
    }
    Box(
        modifier = modifier
            .width(panelWidth)
            .heightIn(max = screen.screenHeightDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(rim)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(face)
            .focusRequester(focusOwner)
            .onPreviewKeyEvent { key ->
                val event = key.nativeKeyEvent
                val direction = when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> Bbos6GridDirection.LEFT
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> Bbos6GridDirection.RIGHT
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> Bbos6GridDirection.UP
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> Bbos6GridDirection.DOWN
                    else -> null
                }
                if (direction != null) {
                    if (event.action == AndroidKeyEvent.ACTION_DOWN) {
                        heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        selected = Bbos6GridNavigation.move(cells, selected, direction)
                    }
                    true
                } else when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.action == AndroidKeyEvent.ACTION_DOWN && event.repeatCount == 0)
                            heldConfirm = event.keyCode
                        if (event.action == AndroidKeyEvent.ACTION_UP) {
                            val shouldInvoke = heldConfirm == event.keyCode && !event.isCanceled
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            if (shouldInvoke) activate()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_ESCAPE -> {
                        heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        if (event.action == AndroidKeyEvent.ACTION_UP && !event.isCanceled) onEscape()
                        true
                    }
                    else -> {
                        if (event.action == AndroidKeyEvent.ACTION_DOWN)
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        false
                    }
                }
            }
            .focusable()
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(5.dp)) {
            var itemIndex = 0
            groups.forEachIndexed { groupIndex, group ->
                group.chunked(visibleColumns).forEach { chunk ->
                    Row(Modifier.fillMaxWidth().height(94.dp)) {
                        repeat(visibleColumns) { column ->
                            if (column < chunk.size) {
                                val index = itemIndex++
                                val item = chunk[column]
                                val highlighted = (selected == index || pressed == index) && item.enabled()
                                Bbos6ContextTile(
                                    item = item, highlighted = highlighted,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .bringIntoViewRequester(requesters[index]),
                                    onPress = { isPressed ->
                                        pressed = if (isPressed) index else -1
                                    },
                                    onActivate = {
                                        if (item.enabled()) {
                                            selected = index
                                            focusOwner.requestFocus()
                                            onAction(item)
                                        }
                                    }
                                )
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (groupIndex < groups.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 9.dp)
                        .height(2.dp).background(divider))
                }
            }
        }
    }
}

@Composable
private fun Bbos6ContextTile(
    item: Bbos6ContextMenuItem, highlighted: Boolean, modifier: Modifier,
    onPress: (Boolean) -> Unit, onActivate: () -> Unit
) {
    val enabled = item.enabled()
    Column(
        modifier = modifier
            .padding(start = 2.dp, end = 2.dp, top = 3.dp, bottom = 3.dp)
            .then(if (highlighted) Modifier.bbos6ContextSelectionFace() else Modifier)
            .pointerInput(item, enabled) {
                if (enabled) detectTapGestures(
                    onPress = {
                        onPress(true)
                        try { tryAwaitRelease() } finally { onPress(false) }
                    },
                    onTap = { onActivate() }
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = item.label()
                if (!enabled) disabled() else onClick { onActivate(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Bbos6ContextIcon(item)
        Spacer(Modifier.height(3.dp))
        BasicText(
            text = item.label(),
            modifier = Modifier.padding(horizontal = 2.dp),
            style = TextStyle(color = Color.White.copy(alpha = if (enabled) 1f else 0.42f),
                fontSize = 14.sp, textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false)),
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Original caller-supplied Android drawables are rasterized without adding Android Views. */
@Composable
private fun Bbos6ContextIcon(item: Bbos6ContextMenuItem) {
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 42.dp.roundToPx() }
    val icon = remember(item, iconPx) {
        val drawable = item.iconDrawable()
            ?: item.iconResourceId().takeIf { it != 0 }?.let(context::getDrawable)
        drawable?.toBbos6Bitmap(iconPx)
    }
    if (icon == null) {
        Spacer(Modifier.size(42.dp))
    } else {
        Image(painter = BitmapPainter(icon.asImageBitmap()), contentDescription = null,
            modifier = Modifier.size(42.dp), contentScale = ContentScale.Fit,
            alpha = if (item.enabled()) 1f else 0.42f)
    }
}

private fun Drawable.toBbos6Bitmap(size: Int): Bitmap {
    val source = constantState?.newDrawable()?.mutate() ?: mutate()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val sourceWidth = source.intrinsicWidth.takeIf { it > 0 } ?: size
    val sourceHeight = source.intrinsicHeight.takeIf { it > 0 } ?: size
    val scale = minOf(size.toFloat() / sourceWidth, size.toFloat() / sourceHeight)
    val width = (sourceWidth * scale).roundToInt()
    val height = (sourceHeight * scale).roundToInt()
    source.bounds = Rect((size - width) / 2, (size - height) / 2,
        (size + width) / 2, (size + height) / 2)
    source.draw(Canvas(bitmap))
    return bitmap
}
