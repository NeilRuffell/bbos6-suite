package dev.q25.bbos6.ui.compose

import android.view.KeyEvent as AndroidKeyEvent
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import androidx.compose.ui.viewinterop.AndroidView
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shared Compose popup with caller-supplied actions and optional editable
 * text. Equivalent to Bbos6PopupButtonList's content/DPAD focus contract;
 * callbacks, dialogs opened and any writes belong exclusively to callers.
 *
 * Text is null when the popup does not have an input; empty is valid when
 * an input exists. The caller performs all validation.
 */
@Composable
fun Bbos6PopupButtonDialog(
    actions: List<Bbos6PopupAction>,
    onDismissRequest: () -> Unit,
    onAction: (String, String?) -> Unit,
    title: String? = null,
    message: String? = null,
    titleIcon: Painter? = null,
    modifier: Modifier = Modifier,
    initialSelectionId: String? = null,
    horizontalButtons: Boolean = false,
    textInput: Boolean = false,
    initialText: String = "",
    inputHint: String? = null,
    dismissOnAction: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val hostView = LocalView.current
        SideEffect {
            bbos6DialogWindow(hostView)?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.36f)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            }
        }
        Bbos6PopupButtonPanel(
            actions = actions,
            onAction = { id, value ->
                if (dismissOnAction) onDismissRequest()
                onAction(id, value)
            },
            onEscape = onDismissRequest,
            title = title,
            message = message,
            titleIcon = titleIcon,
            modifier = modifier,
            initialSelectionId = initialSelectionId,
            horizontalButtons = horizontalButtons,
            textInput = textInput,
            initialText = initialText,
            inputHint = inputHint
        )
    }
}

/** Reusable popup content for a dialog or host panel. */
@Composable
fun Bbos6PopupButtonPanel(
    actions: List<Bbos6PopupAction>,
    onAction: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    onEscape: () -> Unit = {},
    title: String? = null,
    message: String? = null,
    titleIcon: Painter? = null,
    initialSelectionId: String? = null,
    horizontalButtons: Boolean = false,
    textInput: Boolean = false,
    initialText: String = "",
    inputHint: String? = null,
    nativeTextInput: Boolean = false
) {
    require(actions.isNotEmpty()) { "At least one button is required" }
    require(actions.all { it.id.isNotBlank() && it.label.isNotBlank() }) {
        "Button ID and label must not be blank"
    }
    require(actions.map { it.id }.toSet().size == actions.size) {
        "Duplicate button ID"
    }
    val initial = remember(actions, initialSelectionId) {
        Bbos6PopupNavigation.initial(actions, initialSelectionId)
    }
    var selected by remember(actions, initialSelectionId) { mutableIntStateOf(initial) }
    var heldConfirm by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }
    var fieldFocused by remember { mutableStateOf(false) }
    var buttonFocused by remember { mutableStateOf(false) }
    var text by remember(initialText) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    // Java consumers retain the actual Android EditText, not a second editor.
    var nativeField by remember { mutableStateOf<dev.q25.bbos6.ui.Bbos6TextField?>(null) }
    fun currentValue(): String? = when {
        !textInput -> null
        nativeTextInput -> nativeField?.text?.toString() ?: initialText
        else -> text.text
    }
    val shortcuts = remember(actions) { Bbos6PopupNavigation.shortcuts(actions) }
    val shortcutOffsets = remember(shortcuts) {
        shortcuts.values.associate { (index, offset) -> index to offset }
    }
    val buttonsFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }
    val configuration = LocalConfiguration.current

    LaunchedEffect(textInput, nativeTextInput, nativeField) {
        if (textInput) {
            if (nativeTextInput) nativeField?.requestFocus()
            else inputFocus.requestFocus()
        } else buttonsFocus.requestFocus()
    }

    fun activate() {
        actions.getOrNull(selected)?.takeIf { it.enabled }?.let { action ->
            onAction(action.id, currentValue())
        }
    }

    Box(
        modifier = modifier
            .width(minOf(320.dp, configuration.screenWidthDp.dp))
            .heightIn(max = configuration.screenHeightDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF535960))
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF212429))
            .focusRequester(buttonsFocus)
            .onFocusChanged { buttonFocused = it.isFocused }
            .onPreviewKeyEvent { key ->
                val event = key.nativeKeyEvent
                val code = event.keyCode
                val down = event.action == AndroidKeyEvent.ACTION_DOWN
                if (textInput && fieldFocused) {
                    if (code == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                        if (down) {
                            if (nativeTextInput) nativeField?.clearFocus()
                            buttonsFocus.requestFocus()
                        }
                        true
                    } else if (code == AndroidKeyEvent.KEYCODE_ESCAPE) {
                        if (!down && !event.isCanceled) onEscape()
                        true
                    } else false
                } else if (textInput && code == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                    (horizontalButtons || selected == Bbos6PopupNavigation.initial(actions, null))) {
                    if (down) {
                        if (nativeTextInput) nativeField?.requestFocus()
                        else inputFocus.requestFocus()
                    }
                    true
                } else if (
                    if (horizontalButtons)
                        code == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                            code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                    else
                        code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                            code == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (down) {
                        heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        selected = Bbos6PopupNavigation.move(actions, selected,
                            if (code == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                                code == AndroidKeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    true
                } else if (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    code == AndroidKeyEvent.KEYCODE_ENTER ||
                    code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (down && event.repeatCount == 0) heldConfirm = code
                    if (!down) {
                        val invoke = heldConfirm == code && !event.isCanceled
                        heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        if (invoke) activate()
                    }
                    true
                } else if (code == AndroidKeyEvent.KEYCODE_ESCAPE) {
                    heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                    if (!down && !event.isCanceled) onEscape()
                    true
                } else {
                    val accelerator = if (!event.isCtrlPressed && !event.isAltPressed &&
                        !event.isMetaPressed && event.unicodeChar != 0)
                        shortcuts[event.unicodeChar.toChar().uppercaseChar()]?.first
                    else null
                    if (accelerator != null) {
                        if (down && event.repeatCount == 0) {
                            heldConfirm = code
                            selected = accelerator
                        } else if (!down) {
                            val invoke = heldConfirm == code && !event.isCanceled
                            heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                            if (invoke) {
                                selected = accelerator
                                onAction(actions[accelerator].id, currentValue())
                            }
                        }
                        true
                    } else {
                        if (down) heldConfirm = AndroidKeyEvent.KEYCODE_UNKNOWN
                        false
                    }
                }
            }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 12.dp, end = 12.dp,
                    top = if (title != null || titleIcon != null || message != null) 20.dp else 16.dp,
                    bottom = 22.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title != null || titleIcon != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (titleIcon != null) {
                        Box(
                            modifier = Modifier.size(30.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFB9BDC1))
                                .padding(1.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF212429))
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(painter = titleIcon, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        if (title != null) Spacer(Modifier.width(10.dp))
                    }
                    if (title != null) {
                        BasicText(
                            text = title,
                            style = TextStyle(color = Color.White, fontSize = 18.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (message != null) {
                BasicText(
                    text = message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)
                        .padding(bottom = 14.dp),
                    style = TextStyle(color = Color(0xFFF1F1F1), fontSize = 15.sp,
                        textAlign = TextAlign.Center)
                )
            }
            if (textInput) {
                if (nativeTextInput) {
                    // Original verified EditText, now inside Compose chrome.
                    AndroidView(
                        factory = { context ->
                            dev.q25.bbos6.ui.Bbos6TextField(context).apply {
                                setSingleLine(true)
                                inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                setText(initialText)
                                hint = inputHint
                                setSelectAllOnFocus(true)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                setMinHeight(0)
                                minimumHeight = 0
                                setOnFocusChangeListener { _, focused ->
                                    fieldFocused = focused
                                }
                                setOnKeyListener { _, keyCode, event ->
                                    if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                                        if (event.action == AndroidKeyEvent.ACTION_DOWN) {
                                            clearFocus()
                                            buttonsFocus.requestFocus()
                                        }
                                        true
                                    } else false
                                }
                                nativeField = this
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(30.dp)
                    )
                } else {
                    Bbos6TextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                        minimumHeight = 30.dp, hint = inputHint,
                        focusRequester = inputFocus,
                        onInputFocusChanged = { fieldFocused = it },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            if (horizontalButtons) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .widthIn(min = maxWidth),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions.forEachIndexed { index, action ->
                        if (index > 0) Spacer(Modifier.width(8.dp))
                        PopupActionFace(action, index, selected == index && buttonFocused,
                            contentSized = true, shortcutIndex = shortcutOffsets[index], onSelect = {
                                selected = index
                                buttonsFocus.requestFocus()
                            }, onInvoke = {
                                onAction(action.id, currentValue())
                            })
                    }
                }
                }
            } else {
                actions.forEachIndexed { index, action ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    PopupActionFace(action, index, selected == index && buttonFocused,
                        contentSized = false, shortcutIndex = shortcutOffsets[index], onSelect = {
                            selected = index
                            buttonsFocus.requestFocus()
                        }, onInvoke = {
                            onAction(action.id, currentValue())
                        })
                }
            }
        }
    }
}

@Composable
private fun PopupActionFace(
    action: Bbos6PopupAction,
    index: Int,
    selected: Boolean,
    contentSized: Boolean,
    shortcutIndex: Int?,
    onSelect: () -> Unit,
    onInvoke: () -> Unit
) {
    Bbos6PopupButton(
        label = action.label,
        highlighted = selected && action.enabled,
        contentSized = contentSized,
        shortcutIndex = shortcutIndex,
        modifier = (if (contentSized) Modifier else Modifier.width(176.dp))
            .pointerInput(index, action.enabled) {
                if (action.enabled) detectTapGestures(
                    onPress = { onSelect(); tryAwaitRelease() },
                    onTap = { onSelect(); onInvoke() }
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = action.label
                if (!action.enabled) disabled() else onClick {
                    onSelect(); onInvoke(); true
                }
            }
    )
}
