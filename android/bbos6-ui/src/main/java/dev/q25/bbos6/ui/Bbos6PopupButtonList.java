package dev.q25.bbos6.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.InputType;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Standalone BBOS6-inspired modal with a caption and vertical button stack.
 *
 * Based on a supplied BBOS6 reference: dark rounded rim, optional title icon,
 * beveled silver buttons, a blue selected button, rounded edges and a low
 * shadow. Only the selected blue face is shared with Bbos6ContextMenu.
 * The caller supplies all labels, icons, IDs and actions. Nothing in this
 * helper opens an app screen or owns music, storage or other business logic.
 */
public final class Bbos6PopupButtonList {
    private static final int PANEL_COLOR = 0xff212429;
    // Compact, fixed proportions based on the 640x480 Bold 9900 reference.
    // Android dp/sp handle device density; no extra pixel-scale corrections.
    private static final int PANEL_WIDTH_DP = 320;
    private static final int PANEL_CORNER_DP = 7;
    private static final int BUTTON_WIDTH_DP = 176;
    private static final int BUTTON_HEIGHT_DP = 30;
    private static final int BUTTON_GAP_DP = 14;
    private static final int BUTTON_CORNER_DP = 4;
    private static final int ICON_SIZE_DP = 30;
    private static final int INPUT_BUTTON_GAP_DP = 8;

    private Bbos6PopupButtonList() {}

    public interface OnActionListener {
        void onAction(String actionId);
    }

    /** Text is returned to the owning app; this helper never stores it. */
    public interface OnTextActionListener {
        void onAction(String actionId, String text);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final class Action {
        final String id;
        final String label;
        final boolean enabled;

        Action(String id, String label, boolean enabled) {
            this.id = requireText(id, "id");
            this.label = requireText(label, "label");
            this.enabled = enabled;
        }
    }

    private static String requireText(String text, String label) {
        Objects.requireNonNull(text, label);
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return text;
    }

    public static final class Builder {
        private final List<Action> actions = new ArrayList<>();
        private String title;
        private String message;
        private int iconResourceId;
        private Drawable iconDrawable;
        private String initialSelectionId;
        private boolean dismissOnAction = true;
        private OnActionListener listener;
        private OnTextActionListener textListener;
        private boolean hasTextInput;
        private String initialText = "";
        private String inputHint;
        private boolean horizontalButtons;

        /** Optional caption. Without it, only the button stack is shown. */
        public Builder title(String text) {
            title = text == null || text.trim().isEmpty() ? null : text;
            return this;
        }

        /** Optional detail or confirmation warning below the caption. */
        public Builder message(String text) {
            message = text == null || text.trim().isEmpty() ? null : text;
            return this;
        }

        /** Optional single-line native Android text field. */
        public Builder textInput(String initialValue, String hint) {
            hasTextInput = true;
            initialText = initialValue == null ? "" : initialValue;
            inputHint = hint;
            return this;
        }

        /** Use a horizontal row of buttons instead of the existing stack. */
        public Builder horizontalButtons() {
            horizontalButtons = true;
            return this;
        }

        /** Optional caller-owned Android drawable. Zero clears the icon. */
        public Builder titleIconResource(int resourceId) {
            if (resourceId < 0) {
                throw new IllegalArgumentException("resourceId must be >= 0");
            }
            iconResourceId = resourceId;
            iconDrawable = null;
            return this;
        }

        /** Optional caller-supplied drawable. Null clears the icon. */
        public Builder titleIcon(Drawable drawable) {
            iconDrawable = drawable;
            iconResourceId = 0;
            return this;
        }

        /** Adds an enabled button. IDs must be unique within this popup. */
        public Builder addButton(String id, String label) {
            actions.add(new Action(id, label, true));
            return this;
        }

        /** Disabled buttons remain visible but are skipped by navigation. */
        public Builder addDisabledButton(String id, String label) {
            actions.add(new Action(id, label, false));
            return this;
        }

        public Builder initiallySelected(String actionId) {
            initialSelectionId = Objects.requireNonNull(actionId, "actionId");
            return this;
        }

        /** By default an action closes the dialog before invoking its listener. */
        public Builder dismissOnAction(boolean dismiss) {
            dismissOnAction = dismiss;
            return this;
        }

        public Builder onAction(OnActionListener callback) {
            listener = Objects.requireNonNull(callback, "callback");
            return this;
        }

        /** Receives the text at the moment of button activation. */
        public Builder onTextAction(OnTextActionListener callback) {
            textListener = Objects.requireNonNull(callback, "callback");
            return this;
        }

        public Dialog show(Activity activity) {
            Objects.requireNonNull(activity, "activity");
            if (activity.isFinishing() || activity.isDestroyed()) {
                throw new IllegalStateException("Cannot show on a closed Activity");
            }
            if (actions.isEmpty()) {
                throw new IllegalStateException("At least one button is required");
            }
            if (hasTextInput ? textListener == null : listener == null) {
                throw new IllegalStateException("An action listener is required");
            }
            Set<String> ids = new HashSet<>();
            for (Action action : actions) {
                if (!ids.add(action.id)) {
                    throw new IllegalArgumentException("Duplicate button ID: " + action.id);
                }
            }

            PopupDialog dialog = new PopupDialog(activity, title, message,
                    iconResourceId, iconDrawable, new ArrayList<>(actions),
                    initialSelectionId, dismissOnAction, listener, textListener,
                    hasTextInput, initialText, inputHint, horizontalButtons);
            dialog.show();
            return dialog;
        }
    }

    private static final class PopupDialog extends Dialog {
        private final Activity activity;
        private final List<Action> actions;
        private final List<TextView> buttons = new ArrayList<>();
        private final Map<Character, Integer> shortcuts = new HashMap<>();
        private final OnActionListener listener;
        private final OnTextActionListener textListener;
        private final boolean dismissOnAction;
        private final boolean horizontalButtons;
        private EditText input;
        private FrameLayout panel;
        private final String initialSelectionId;
        private ScrollView scroll;
        private int selection = -1;
        private int heldConfirm = KeyEvent.KEYCODE_UNKNOWN;

        PopupDialog(Activity activity, String title, String message,
                int titleIconResource, Drawable titleIcon, List<Action> actions,
                String initialSelectionId, boolean dismissOnAction,
                OnActionListener listener, OnTextActionListener textListener,
                boolean hasTextInput, String initialText, String inputHint,
                boolean horizontalButtons) {
            super(activity);
            this.activity = activity;
            this.actions = actions;
            this.initialSelectionId = initialSelectionId;
            this.dismissOnAction = dismissOnAction;
            this.listener = listener;
            this.textListener = textListener;
            this.horizontalButtons = horizontalButtons;
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setCancelable(true);
            setCanceledOnTouchOutside(false);
            buildContent(title, message, titleIconResource, titleIcon,
                    hasTextInput, initialText, inputHint);
        }

        private void buildContent(String title, String message,
                int iconResource, Drawable iconDrawable,
                boolean hasTextInput, String initialText, String inputHint) {
            int[] viewport = availableViewport(activity);
            // The viewport clamp only prevents overflow on narrower screens.
            int width = Math.min(dp(activity, PANEL_WIDTH_DP), viewport[0]);
            int maxHeight = viewport[1];

            panel = new FrameLayout(activity);
            panel.setBackground(panelBackground(activity));
            panel.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            dp(activity, PANEL_CORNER_DP));
                }
            });
            panel.setClipToOutline(true);
            panel.setElevation(dp(activity, 5));
            panel.setFocusableInTouchMode(true);

            BoundedScrollView scroller = new BoundedScrollView(activity, maxHeight);
            scroller.setVerticalScrollBarEnabled(false);
            scroller.setFillViewport(false);
            scroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            scroll = scroller;

            LinearLayout content = new LinearLayout(activity);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setOrientation(LinearLayout.VERTICAL);
            boolean hasHeader = title != null || iconResource != 0 || iconDrawable != null;
            content.setPadding(dp(activity, 12),
                    dp(activity, hasHeader || message != null ? 20 : 16),
                    dp(activity, 12), dp(activity, 22));

            if (hasHeader) {
                addHeader(content, title, iconResource, iconDrawable);
            }
            if (message != null) {
                addMessage(content, message);
            }
            if (hasTextInput) {
                addTextInput(content, initialText, inputHint);
            }
            int buttonWidth = dp(activity, BUTTON_WIDTH_DP);
            LinearLayout buttonParent = content;
            if (horizontalButtons) {
                buttonParent = new LinearLayout(activity);
                buttonParent.setOrientation(LinearLayout.HORIZONTAL);
                content.addView(buttonParent, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            for (int i = 0; i < actions.size(); i++) {
                addButton(buttonParent, i, buttonWidth);
            }

            scroller.addView(content, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.addView(scroller, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            selection = firstEnabled();
            if (initialSelectionId != null) {
                for (int i = 0; i < actions.size(); i++) {
                    if (actions.get(i).enabled
                            && actions.get(i).id.equals(initialSelectionId)) {
                        selection = i;
                        break;
                    }
                }
            }
            refreshButtons();
            setContentView(panel);

            Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.CENTER);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.36f);
            }
            setOnShowListener(dialog -> {
                Window shown = getWindow();
                if (shown != null) {
                    shown.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
                }
                if (input != null) {
                    input.requestFocus();
                    shown.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                } else {
                    panel.requestFocus();
                }
                revealSelection();
            });
        }

        private void addHeader(LinearLayout content, String title,
                int iconResource, Drawable iconDrawable) {
            LinearLayout header = new LinearLayout(activity);
            header.setGravity(Gravity.CENTER);
            header.setOrientation(LinearLayout.HORIZONTAL);

            if (iconResource != 0 || iconDrawable != null) {
                FrameLayout frame = new FrameLayout(activity);
                GradientDrawable border = new GradientDrawable();
                border.setColor(PANEL_COLOR);
                border.setCornerRadius(dp(activity, 4));
                border.setStroke(Math.max(1, dp(activity, 1)), 0xffb9bdc1);
                frame.setBackground(border);

                ImageView icon = new ImageView(activity);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Drawable picture = iconDrawable;
                if (picture != null && picture.getConstantState() != null) {
                    picture = picture.getConstantState()
                            .newDrawable(activity.getResources()).mutate();
                }
                if (picture == null && iconResource != 0) {
                    picture = activity.getDrawable(iconResource);
                }
                icon.setImageDrawable(picture);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                iconParams.setMargins(dp(activity, 3), dp(activity, 3),
                        dp(activity, 3), dp(activity, 3));
                frame.addView(icon, iconParams);
                LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                        dp(activity, ICON_SIZE_DP), dp(activity, ICON_SIZE_DP));
                if (title != null) frameParams.rightMargin = dp(activity, 10);
                header.addView(frame, frameParams);
            }

            if (title != null) {
                TextView heading = new TextView(activity);
                heading.setText(title);
                heading.setTextColor(Color.WHITE);
                heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                heading.setGravity(Gravity.CENTER_VERTICAL);
                heading.setMaxLines(2);
                heading.setEllipsize(TextUtils.TruncateAt.END);
                heading.setIncludeFontPadding(false);
                heading.setFocusable(false);
                header.addView(heading, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(activity, 20);
            content.addView(header, params);
        }

        /** Wrap long warnings within the bounded scrollable dialog. */
        private void addMessage(LinearLayout content, String text) {
            TextView detail = new TextView(activity);
            detail.setText(text);
            detail.setTextColor(0xfff1f1f1);
            detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            detail.setGravity(Gravity.CENTER);
            detail.setFocusable(false);
            detail.setClickable(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(activity, 5);
            params.rightMargin = dp(activity, 5);
            params.bottomMargin = dp(activity, 14);
            content.addView(detail, params);
        }

        /** Native EditText handles typing, selection, and IME by itself. */
        private void addTextInput(LinearLayout content,
                String initialText, String hint) {
            input = new EditText(activity);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setText(initialText);
            input.setHint(hint);
            input.setSelectAllOnFocus(true);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(activity, 14);
            content.addView(input, params);
        }

        private void addButton(LinearLayout content, int index, int buttonWidth) {
            Action action = actions.get(index);
            TextView button = new TextView(activity);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            button.setGravity(Gravity.CENTER);
            button.setSingleLine(true);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setIncludeFontPadding(false);
            button.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
            button.setFocusable(false);
            button.setEnabled(action.enabled);
            button.setClickable(action.enabled);
            button.setContentDescription(action.label);

            SpannableString styled = new SpannableString(action.label);
            if (action.enabled) {
                int underline = uniqueShortcutIndex(action.label, index);
                if (underline >= 0) {
                    styled.setSpan(new UnderlineSpan(), underline, underline + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                button.setOnTouchListener((view, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        changeSelection(index);
                    }
                    return false;
                });
                button.setOnClickListener(view -> {
                    changeSelection(index);
                    invokeSelection();
                });
            }
            button.setText(styled);
            button.setAlpha(action.enabled ? 1f : 0.45f);
            LinearLayout.LayoutParams params;
            if (horizontalButtons) {
                params = new LinearLayout.LayoutParams(
                        0, dp(activity, BUTTON_HEIGHT_DP), 1f);
                if (index < actions.size() - 1) {
                    params.rightMargin = dp(activity, INPUT_BUTTON_GAP_DP);
                }
            } else {
                params = new LinearLayout.LayoutParams(
                        buttonWidth, dp(activity, BUTTON_HEIGHT_DP));
                params.gravity = Gravity.CENTER_HORIZONTAL;
                if (index < actions.size() - 1) {
                    params.bottomMargin = dp(activity, BUTTON_GAP_DP);
                }
            }
            content.addView(button, params);
            buttons.add(button);
        }

        /** Assign a unique displayed accelerator, skipping duplicate letters. */
        private int uniqueShortcutIndex(String label, int position) {
            for (int i = 0; i < label.length(); i++) {
                char key = Character.toUpperCase(label.charAt(i));
                if (key < 'A' || key > 'Z' || shortcuts.containsKey(key)) continue;
                shortcuts.put(key, position);
                return i;
            }
            return -1;
        }

        private int firstEnabled() {
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).enabled) return i;
            }
            return -1;
        }

        private void changeSelection(int index) {
            if (index < 0 || index >= actions.size()
                    || !actions.get(index).enabled || index == selection) return;
            selection = index;
            refreshButtons();
            revealSelection();
        }

        private void moveSelection(int direction) {
            if (selection < 0) return;
            for (int i = selection + direction; i >= 0 && i < actions.size();
                    i += direction) {
                if (actions.get(i).enabled) {
                    changeSelection(i);
                    return;
                }
            }
        }

        private void refreshButtons() {
            for (int i = 0; i < buttons.size(); i++) {
                TextView button = buttons.get(i);
                boolean selected = i == selection && actions.get(i).enabled;
                button.setBackground(buttonBackground(activity, selected));
                button.setTextColor(selected ? Color.WHITE : 0xff242629);
                // A flat native drawable avoids double shadows and black rims.
            }
        }

        private void revealSelection() {
            if (scroll == null || selection < 0) return;
            TextView selected = buttons.get(selection);
            scroll.post(() -> {
                if (!isShowing()) return;
                Rect rect = new Rect(0, 0, selected.getWidth(), selected.getHeight());
                selected.requestRectangleOnScreen(rect, true);
            });
        }

        private void invokeSelection() {
            if (selection < 0 || selection >= actions.size()) return;
            Action action = actions.get(selection);
            if (!action.enabled) return;
            String value = input == null ? null : input.getText().toString();
            if (dismissOnAction) dismiss();
            if (input != null) {
                textListener.onAction(action.id, value);
            } else {
                listener.onAction(action.id);
            }
        }

        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            int code = event.getKeyCode();
            // In text mode, normal typing and caret keys belong to EditText.
            // Down leaves the field for the popup buttons; Up returns to it.
            if (input != null && input.hasFocus()) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        input.clearFocus();
                        panel.requestFocus();
                        revealSelection();
                    }
                    return true;
                }
                if (code != KeyEvent.KEYCODE_ESCAPE) {
                    return super.dispatchKeyEvent(event);
                }
            } else if (input != null && code == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    input.requestFocus();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    heldConfirm = KeyEvent.KEYCODE_UNKNOWN;
                    moveSelection(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) heldConfirm = code;
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean invoke = heldConfirm == code && !event.isCanceled();
                    heldConfirm = KeyEvent.KEYCODE_UNKNOWN;
                    if (invoke) invokeSelection();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                heldConfirm = KeyEvent.KEYCODE_UNKNOWN;
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    dismiss();
                }
                return true;
            }
            if (!event.isCtrlPressed() && !event.isAltPressed()
                    && !event.isMetaPressed()) {
                int key = event.getUnicodeChar();
                int chosen = shortcuts.getOrDefault(
                        Character.toUpperCase((char) key), -1);
                if (chosen >= 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && event.getRepeatCount() == 0) {
                        heldConfirm = code;
                        changeSelection(chosen);
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        boolean invoke = heldConfirm == code && !event.isCanceled();
                        heldConfirm = KeyEvent.KEYCODE_UNKNOWN;
                        if (invoke) {
                            changeSelection(chosen);
                            invokeSelection();
                        }
                    }
                    return true;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                heldConfirm = KeyEvent.KEYCODE_UNKNOWN;
            }
            return super.dispatchKeyEvent(event);
        }
    }

    /**
     * One native gradient face per button. The BB9900 screenshot shows a
     * slender outer edge and a largely uniform lower face: there is no
     * separate black underlay, inset shadow, or elevated button surface.
     */
    private static Drawable buttonBackground(Context context, boolean selected) {
        if (selected) {
            return Bbos6SelectionBackground.popupButtonBlue(
                    context, BUTTON_CORNER_DP);
        }
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xffd8d8d9, 0xffcccdcf, 0xffafb1b4,
                        0xffafb1b4, 0xffafb1b4});
        face.setCornerRadius(dp(context, BUTTON_CORNER_DP));
        face.setStroke(1, 0xff959aa2);
        return face;
    }

    private static Drawable panelBackground(Context context) {
        GradientDrawable rim = new GradientDrawable();
        rim.setColor(0xff535960);
        rim.setCornerRadius(dp(context, PANEL_CORNER_DP));

        GradientDrawable face = new GradientDrawable();
        face.setColor(PANEL_COLOR);
        face.setCornerRadius(dp(context, PANEL_CORNER_DP - 1));
        LayerDrawable background = new LayerDrawable(new Drawable[]{rim, face});
        background.setLayerInset(1, dp(context, 1), dp(context, 1),
                dp(context, 1), dp(context, 1));
        return background;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int[] availableViewport(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{
                    Math.max(1, bounds.width() - insets.left - insets.right),
                    Math.max(1, bounds.height() - insets.top - insets.bottom)
            };
        }
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return new int[]{Math.max(1, metrics.widthPixels),
                Math.max(1, metrics.heightPixels)};
    }

    private static final class BoundedScrollView extends ScrollView {
        private final int maxHeightPx;

        BoundedScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = maxHeightPx;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int requested = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? maxHeightPx : Math.min(maxHeightPx, MeasureSpec.getSize(heightMeasureSpec));
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    requested, MeasureSpec.AT_MOST));
        }
    }
}
