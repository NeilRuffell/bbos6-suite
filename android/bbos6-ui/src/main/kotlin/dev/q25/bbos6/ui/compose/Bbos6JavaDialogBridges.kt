package dev.q25.bbos6.ui.compose

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.q25.bbos6.ui.Bbos6ContextMenu
import dev.q25.bbos6.ui.Bbos6ContextMenuItem
import dev.q25.bbos6.ui.Bbos6PropertiesItem
import dev.q25.bbos6.ui.Bbos6PropertiesPopup
import dev.q25.bbos6.ui.Bbos6PopupButtonList
import dev.q25.bbos6.ui.Bbos6FocusPolicy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One shared Android Dialog host for legacy Java callers of canonical Compose
 * modal visuals. The owning Activity supplies view-tree lifecycle/state and
 * receives the original Dialog so dismiss/Back semantics remain unchanged.
 */
private fun createComposeDialog(
    activity: ComponentActivity,
    dismissOnOutside: Boolean,
    dimBehind: Boolean
): Pair<Dialog, ComposeView> {
    val dialog = Dialog(activity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setCancelable(true)
    dialog.setCanceledOnTouchOutside(dismissOnOutside)
    val content = ComposeView(activity).apply {
        setViewTreeLifecycleOwner(activity)
        setViewTreeSavedStateRegistryOwner(activity)
        setViewTreeViewModelStoreOwner(activity)
        isFocusableInTouchMode = true
        setBackgroundColor(Color.TRANSPARENT)
    }
    // Disable Android's native focus wash at the outer ComposeView host.
    // The inner AndroidComposeView is handled after composition is attached.
    Bbos6FocusPolicy.forManagedContainers(content)
    dialog.setContentView(content)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setGravity(Gravity.CENTER)
        if (dimBehind) {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.36f)
        } else {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    return dialog to content
}

private fun availableViewport(activity: ComponentActivity): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics: WindowMetrics = activity.windowManager.currentWindowMetrics
        val bounds: Rect = metrics.bounds
        val safeInsets: Insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        return max(1, bounds.width() - safeInsets.left - safeInsets.right) to
            max(1, bounds.height() - safeInsets.top - safeInsets.bottom)
    }
    val metrics = activity.resources.displayMetrics
    return max(1, metrics.widthPixels) to max(1, metrics.heightPixels)
}

/** Maps verified Java model values without changing action IDs or icons. */
internal fun javaContextActions(
    groups: List<List<Bbos6ContextMenuItem>>
): List<List<Bbos6ContextAction>> = groups.map { group ->
    group.map { item ->
        Bbos6ContextAction(
            id = item.id(),
            label = item.label(),
            iconResourceId = item.iconResourceId(),
            iconDrawable = item.iconDrawable(),
            enabled = item.enabled()
        )
    }
}

internal fun javaPropertyRows(items: List<Bbos6PropertiesItem>): List<Bbos6PropertyRow> =
    items.map { item ->
        when (item.kind()) {
            Bbos6PropertiesItem.Kind.TEXT -> Bbos6PropertyRow.Text(item.label())
            Bbos6PropertiesItem.Kind.INLINE_PROPERTY ->
                Bbos6PropertyRow.Inline(item.label(), item.value())
            Bbos6PropertiesItem.Kind.TRAILING_PROPERTY ->
                Bbos6PropertyRow.Trailing(item.label(), item.value())
            Bbos6PropertiesItem.Kind.SPACER ->
                Bbos6PropertyRow.Spacer(item.spaceDp())
        }
    }

/**
 * Java Bbos6ContextMenu.Builder retains its public API and validation.
 * Only Activities that support Compose lifecycle owners use this renderer;
 * plain platform Activities continue through the existing native fallback.
 */
object Bbos6ContextMenuComposeBridge {
    @JvmStatic
    fun show(
        activity: ComponentActivity,
        javaGroups: List<List<Bbos6ContextMenuItem>>,
        columns: Int,
        initialSelectionId: String?,
        dismissOnAction: Boolean,
        onAction: Bbos6ContextMenu.OnActionListener
    ): Dialog {
        val originals = javaGroups.flatten().associateBy { it.id() }
        val groups = javaContextActions(javaGroups)
        val viewport = availableViewport(activity)
        val visibleColumns = min(columns, javaGroups.maxOf { it.size })
        val desiredWidthDp = when (visibleColumns) {
            1 -> 112
            2 -> 212
            else -> 312
        }
        val pixelWidth = min(
            viewport.first,
            (desiredWidthDp * activity.resources.displayMetrics.density).roundToInt()
        ).coerceAtLeast(1)
        val (dialog, content) = createComposeDialog(
            activity, dismissOnOutside = true, dimBehind = false
        )
        content.setContent {
            Bbos6ManagedFocusEffect()
            Bbos6ContextMenuGrid(
                groups = groups,
                columns = columns,
                initialSelectionId = initialSelectionId,
                onEscape = dialog::dismiss,
                onAction = { item ->
                    if (dismissOnAction) dialog.dismiss()
                    originals[item.id]?.let(onAction::onAction)
                }
            )
        }
        dialog.show()
        dialog.window?.setLayout(pixelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}

/** Java Bbos6PropertiesPopup retains show(Activity, List) and Dialog return. */
object Bbos6PropertiesComposeBridge {
    @JvmStatic
    fun show(activity: ComponentActivity, source: List<Bbos6PropertiesItem>): Dialog {
        val items = javaPropertyRows(source)
        val viewport = availableViewport(activity)
        val width = max(1, (viewport.first * 0.90f).roundToInt())
        val maxHeight = max(1, (viewport.second * 0.90f).roundToInt())
        val (dialog, content) = createComposeDialog(
            activity, dismissOnOutside = false, dimBehind = false
        )
        content.setContent {
            Bbos6ManagedFocusEffect()
            val height = with(LocalDensity.current) { maxHeight.toDp() }
            Box(Modifier.fillMaxWidth().heightIn(max = height)) {
                Bbos6PropertiesPanel(items, Modifier.fillMaxWidth())
            }
        }
        dialog.show()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}

/**
 * Compatibility bridge for existing Java button-only popups.
 * Input popups stay native so their verified Android EditText/IME/DPAD
 * behavior is never silently changed. No second popup design is maintained.
 */
object Bbos6PopupButtonComposeBridge {
    @JvmStatic
    fun show(
        activity: ComponentActivity,
        buttons: List<Bbos6PopupAction>,
        title: String?,
        message: String?,
        titleIconResource: Int,
        titleDrawable: Drawable?,
        initialSelectionId: String?,
        horizontalButtons: Boolean,
        dismissOnAction: Boolean,
        listener: Bbos6PopupButtonList.OnActionListener
    ): Dialog {
        val viewport = availableViewport(activity)
        val width = min(
            viewport.first,
            (320f * activity.resources.displayMetrics.density).roundToInt()
        ).coerceAtLeast(1)
        val (dialog, content) = createComposeDialog(
            activity, dismissOnOutside = false, dimBehind = true
        )
        content.setContent {
            Bbos6ManagedFocusEffect()
            val context = LocalContext.current
            val icon = remember(context, titleIconResource, titleDrawable) {
                val drawable = titleDrawable
                    ?: titleIconResource.takeIf { it != 0 }?.let(context::getDrawable)
                drawable?.let(::Bbos6NativeDrawablePainter)
            }
            Bbos6PopupButtonPanel(
                actions = buttons,
                onAction = { id, _ ->
                    if (dismissOnAction) dialog.dismiss()
                    listener.onAction(id)
                },
                onEscape = dialog::dismiss,
                title = title,
                message = message,
                titleIcon = icon,
                initialSelectionId = initialSelectionId,
                horizontalButtons = horizontalButtons
            )
        }
        dialog.show()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}
