package dev.q25.bbos6.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reusable BlackBerry OS 6-inspired grid context/action menu.
 *
 * This is a standalone native Android presentation component, not a Music
 * command handler. Callers provide icons, labels, action IDs and callbacks.
 * Group dividers, rounded slate panel, vivid blue selected tile, touch input
 * and keyboard/trackpad focus all belong to the helper.
 *
 * Colors and proportions are measured from a supplied BBOS6 reference image;
 * they are not claimed to be proprietary SDK theme constants.
 */
public final class Bbos6ContextMenu {
    public static final int TOP_PANEL_COLOR = 0xff445d79;
    public static final int BOTTOM_PANEL_COLOR = 0xff3a4d61;
    public static final int LABEL_COLOR = Color.WHITE;

    private static final int DEFAULT_COLUMNS = 3;
    private static final int MAX_COLUMNS = 6;
    private static final float WIDTH_FRACTION = 0.98f;
    private static final float HEIGHT_FRACTION = 0.94f;
    private static final int MAX_WIDTH_DP = 560;
    private static final int CORNER_RADIUS_DP = 8;
    private static final int CONTENT_INSET_DP = 7;
    private static final int TILE_SIDE_INSET_DP = 3;
    private static final int TILE_TOP_INSET_DP = 6;
    private static final float LABEL_SIZE_SP = 15f;

    public interface OnActionListener {
        void onAction(Bbos6ContextMenuItem item);
    }

    private Bbos6ContextMenu() {}

    public static Builder builder() {
        return new Builder();
    }

    /** Describe action groups without committing to any app-specific commands. */
    public static final class Builder {
        private final ArrayList<List<Bbos6ContextMenuItem>> groups = new ArrayList<>();
        private int columns = DEFAULT_COLUMNS;
        private String initialSelectionId;
        private boolean dismissOnAction = true;
        private OnActionListener actionListener;

        /**
         * Items within a group are laid out left to right, wrapping after
         * the configured number of columns. Groups receive thin separators.
         */
        public Builder addGroup(Bbos6ContextMenuItem... items) {
            Objects.requireNonNull(items, "items");
            return addGroup(Arrays.asList(items));
        }

        public Builder addGroup(List<Bbos6ContextMenuItem> items) {
            Objects.requireNonNull(items, "items");
            if (items.isEmpty()) throw new IllegalArgumentException("Empty group");
            ArrayList<Bbos6ContextMenuItem> copy = new ArrayList<>(items.size());
            for (Bbos6ContextMenuItem item : items) {
                copy.add(Objects.requireNonNull(item, "group item"));
            }
            groups.add(Collections.unmodifiableList(copy));
            return this;
        }

        /** Defaults to three columns, matching the reference screenshot. */
        public Builder columns(int count) {
            if (count < 1 || count > MAX_COLUMNS) {
                throw new IllegalArgumentException("columns must be 1.." + MAX_COLUMNS);
            }
            columns = count;
            return this;
        }

        /** Select by stable action ID; falls back to the first enabled item. */
        public Builder initiallySelected(String itemId) {
            initialSelectionId = Objects.requireNonNull(itemId, "itemId");
            return this;
        }

        /** Defaults to true, so a selected action closes the menu first. */
        public Builder dismissOnAction(boolean dismiss) {
            dismissOnAction = dismiss;
            return this;
        }

        public Builder onAction(OnActionListener listener) {
            actionListener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        public Dialog show(Activity activity) {
            Objects.requireNonNull(activity, "activity");
            if (actionListener == null) {
                throw new IllegalStateException("An action listener is required");
            }
            if (groups.isEmpty()) {
                throw new IllegalStateException("At least one action group is required");
            }
            if (activity.isFinishing() || activity.isDestroyed()) {
                throw new IllegalStateException("Cannot show on a closed Activity");
            }

            ArrayList<List<Bbos6ContextMenuItem>> snapshot =
                    new ArrayList<>(groups.size());
            Set<String> uniqueIds = new HashSet<>();
            for (List<Bbos6ContextMenuItem> group : groups) {
                for (Bbos6ContextMenuItem item : group) {
                    if (!uniqueIds.add(item.id())) {
                        throw new IllegalArgumentException(
                                "Duplicate menu action ID: " + item.id());
                    }
                }
                snapshot.add(new ArrayList<>(group));
            }
            MenuDialog dialog = new MenuDialog(activity, snapshot, columns,
                    initialSelectionId, dismissOnAction, actionListener);
            dialog.show();
            return dialog;
        }
    }

    /** Owns focus locally; never moves a selection in the underlying Activity. */
    private static final class MenuDialog extends Dialog {
        private final Activity activity;
        private final List<List<Bbos6ContextMenuItem>> groups;
        private final int columns;
        private final String initiallySelectedId;
        private final boolean dismissOnAction;
        private final OnActionListener actionListener;
        private final ArrayList<CellView> cells = new ArrayList<>();
        private final ArrayList<Bbos6ContextMenuNavigation.Cell> navigation =
                new ArrayList<>();
        private ScrollView scroll;
        private int selection = -1;
        private int heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN;

        MenuDialog(Activity activity, List<List<Bbos6ContextMenuItem>> groups,
                int columns, String initiallySelectedId, boolean dismissOnAction,
                OnActionListener actionListener) {
            super(activity);
            this.activity = activity;
            this.groups = groups;
            this.columns = columns;
            this.initiallySelectedId = initiallySelectedId;
            this.dismissOnAction = dismissOnAction;
            this.actionListener = actionListener;

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setCancelable(true);
            // Context/action menus can be dismissed without choosing an item.
            setCanceledOnTouchOutside(true);
            buildContent();
        }

        private void buildContent() {
            int[] viewport = availableViewport(activity);
            int panelWidth = Math.max(1, Math.min(dp(activity, MAX_WIDTH_DP),
                    Math.round(viewport[0] * WIDTH_FRACTION)));
            int maxHeight = Math.max(1, Math.round(viewport[1] * HEIGHT_FRACTION));
            int innerWidth = Math.max(1, panelWidth - 2 * dp(activity, CONTENT_INSET_DP));
            int cellWidth = Math.max(1, innerWidth / columns);
            int rowHeight = Math.max(dp(activity, 94),
                    Math.min(dp(activity, 166), Math.round(cellWidth * 1.02f)));
            // Fit the reference's three square-ish rows on Q25 square screens
            // when possible. Tall menus still scroll instead of truncating.
            int totalRows = 0;
            for (List<Bbos6ContextMenuItem> group : groups) {
                totalRows += (group.size() + columns - 1) / columns;
            }
            int separators = Math.max(0, groups.size() - 1) * dp(activity, 2);
            int roomForRows = maxHeight - separators
                    - 2 * dp(activity, CONTENT_INSET_DP);
            int fittingRowHeight = roomForRows / Math.max(1, totalRows);
            if (fittingRowHeight >= dp(activity, 94)) {
                rowHeight = Math.min(rowHeight, fittingRowHeight);
            }

            FrameLayout panel = new FrameLayout(activity);
            panel.setBackground(panelBackground(activity));
            panel.setClipToOutline(true);
            panel.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            dp(activity, CORNER_RADIUS_DP));
                }
            });
            panel.setFocusableInTouchMode(true);
            panel.setElevation(dp(activity, 3));

            BoundedScrollView scroller = new BoundedScrollView(activity, maxHeight);
            scroller.setFillViewport(false);
            scroller.setVerticalScrollBarEnabled(false);
            scroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            scroll = scroller;

            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(activity, CONTENT_INSET_DP),
                    dp(activity, CONTENT_INSET_DP),
                    dp(activity, CONTENT_INSET_DP),
                    dp(activity, CONTENT_INSET_DP));

            int globalRow = 0;
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                List<Bbos6ContextMenuItem> group = groups.get(groupIndex);
                for (int start = 0; start < group.size(); start += columns) {
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    body.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));

                    for (int col = 0; col < columns; col++) {
                        int itemPosition = start + col;
                        if (itemPosition < group.size()) {
                            addCell(row, group.get(itemPosition),
                                    globalRow, col, rowHeight, cellWidth);
                        } else {
                            // Keep partial rows left aligned in the same grid.
                            row.addView(new View(activity),
                                    new LinearLayout.LayoutParams(
                                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                        }
                    }
                    globalRow++;
                }
                if (groupIndex + 1 < groups.size()) addSeparator(body);
            }

            scroller.addView(body, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.addView(scroller, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            selection = Bbos6ContextMenuNavigation.firstEnabled(navigation);
            if (initiallySelectedId != null) {
                for (int i = 0; i < cells.size(); i++) {
                    if (cells.get(i).item.id().equals(initiallySelectedId)
                            && cells.get(i).item.enabled()) {
                        selection = i;
                        break;
                    }
                }
            }
            updateSelection();

            setContentView(panel);
            Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setGravity(Gravity.CENTER);
            }
            setOnShowListener(dialog -> {
                if (getWindow() != null) {
                    getWindow().setLayout(panelWidth,
                            WindowManager.LayoutParams.WRAP_CONTENT);
                }
                panel.requestFocus();
                revealSelection();
            });
        }

        private void addCell(LinearLayout row, Bbos6ContextMenuItem item,
                int gridRow, int column, int heightPx, int cellWidthPx) {
            FrameLayout hitTarget = new FrameLayout(activity);
            row.addView(hitTarget, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            int insetX = dp(activity, TILE_SIDE_INSET_DP);
            int insetY = dp(activity, TILE_TOP_INSET_DP);

            FrameLayout tile = new FrameLayout(activity);
            // StateListDrawable on the tile responds to the parent hit target
            // so touch-down shows the same blue face as trackpad selection.
            tile.setDuplicateParentStateEnabled(true);
            FrameLayout.LayoutParams tileParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            tileParams.setMargins(insetX, insetY, insetX, insetY);
            hitTarget.addView(tile, tileParams);

            int iconAreaHeight = Math.round((heightPx - 2 * insetY) * 0.64f);
            FrameLayout iconArea = new FrameLayout(activity);
            tile.addView(iconArea, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, iconAreaHeight,
                    Gravity.TOP));

            ImageView icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable image = resolveIcon(item);
            if (image != null) {
                icon.setImageDrawable(image);
            } else {
                icon.setVisibility(View.INVISIBLE);
            }
            int iconSize = Math.min(dp(activity, 66), Math.round(cellWidthPx * 0.48f));
            iconArea.addView(icon, new FrameLayout.LayoutParams(
                    iconSize, iconSize, Gravity.CENTER));

            TextView label = new TextView(activity);
            label.setText(item.label());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SIZE_SP);
            label.setTextColor(LABEL_COLOR);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setIncludeFontPadding(false);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setPadding(dp(activity, 2), 0, dp(activity, 2), 0);
            int labelHeight = Math.round((heightPx - 2 * insetY) * 0.38f);
            tile.addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, labelHeight,
                    Gravity.BOTTOM));

            hitTarget.setEnabled(item.enabled());
            hitTarget.setClickable(item.enabled());
            hitTarget.setContentDescription(item.label());
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            hitTarget.setAlpha(item.enabled() ? 1f : 0.42f);

            int index = cells.size();
            cells.add(new CellView(item, hitTarget, tile));
            navigation.add(new Bbos6ContextMenuNavigation.Cell(
                    gridRow, column, item.enabled()));
            if (item.enabled()) {
                hitTarget.setOnClickListener(view -> {
                    changeSelection(index);
                    invokeSelection();
                });
            }
        }

        private Drawable resolveIcon(Bbos6ContextMenuItem item) {
            Drawable provided = item.iconDrawable();
            if (provided != null) {
                Drawable.ConstantState state = provided.getConstantState();
                return state == null ? provided
                        : state.newDrawable(activity.getResources()).mutate();
            }
            return item.iconResourceId() == 0
                    ? null : activity.getDrawable(item.iconResourceId());
        }

        private void addSeparator(LinearLayout body) {
            View separator = new View(activity);
            GradientDrawable line = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xff72869b, 0xff32495d});
            separator.setBackground(line);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 2));
            params.leftMargin = dp(activity, 9);
            params.rightMargin = dp(activity, 9);
            body.addView(separator, params);
        }

        private void changeSelection(int newIndex) {
            if (newIndex == selection || newIndex < 0 || newIndex >= cells.size()
                    || !cells.get(newIndex).item.enabled()) return;
            selection = newIndex;
            updateSelection();
            revealSelection();
        }

        private void updateSelection() {
            for (int i = 0; i < cells.size(); i++) {
                CellView cell = cells.get(i);
                boolean highlighted = i == selection && cell.item.enabled();
                StateListDrawable states = new StateListDrawable();
                states.addState(new int[]{android.R.attr.state_pressed},
                        blueSelection(activity));
                states.addState(new int[]{}, highlighted
                        ? blueSelection(activity) : new ColorDrawable(Color.TRANSPARENT));
                cell.tile.setBackground(states);
                cell.tile.setElevation(highlighted ? dp(activity, 2) : 0);
            }
        }

        private void revealSelection() {
            if (selection < 0 || scroll == null) return;
            View cell = cells.get(selection).hitTarget;
            scroll.post(() -> {
                if (!isShowing()) return;
                Rect rectangle = new Rect(0, 0, cell.getWidth(), cell.getHeight());
                cell.requestRectangleOnScreen(rectangle, true);
            });
        }

        private void invokeSelection() {
            if (selection < 0 || selection >= cells.size()) return;
            Bbos6ContextMenuItem item = cells.get(selection).item;
            if (!item.enabled()) return;
            if (dismissOnAction) dismiss();
            actionListener.onAction(item);
        }

        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            int key = event.getKeyCode();
            Bbos6ContextMenuNavigation.Direction direction = null;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                direction = Bbos6ContextMenuNavigation.Direction.LEFT;
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                direction = Bbos6ContextMenuNavigation.Direction.RIGHT;
            } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                direction = Bbos6ContextMenuNavigation.Direction.UP;
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                direction = Bbos6ContextMenuNavigation.Direction.DOWN;
            }
            if (direction != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
                    changeSelection(Bbos6ContextMenuNavigation.move(
                            navigation, selection, direction));
                }
                return true;
            }

            if (key == KeyEvent.KEYCODE_DPAD_CENTER
                    || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) {
                    heldConfirmKey = key;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean activate = heldConfirmKey == key && !event.isCanceled();
                    heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
                    if (activate) invokeSelection();
                }
                // A key held past Android's normal repeat threshold must not
                // invoke an action multiple times.
                return true;
            }
            if (key == KeyEvent.KEYCODE_ESCAPE) {
                heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    dismiss();
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                heldConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
            }
            // Let Android handle Back and standard input/accessibility.
            return super.dispatchKeyEvent(event);
        }
    }

    private static final class CellView {
        final Bbos6ContextMenuItem item;
        final View hitTarget;
        final View tile;

        CellView(Bbos6ContextMenuItem item, View hitTarget, View tile) {
            this.item = item;
            this.hitTarget = hitTarget;
            this.tile = tile;
        }
    }

    private static Drawable blueSelection(Activity context) {
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff138fe4, 0xff0974cb, 0xff0750a8});
        face.setCornerRadius(dp(context, 7));
        face.setStroke(Math.max(1, dp(context, 1)), 0xff2368a0);
        return face;
    }

    private static Drawable panelBackground(Activity context) {
        int border = Math.max(1, dp(context, 1));
        int radius = dp(context, CORNER_RADIUS_DP);
        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff92a0ad, 0xff6f8294, 0xff3a4d5f});
        rim.setCornerRadius(radius);

        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{TOP_PANEL_COLOR, 0xff425a75, BOTTOM_PANEL_COLOR});
        body.setCornerRadius(Math.max(0, radius - border));

        LayerDrawable background = new LayerDrawable(new Drawable[]{rim, body});
        background.setLayerInset(1, border, border, border, border);
        return background;
    }

    private static int dp(Activity activity, float dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
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
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return new int[]{
                Math.max(1, metrics.widthPixels), Math.max(1, metrics.heightPixels)
        };
    }

    /** Limit only the menu's scroll content, not the containing Activity. */
    private static final class BoundedScrollView extends ScrollView {
        private final int maxHeightPx;

        BoundedScrollView(Activity activity, int maxHeightPx) {
            super(activity);
            this.maxHeightPx = maxHeightPx;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int limit = mode == MeasureSpec.UNSPECIFIED ? maxHeightPx
                    : Math.min(maxHeightPx, MeasureSpec.getSize(heightMeasureSpec));
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }
}
