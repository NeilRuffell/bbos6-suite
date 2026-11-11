package dev.q25.bbos6.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.q25.bbos6.ui.compose.Bbos6ContextMenuComposeBridge

/**
 * Shared BBOS6 grid menu. Kotlin implementations for both the Compose dialog
 * bridge (AndroidX) and the parity-preserved native Android fallback.
 */
object Bbos6ContextMenu {
    @JvmField val TOP_PANEL_COLOR = 0xff445d79.toInt()
    @JvmField val BOTTOM_PANEL_COLOR = 0xff3a4d61.toInt()
    const val LABEL_COLOR = Color.WHITE

    private const val DEFAULT_COLUMNS = 3
    private const val MAX_COLUMNS = 6
    private const val ONE_COLUMN_WIDTH_DP = 112
    private const val TWO_COLUMN_WIDTH_DP = 212
    private const val MULTI_COLUMN_WIDTH_DP = 312
    private const val ROW_HEIGHT_DP = 94
    private const val ICON_SIZE_DP = 42
    private const val CORNER_RADIUS_DP = 7
    private const val CONTENT_INSET_DP = 5
    private const val TILE_SIDE_INSET_DP = 2
    private const val TILE_TOP_INSET_DP = 3
    private const val LABEL_SIZE_SP = 14f

    fun interface OnActionListener {
        fun onAction(item: Bbos6ContextMenuItem)
    }

    @JvmStatic fun builder() = Builder()

    class Builder {
        private val groups = ArrayList<List<Bbos6ContextMenuItem>>()
        private var columns = DEFAULT_COLUMNS
        private var initialSelectionId: String? = null
        private var dismissOnAction = true
        private var actionListener: OnActionListener? = null

        fun addGroup(vararg items: Bbos6ContextMenuItem): Builder = addGroup(items.asList())

        fun addGroup(items: List<Bbos6ContextMenuItem>): Builder = apply {
            require(items.isNotEmpty()) { "Empty group" }
            groups.add(java.util.Collections.unmodifiableList(ArrayList(items)))
        }

        fun columns(count: Int): Builder = apply {
            require(count in 1..MAX_COLUMNS) { "columns must be 1..$MAX_COLUMNS" }
            columns = count
        }

        fun initiallySelected(itemId: String): Builder = apply {
            initialSelectionId = itemId
        }

        fun dismissOnAction(dismiss: Boolean): Builder = apply {
            dismissOnAction = dismiss
        }

        fun onAction(listener: OnActionListener): Builder = apply {
            actionListener = listener
        }

        fun show(activity: Activity): Dialog {
            val listener = actionListener
                ?: throw IllegalStateException("An action listener is required")
            check(groups.isNotEmpty()) { "At least one action group is required" }
            check(!activity.isFinishing && !activity.isDestroyed) {
                "Cannot show on a closed Activity"
            }
            val snapshot = ArrayList<List<Bbos6ContextMenuItem>>(groups.size)
            val uniqueIds = HashSet<String>()
            for (group in groups) {
                for (item in group) {
                    require(uniqueIds.add(item.id())) {
                        "Duplicate menu action ID: " + item.id()
                    }
                }
                snapshot.add(ArrayList(group))
            }
            if (activity is ComponentActivity) {
                return Bbos6ContextMenuComposeBridge.show(
                    activity, snapshot, columns, initialSelectionId, dismissOnAction, listener
                )
            }
            return MenuDialog(
                activity, snapshot, columns, initialSelectionId, dismissOnAction, listener
            ).apply { show() }
        }
    }

    private class MenuDialog(
        private val activity: Activity,
        private val groups: List<List<Bbos6ContextMenuItem>>,
        private val columns: Int,
        private val initiallySelectedId: String?,
        private val dismissOnAction: Boolean,
        private val actionListener: OnActionListener
    ) : Dialog(activity) {
        private val cells = ArrayList<CellView>()
        private val navigation = ArrayList<Bbos6ContextMenuNavigation.Cell>()
        private var scroll: ScrollView? = null
        private var selection = -1
        private var heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN

        init {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            buildContent()
        }

        private fun buildContent() {
            val viewport = availableViewport(activity)
            val largestGroup = groups.maxOfOrNull { it.size } ?: 1
            val visibleColumns = minOf(columns, largestGroup)
            val panelWidthDp = when (visibleColumns) {
                1 -> ONE_COLUMN_WIDTH_DP
                2 -> TWO_COLUMN_WIDTH_DP
                else -> MULTI_COLUMN_WIDTH_DP
            }
            val panelWidth = minOf(dp(activity, panelWidthDp), viewport.first)
            val rowHeight = dp(activity, ROW_HEIGHT_DP)

            val panel = FrameLayout(activity).apply {
                background = panelBackground(activity)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(
                            0, 0, view.width, view.height,
                            dp(activity, CORNER_RADIUS_DP).toFloat()
                        )
                    }
                }
                isFocusableInTouchMode = true
                elevation = dp(activity, 3).toFloat()
            }
            val scroller = BoundedScrollView(activity, viewport.second).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            scroll = scroller

            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(activity, CONTENT_INSET_DP), dp(activity, CONTENT_INSET_DP),
                    dp(activity, CONTENT_INSET_DP), dp(activity, CONTENT_INSET_DP)
                )
            }
            var globalRow = 0
            for ((groupIndex, group) in groups.withIndex()) {
                var start = 0
                while (start < group.size) {
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    body.addView(row, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
                    ))
                    for (col in 0 until visibleColumns) {
                        val index = start + col
                        if (index < group.size) {
                            addCell(row, group[index], globalRow, col)
                        } else {
                            row.addView(View(activity), LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
                            ))
                        }
                    }
                    globalRow++
                    start += visibleColumns
                }
                if (groupIndex + 1 < groups.size) addSeparator(body)
            }
            scroller.addView(body, ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            panel.addView(scroller, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            selection = Bbos6ContextMenuNavigation.firstEnabled(navigation)
            if (initiallySelectedId != null) {
                for (i in cells.indices) {
                    val item = cells[i].item
                    if (item.id() == initiallySelectedId && item.enabled()) {
                        selection = i
                        break
                    }
                }
            }
            updateSelection()
            Bbos6FocusPolicy.forManagedContainers(panel)
            setContentView(panel)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setGravity(Gravity.CENTER)
            }
            setOnShowListener {
                window?.setLayout(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
                panel.requestFocus()
                revealSelection()
            }
        }

        private fun addCell(
            row: LinearLayout, item: Bbos6ContextMenuItem, gridRow: Int, column: Int
        ) {
            val hitTarget = FrameLayout(activity)
            row.addView(hitTarget, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ))
            val tile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isDuplicateParentStateEnabled = true
            }
            hitTarget.addView(tile, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(
                    dp(activity, TILE_SIDE_INSET_DP), dp(activity, TILE_TOP_INSET_DP),
                    dp(activity, TILE_SIDE_INSET_DP), dp(activity, TILE_TOP_INSET_DP)
                )
            })
            val icon = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val image = resolveIcon(item)
                if (image == null) visibility = View.INVISIBLE else setImageDrawable(image)
            }
            tile.addView(icon, LinearLayout.LayoutParams(
                dp(activity, ICON_SIZE_DP), dp(activity, ICON_SIZE_DP)
            ))
            val label = TextView(activity).apply {
                text = item.label()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SIZE_SP)
                setTextColor(LABEL_COLOR)
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(activity, 2), 0, dp(activity, 2), 0)
            }
            tile.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 3) })

            hitTarget.isEnabled = item.enabled()
            hitTarget.isClickable = item.enabled()
            hitTarget.contentDescription = item.label()
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            hitTarget.alpha = if (item.enabled()) 1f else 0.42f
            val index = cells.size
            cells.add(CellView(item, hitTarget, tile))
            navigation.add(
                Bbos6ContextMenuNavigation.Cell(gridRow, column, item.enabled())
            )
            if (item.enabled()) {
                hitTarget.setOnClickListener {
                    changeSelection(index)
                    invokeSelection()
                }
            }
        }

        private fun resolveIcon(item: Bbos6ContextMenuItem): Drawable? {
            val provided = item.iconDrawable()
            if (provided != null) {
                val state = provided.constantState
                return state?.newDrawable(activity.resources)?.mutate() ?: provided
            }
            return if (item.iconResourceId() == 0) null
            else activity.getDrawable(item.iconResourceId())
        }

        private fun addSeparator(body: LinearLayout) {
            val separator = View(activity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xff72869b.toInt(), 0xff32495d.toInt())
                )
            }
            body.addView(separator, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 2)
            ).apply {
                leftMargin = dp(activity, 9)
                rightMargin = dp(activity, 9)
            })
        }

        private fun changeSelection(newIndex: Int) {
            if (newIndex == selection || newIndex !in cells.indices ||
                !cells[newIndex].item.enabled()) return
            selection = newIndex
            updateSelection()
            revealSelection()
        }

        private fun updateSelection() {
            for ((index, cell) in cells.withIndex()) {
                val highlighted = index == selection && cell.item.enabled()
                val states = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), blueSelection(activity))
                    addState(intArrayOf(),
                        if (highlighted) blueSelection(activity) else ColorDrawable(Color.TRANSPARENT)
                    )
                }
                cell.tile.background = states
                cell.tile.elevation = if (highlighted) dp(activity, 2).toFloat() else 0f
            }
        }

        private fun revealSelection() {
            val scroller = scroll ?: return
            if (selection !in cells.indices) return
            val cell = cells[selection].hitTarget
            scroller.post {
                if (isShowing) {
                    cell.requestRectangleOnScreen(Rect(0, 0, cell.width, cell.height), true)
                }
            }
        }

        private fun invokeSelection() {
            val item = cells.getOrNull(selection)?.item ?: return
            if (!item.enabled()) return
            if (dismissOnAction) dismiss()
            actionListener.onAction(item)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val key = event.keyCode
            val direction = when (key) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Bbos6ContextMenuNavigation.Direction.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> Bbos6ContextMenuNavigation.Direction.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> Bbos6ContextMenuNavigation.Direction.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> Bbos6ContextMenuNavigation.Direction.DOWN
                else -> null
            }
            if (direction != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN
                    changeSelection(Bbos6ContextMenuNavigation.move(
                        navigation, selection, direction
                    ))
                }
                return true
            }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER ||
                key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    heldConfirmKey = key
                } else if (event.action == KeyEvent.ACTION_UP) {
                    val activate = heldConfirmKey == key && !event.isCanceled
                    heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN
                    if (activate) invokeSelection()
                }
                return true
            }
            if (key == KeyEvent.KEYCODE_ESCAPE) {
                heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dismiss()
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private class CellView(
        val item: Bbos6ContextMenuItem, val hitTarget: View, val tile: View
    )

    private fun blueSelection(context: Activity): Drawable =
        Bbos6SelectionBackground.blue(context)

    private fun panelBackground(context: Activity): Drawable {
        val border = maxOf(1, dp(context, 1))
        val radius = dp(context, CORNER_RADIUS_DP)
        val rim = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xff92a0ad.toInt(), 0xff6f8294.toInt(), 0xff3a4d5f.toInt())
        ).apply { cornerRadius = radius.toFloat() }
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(TOP_PANEL_COLOR, 0xff425a75.toInt(), BOTTOM_PANEL_COLOR)
        ).apply { cornerRadius = maxOf(0, radius - border).toFloat() }
        return LayerDrawable(arrayOf(rim, body)).apply {
            setLayerInset(1, border, border, border, border)
        }
    }

    private fun dp(context: Activity, amount: Int): Int =
        Math.round(amount * context.resources.displayMetrics.density)

    private fun availableViewport(activity: Activity): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = activity.windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return maxOf(1, bounds.width() - bars.left - bars.right) to
                maxOf(1, bounds.height() - bars.top - bars.bottom)
        }
        val metrics = activity.resources.displayMetrics
        return maxOf(1, metrics.widthPixels) to maxOf(1, metrics.heightPixels)
    }

    private class BoundedScrollView(
        activity: Activity, private val maxHeightPx: Int
    ) : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = View.MeasureSpec.getMode(heightMeasureSpec)
            val limit = if (mode == View.MeasureSpec.UNSPECIFIED) maxHeightPx
            else minOf(maxHeightPx, View.MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(
                widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                    limit, View.MeasureSpec.AT_MOST
                )
            )
        }
    }
}
