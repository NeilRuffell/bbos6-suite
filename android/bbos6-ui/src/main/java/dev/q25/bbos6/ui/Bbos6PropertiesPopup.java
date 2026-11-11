package dev.q25.bbos6.ui;

import android.app.Activity;
import android.app.Dialog;

import androidx.activity.ComponentActivity;
import dev.q25.bbos6.ui.compose.Bbos6PropertiesComposeBridge;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reusable BlackBerry OS 6-inspired Properties/Info overlay.
 *
 * Rendered entirely with Android's native Dialog, Views and drawables.
 * The border, panel and typography are matched to a reference screenshot,
 * not to undocumented, proprietary BlackBerry OS theme constants.
 *
 * The caller supplies content through {@link Bbos6PropertiesItem}; this
 * widget never reads files, music tags, storage, or application state.
 */
public final class Bbos6PropertiesPopup {
    public static final int SURFACE_COLOR = 0xff212429;
    public static final int TEXT_COLOR = Color.WHITE;

    private static final float MAX_VIEWPORT_FRACTION = 0.90f;
    private static final int CORNER_RADIUS_DP = 10;
    private static final int HORIZONTAL_PADDING_DP = 12;
    private static final int VERTICAL_PADDING_DP = 10;
    private static final int MIN_ROW_HEIGHT_DP = 18;
    private static final float FONT_SIZE_SP = 15f;

    private Bbos6PropertiesPopup() {}

    public static Builder builder() {
        return new Builder();
    }

    /** A builder may be reused; each call to show snapshots its contents. */
    public static final class Builder {
        private final ArrayList<Bbos6PropertiesItem> items = new ArrayList<>();

        public Builder add(Bbos6PropertiesItem item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public Builder addText(String text) {
            return add(Bbos6PropertiesItem.text(text));
        }

        public Builder addInline(String label, String value) {
            return add(Bbos6PropertiesItem.inline(label, value));
        }

        public Builder addTrailing(String label, String value) {
            return add(Bbos6PropertiesItem.trailing(label, value));
        }

        public Builder addSpacer(int dp) {
            return add(Bbos6PropertiesItem.spacer(dp));
        }

        public List<Bbos6PropertiesItem> items() {
            return Collections.unmodifiableList(new ArrayList<>(items));
        }

        public Dialog show(Activity activity) {
            return Bbos6PropertiesPopup.show(activity, items());
        }
    }

    /**
     * Shows a centered, titleless, persistent properties panel.
     *
     * Back closes this dialog; outside touches do not dismiss it. The list
     * behind the dialog is left mounted, preserving selection/scroll state.
     * Overflow is handled inside the popup, not by growing past the viewport.
     */
    public static Dialog show(Activity activity, List<Bbos6PropertiesItem> items) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(items, "items");
        if (activity.isFinishing() || activity.isDestroyed()) {
            throw new IllegalStateException("Cannot show a popup on a closed Activity");
        }
        final List<Bbos6PropertiesItem> snapshot = new ArrayList<>(items);
        for (Bbos6PropertiesItem item : snapshot) {
            Objects.requireNonNull(item, "item");
        }

        // Shared Compose panel for AndroidX lifecycle owners; preserve the
        // native implementation for platform-only Activity integrations.
        if (activity instanceof ComponentActivity) {
            return Bbos6PropertiesComposeBridge.show(
                    (ComponentActivity) activity, snapshot);
        }

        int[] viewport = availableViewport(activity);
        int popupWidth = Math.max(1, Math.round(viewport[0] * MAX_VIEWPORT_FRACTION));
        int popupMaxHeight = Math.max(1, Math.round(viewport[1] * MAX_VIEWPORT_FRACTION));

        FrameLayout panel = new FrameLayout(activity);
        panel.setBackground(panelBackground(activity));
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        dp(activity, CORNER_RADIUS_DP));
            }
        });
        panel.setClipToOutline(true);
        panel.setElevation(dp(activity, 3));

        BoundedScrollView scroll = new BoundedScrollView(activity, popupMaxHeight);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, HORIZONTAL_PADDING_DP),
                dp(activity, VERTICAL_PADDING_DP),
                dp(activity, HORIZONTAL_PADDING_DP),
                dp(activity, VERTICAL_PADDING_DP));

        for (Bbos6PropertiesItem item : snapshot) {
            addItem(activity, content, item);
        }
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The read-only scroll container is a composite, not a focus target
        // that should receive Android's default full-panel highlight.
        Bbos6FocusPolicy.forManagedContainers(panel);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // The reference shows the underlying selected row without an
            // additional app-level black scrim.
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(popupWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static void addItem(Activity activity, LinearLayout parent,
            Bbos6PropertiesItem item) {
        if (item.kind() == Bbos6PropertiesItem.Kind.SPACER) {
            if (item.spaceDp() > 0) {
                parent.addView(new View(activity), new LinearLayout.LayoutParams(
                        1, dp(activity, item.spaceDp())));
            }
            return;
        }

        if (item.kind() == Bbos6PropertiesItem.Kind.TRAILING_PROPERTY) {
            LinearLayout pair = new LinearLayout(activity);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            pair.setGravity(Gravity.CENTER_VERTICAL);
            pair.setMinimumHeight(dp(activity, MIN_ROW_HEIGHT_DP));

            TextView label = textView(activity, item.label());
            pair.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView value = textView(activity, item.value());
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            right.leftMargin = dp(activity, 6);
            pair.addView(value, right);
            parent.addView(pair, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        String text = item.kind() == Bbos6PropertiesItem.Kind.INLINE_PROPERTY
                ? item.label() + (item.value().isEmpty() ? "" : " " + item.value())
                : item.label();
        TextView line = textView(activity, text);
        parent.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static TextView textView(Activity activity, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(TEXT_COLOR);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_SP);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(activity, MIN_ROW_HEIGHT_DP));
        return view;
    }

    private static LayerDrawable panelBackground(Activity activity) {
        int outerRadius = dp(activity, CORNER_RADIUS_DP);
        int border = Math.max(1, dp(activity, 1));

        // The thin rim brightens toward the top, as in the supplied image.
        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff73797d, 0xff4b5055});
        rim.setCornerRadius(outerRadius);

        GradientDrawable face = new GradientDrawable();
        face.setColor(SURFACE_COLOR);
        face.setCornerRadius(Math.max(0, outerRadius - border));

        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                rim, face});
        layers.setLayerInset(1, border, border, border, border);
        return layers;
    }

    private static int dp(Activity context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int[] availableViewport(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{
                    Math.max(1, bounds.width() - bars.left - bars.right),
                    Math.max(1, bounds.height() - bars.top - bars.bottom)
            };
        }
        DisplayMetrics display = activity.getResources().getDisplayMetrics();
        return new int[]{Math.max(1, display.widthPixels),
                Math.max(1, display.heightPixels)};
    }

    private static final class BoundedScrollView extends ScrollView {
        private final int maximumHeightPx;

        BoundedScrollView(Activity activity, int maximumHeightPx) {
            super(activity);
            this.maximumHeightPx = maximumHeightPx;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int allowed = mode == MeasureSpec.UNSPECIFIED
                    ? maximumHeightPx
                    : Math.min(maximumHeightPx, MeasureSpec.getSize(heightMeasureSpec));
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(allowed, MeasureSpec.AT_MOST));
        }
    }
}
