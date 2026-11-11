package dev.q25.bbos6.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import androidx.activity.ComponentActivity
import dev.q25.bbos6.ui.compose.Bbos6PopupAction
import dev.q25.bbos6.ui.compose.Bbos6PopupButtonComposeBridge

/**
 * Reusable BBOS6 modal buttons and optional text-entry popup, fully authored
 * in Kotlin. AndroidX button-only dialogs use shared Compose; a native
 * Android EditText remains the Q25-verified keyboard/DPAD focus owner for
 * input dialogs. No app-specific actions or persistence live here.
 */
object Bbos6PopupButtonList {
    private const val PANEL_COLOR: Int = -14605271 // #FF212429
    private const val PANEL_WIDTH_DP = 320
    private const val PANEL_CORNER_DP = 7
    private const val BUTTON_WIDTH_DP = 176
    private const val BUTTON_GAP_DP = 14
    private const val ICON_SIZE_DP = 30
    private const val INPUT_BUTTON_GAP_DP = 8

    fun interface OnActionListener {
        fun onAction(actionId: String)
    }
    fun interface OnTextActionListener {
        fun onAction(actionId: String, text: String)
    }

    @JvmStatic fun builder() = Builder()

    private class Action(val id: String, val label: String, val enabled: Boolean) {
        init {
            require(id.isNotBlank()) { "id must not be blank" }
            require(label.isNotBlank()) { "label must not be blank" }
        }
    }

    class Builder {
        private val actions = ArrayList<Action>()
        private var title: String? = null
        private var message: String? = null
        private var iconResourceId = 0
        private var iconDrawable: Drawable? = null
        private var initialSelectionId: String? = null
        private var dismissOnAction = true
        private var listener: OnActionListener? = null
        private var textListener: OnTextActionListener? = null
        private var hasTextInput = false
        private var initialText = ""
        private var inputHint: String? = null
        private var horizontalButtons = false

        fun title(text: String?): Builder = apply { title = text?.takeUnless { it.isBlank() } }
        fun message(text: String?): Builder = apply {
            message = text?.takeUnless { it.isBlank() }
        }
        fun textInput(initialValue: String?, hint: String?): Builder = apply {
            hasTextInput = true
            initialText = initialValue ?: ""
            inputHint = hint
        }
        fun horizontalButtons(): Builder = apply { horizontalButtons = true }
        fun titleIconResource(resourceId: Int): Builder = apply {
            require(resourceId >= 0) { "resourceId must be >= 0" }
            iconResourceId = resourceId
            iconDrawable = null
        }
        fun titleIcon(drawable: Drawable?): Builder = apply {
            iconDrawable = drawable
            iconResourceId = 0
        }
        fun addButton(id: String, label: String): Builder = apply {
            actions.add(Action(id, label, true))
        }
        fun addDisabledButton(id: String, label: String): Builder = apply {
            actions.add(Action(id, label, false))
        }
        fun initiallySelected(actionId: String): Builder = apply {
            initialSelectionId = actionId
        }
        fun dismissOnAction(dismiss: Boolean): Builder = apply {
            dismissOnAction = dismiss
        }
        fun onAction(callback: OnActionListener): Builder = apply {
            listener = callback
        }
        fun onTextAction(callback: OnTextActionListener): Builder = apply {
            textListener = callback
        }

        fun show(activity: Activity): Dialog {
            check(!activity.isFinishing && !activity.isDestroyed) {
                "Cannot show on a closed Activity"
            }
            check(actions.isNotEmpty()) { "At least one button is required" }
            check(if (hasTextInput) textListener != null else listener != null) {
                "An action listener is required"
            }
            val ids = HashSet<String>()
            for (action in actions) {
                require(ids.add(action.id)) { "Duplicate button ID: " + action.id }
            }
            // Preserve tested ComponentActivity Compose button-only route.
            if (!hasTextInput && activity is ComponentActivity) {
                val shared = actions.map {
                    Bbos6PopupAction(it.id, it.label, it.enabled)
                }
                return Bbos6PopupButtonComposeBridge.show(
                    activity, shared, title, message, iconResourceId, iconDrawable,
                    initialSelectionId, horizontalButtons, dismissOnAction, listener!!
                )
            }
            return PopupDialog(
                activity, title, message, iconResourceId, iconDrawable,
                ArrayList(actions), initialSelectionId, dismissOnAction,
                listener, textListener, hasTextInput, initialText,
                inputHint, horizontalButtons
            ).apply { show() }
        }
    }

    /**
     * Native-Q25 compatibility path, implemented entirely in Kotlin. The
     * field's focus, keyboard editing and caret navigation belong to
     * Android EditText, not a synthetic Compose-to-native focus bridge.
     */
    private class PopupDialog(
        private val activity: Activity,
        title: String?,
        message: String?,
        titleIconResource: Int,
        titleIcon: Drawable?,
        private val actions: List<Action>,
        private val initialSelectionId: String?,
        private val dismissOnAction: Boolean,
        private val listener: OnActionListener?,
        private val textListener: OnTextActionListener?,
        hasTextInput: Boolean,
        initialText: String,
        inputHint: String?,
        private val horizontalButtons: Boolean
    ) : Dialog(activity) {
        private val buttons = ArrayList<Bbos6PopupButtonView>()
        private val shortcuts = HashMap<Char, Int>()
        private var input: Bbos6TextField? = null
        private lateinit var panel: FrameLayout
        private var scroll: ScrollView? = null
        private var selection = -1
        private var heldConfirm = KeyEvent.KEYCODE_UNKNOWN

        init {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            buildContent(
                title, message, titleIconResource, titleIcon, hasTextInput,
                initialText, inputHint
            )
        }

        private fun buildContent(
            title: String?, message: String?, iconResource: Int,
            iconDrawable: Drawable?, hasTextInput: Boolean,
            initialText: String, inputHint: String?
        ) {
            val viewport = availableViewport(activity)
            val width = minOf(dp(activity, PANEL_WIDTH_DP), viewport.first)

            panel = FrameLayout(activity).apply {
                background = panelBackground(activity)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(
                            0, 0, view.width, view.height,
                            dp(activity, PANEL_CORNER_DP).toFloat()
                        )
                    }
                }
                clipToOutline = true
                elevation = dp(activity, 5).toFloat()
                isFocusableInTouchMode = true
                onFocusChangeListener = View.OnFocusChangeListener { _, _ ->
                    refreshButtons()
                }
            }
            val scroller = BoundedScrollView(activity, viewport.second).apply {
                isVerticalScrollBarEnabled = false
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            scroll = scroller
            val hasHeader = title != null || iconResource != 0 || iconDrawable != null
            val content = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(activity, 12), dp(activity, if (hasHeader || message != null) 20 else 16),
                    dp(activity, 12), dp(activity, 22)
                )
            }
            if (hasHeader) addHeader(content, title, iconResource, iconDrawable)
            if (message != null) addMessage(content, message)
            if (hasTextInput) addTextInput(content, initialText, inputHint)

            val buttonWidth = dp(activity, BUTTON_WIDTH_DP)
            var buttonParent = content
            if (horizontalButtons) {
                val horizontal = HorizontalScrollView(activity).apply {
                    isHorizontalScrollBarEnabled = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isFillViewport = true
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                }
                buttonParent = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                horizontal.addView(
                    buttonParent, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                content.addView(horizontal, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            for (index in actions.indices) addButton(buttonParent, index, buttonWidth)

            scroller.addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            panel.addView(scroller, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            selection = firstEnabled()
            val selected = actions.indexOfFirst {
                it.enabled && it.id == initialSelectionId
            }
            if (selected >= 0) selection = selected
            refreshButtons()

            Bbos6FocusPolicy.forManagedContainers(panel)
            setContentView(panel)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.CENTER)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.36f)
            }
            setOnShowListener {
                window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                val field = input
                if (field != null) {
                    field.requestFocus()
                    window?.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    )
                } else {
                    panel.requestFocus()
                }
                revealSelection()
            }
        }

        private fun addHeader(
            content: LinearLayout, title: String?,
            iconResource: Int, iconDrawable: Drawable?
        ) {
            val header = LinearLayout(activity).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
            }
            if (iconResource != 0 || iconDrawable != null) {
                val frame = FrameLayout(activity).apply {
                    background = GradientDrawable().apply {
                        setColor(PANEL_COLOR)
                        cornerRadius = dp(activity, 4).toFloat()
                        setStroke(maxOf(1, dp(activity, 1)), 0xffb9bdc1.toInt())
                    }
                }
                val image = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    var picture = iconDrawable
                    if (picture?.constantState != null) {
                        picture = picture.constantState!!.newDrawable(activity.resources).mutate()
                    }
                    if (picture == null && iconResource != 0) {
                        picture = activity.getDrawable(iconResource)
                    }
                    setImageDrawable(picture)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                frame.addView(image, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(
                        dp(activity, 3), dp(activity, 3), dp(activity, 3), dp(activity, 3)
                    )
                })
                header.addView(frame, LinearLayout.LayoutParams(
                    dp(activity, ICON_SIZE_DP), dp(activity, ICON_SIZE_DP)
                ).apply { if (title != null) rightMargin = dp(activity, 10) })
            }
            if (title != null) {
                header.addView(TextView(activity).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                    isFocusable = false
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            content.addView(header, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(activity, 20) })
        }

        private fun addMessage(content: LinearLayout, text: String) {
            content.addView(TextView(activity).apply {
                this.text = text
                setTextColor(0xfff1f1f1.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                isFocusable = false
                isClickable = false
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(activity, 5)
                rightMargin = dp(activity, 5)
                bottomMargin = dp(activity, 14)
            })
        }

        private fun addTextInput(
            content: LinearLayout, initialText: String, hint: String?
        ) {
            val field = Bbos6TextField(activity).apply {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setText(initialText)
                setHint(hint)
                setSelectAllOnFocus(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = 0
            }
            input = field
            content.addView(field, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, Bbos6PopupButtonView.HEIGHT_DP)
            ).apply { bottomMargin = dp(activity, 14) })
        }

        private fun addButton(content: LinearLayout, index: Int, buttonWidth: Int) {
            val action = actions[index]
            val button = Bbos6PopupButtonView(activity).apply {
                setContentSized(horizontalButtons)
                isEnabled = action.enabled
                isClickable = action.enabled
                contentDescription = action.label
            }
            val styled = SpannableString(action.label)
            if (action.enabled) {
                val underline = uniqueShortcutIndex(action.label, index)
                if (underline >= 0) {
                    styled.setSpan(
                        UnderlineSpan(), underline, underline + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                button.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) changeSelection(index)
                    false
                }
                button.setOnClickListener {
                    changeSelection(index)
                    invokeSelection()
                }
            }
            button.text = styled
            button.alpha = if (action.enabled) 1f else 0.45f
            val params = if (horizontalButtons) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(activity, Bbos6PopupButtonView.HEIGHT_DP)
                ).apply {
                    if (index < actions.size - 1) {
                        rightMargin = dp(activity, INPUT_BUTTON_GAP_DP)
                    }
                }
            } else {
                LinearLayout.LayoutParams(
                    buttonWidth, dp(activity, Bbos6PopupButtonView.HEIGHT_DP)
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    if (index < actions.size - 1) bottomMargin = dp(activity, BUTTON_GAP_DP)
                }
            }
            content.addView(button, params)
            buttons.add(button)
        }

        private fun uniqueShortcutIndex(label: String, position: Int): Int {
            for (i in label.indices) {
                val key = label[i].uppercaseChar()
                if (key !in 'A'..'Z' || shortcuts.containsKey(key)) continue
                shortcuts[key] = position
                return i
            }
            return -1
        }

        private fun firstEnabled(): Int = actions.indexOfFirst { it.enabled }

        private fun changeSelection(index: Int) {
            if (index !in actions.indices || !actions[index].enabled ||
                index == selection) return
            selection = index
            refreshButtons()
            revealSelection()
        }

        private fun moveSelection(direction: Int) {
            if (selection < 0) return
            var i = selection + direction
            while (i in actions.indices) {
                if (actions[i].enabled) {
                    changeSelection(i)
                    return
                }
                i += direction
            }
        }

        private fun refreshButtons() {
            for ((index, button) in buttons.withIndex()) {
                button.setBbos6Highlighted(
                    index == selection && actions[index].enabled && panel.hasFocus()
                )
            }
        }

        private fun revealSelection() {
            val scroller = scroll ?: return
            val selected = buttons.getOrNull(selection) ?: return
            scroller.post {
                if (isShowing) {
                    selected.requestRectangleOnScreen(
                        Rect(0, 0, selected.width, selected.height), true
                    )
                }
            }
        }

        private fun invokeSelection() {
            val action = actions.getOrNull(selection) ?: return
            if (!action.enabled) return
            val value = input?.text?.toString()
            if (dismissOnAction) dismiss()
            if (input != null) {
                textListener?.onAction(action.id, value ?: "")
            } else {
                listener?.onAction(action.id)
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val code = event.keyCode
            val field = input
            if (field != null && field.hasFocus()) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        field.clearFocus()
                        panel.requestFocus()
                        revealSelection()
                    }
                    return true
                }
                if (code != KeyEvent.KEYCODE_ESCAPE) return super.dispatchKeyEvent(event)
            } else if (field != null && code == KeyEvent.KEYCODE_DPAD_UP &&
                (horizontalButtons || selection == firstEnabled())) {
                if (event.action == KeyEvent.ACTION_DOWN) field.requestFocus()
                return true
            }
            if (horizontalButtons &&
                (code == KeyEvent.KEYCODE_DPAD_LEFT ||
                    code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    heldConfirm = KeyEvent.KEYCODE_UNKNOWN
                    moveSelection(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                }
                return true
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    heldConfirm = KeyEvent.KEYCODE_UNKNOWN
                    moveSelection(if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                }
                return true
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER ||
                code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    heldConfirm = code
                }
                if (event.action == KeyEvent.ACTION_UP) {
                    val invoke = heldConfirm == code && !event.isCanceled
                    heldConfirm = KeyEvent.KEYCODE_UNKNOWN
                    if (invoke) invokeSelection()
                }
                return true
            }
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                heldConfirm = KeyEvent.KEYCODE_UNKNOWN
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dismiss()
                return true
            }
            if (!event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                val key = event.unicodeChar
                val chosen = shortcuts[key.toChar().uppercaseChar()] ?: -1
                if (chosen >= 0) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        heldConfirm = code
                        changeSelection(chosen)
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        val invoke = heldConfirm == code && !event.isCanceled
                        heldConfirm = KeyEvent.KEYCODE_UNKNOWN
                        if (invoke) {
                            changeSelection(chosen)
                            invokeSelection()
                        }
                    }
                    return true
                }
            }
            if (event.action == KeyEvent.ACTION_DOWN) heldConfirm = KeyEvent.KEYCODE_UNKNOWN
            return super.dispatchKeyEvent(event)
        }
    }

    private fun panelBackground(context: Context): Drawable {
        val rim = GradientDrawable().apply {
            setColor(0xff535960.toInt())
            cornerRadius = dp(context, PANEL_CORNER_DP).toFloat()
        }
        val face = GradientDrawable().apply {
            setColor(PANEL_COLOR)
            cornerRadius = dp(context, PANEL_CORNER_DP - 1).toFloat()
        }
        return LayerDrawable(arrayOf(rim, face)).apply {
            setLayerInset(
                1, dp(context, 1), dp(context, 1),
                dp(context, 1), dp(context, 1)
            )
        }
    }

    private fun dp(context: Context, value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)

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
        context: Context, private val maxHeightPx: Int
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val requested = if (View.MeasureSpec.getMode(heightMeasureSpec) ==
                View.MeasureSpec.UNSPECIFIED) maxHeightPx
            else minOf(maxHeightPx, View.MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(
                widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                    requested, View.MeasureSpec.AT_MOST
                )
            )
        }
    }
}
