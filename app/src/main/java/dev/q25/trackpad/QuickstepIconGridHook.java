package dev.q25.trackpad;

import android.content.Context;
import android.content.Intent;
import dev.q25.bb7pulldown.RecentsSelectionBridge;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.text.TextPaint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * BBUI's exclusive Recents presentation, inside the original Quickstep host.
 *
 * System ownership: Quickstep keeps its task model, native task objects,
 * app icons, task loading, lifecycle, and ActivityTaskManager actions.
 *
 * Presentation ownership: the RecentsView.draw(Canvas) entry point is
 * REPLACED. Native TaskView.draw(), dispatchDraw(), native carousel cards,
 * thumbnail headers, labels, live-tile composition and stock focus visuals
 * are never invoked while this mode is active. No overlay, new Activity,
 * second Recents host or thumbnail renderer is created.
 *
 * Native TaskViews remain bound solely as Quickstep's data/action handles,
 * not as a second visual presentation. A direct ROM port can replace their
 * inflation with data-only task records.
 *
 * Q25 experimental switch: debug.bbui.recents.icons=true.
 */
public final class QuickstepIconGridHook implements IXposedHookLoadPackage {
    private static final String TAG = "BBUI-RecentsIcons";
    private static final String PACKAGE = "com.android.launcher3";
    private static final String FLAG = "debug.bbui.recents.icons";
    private static final String RECENTS = "com.android.quickstep.views.RecentsView";
    private static final String TASK = "com.android.quickstep.views.TaskView";

    // Native Launcher3 scrim tint: black at 85% opacity / 15% transparency.
    // The icon renderer itself must never own the wallpaper background.
    private static final int OVERVIEW_SCRIM_TINT = 0xD9000000;
    private static final WeakHashMap<View, GridState> STATES = new WeakHashMap<>();
    // Quickstep TaskViews are recycled. NEVER cache a drawable by TaskView.
    // Cache fallback icons by the bound native task's package and user instead.
    private static final Map<String, Drawable> PACKAGE_ICONS = new HashMap<>();
    private static final Map<String, String> PACKAGE_LABELS = new HashMap<>();
    private static final WeakHashMap<View, Boolean> SURFACE_DIAG = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> CHROME_REMOVED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SURFACE_PENDING = new WeakHashMap<>();
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint LABEL_PAINT = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private static Class<?> nativeTaskType;
    private static Class<?> nativeRecentsType;
    private static boolean scrimAppliedLogged;
    private static final WeakHashMap<View, Integer> SCRIM_DRAW_COUNTS = new WeakHashMap<>();

    private static final class GridState {
        int selected = -1;
        int page;
        float startX;
        float startY;
        boolean swiping;
        boolean hadDown;
        boolean logged;
        int lastDrawableCount = -1;
        int diagnosticCount;
        String lastSelectionLogKey;
        String lastPublishedSelection;
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PACKAGE.equals(lp.packageName) || !PACKAGE.equals(lp.processName)) return;
        final Class<?> recents = XposedHelpers.findClassIfExists(RECENTS, lp.classLoader);
        final Class<?> task = XposedHelpers.findClassIfExists(TASK, lp.classLoader);
        final Class<?> paged = XposedHelpers.findClassIfExists(
                "com.android.launcher3.PagedView", lp.classLoader);
        if (recents == null || task == null || paged == null) {
            Log.e(TAG, "Native Quickstep host unavailable; no changes made");
            return;
        }
        nativeTaskType = task;
        nativeRecentsType = recents;

        try {
            // This is the decisive renderer boundary. Unlike the former
            // approach, NO native TaskView/card/header paint is executed.
            XposedBridge.hookAllMethods(recents, "draw", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!enabled() || !(p.args[0] instanceof Canvas)) return;
                    ViewGroup root = (ViewGroup) p.thisObject;
                    Canvas canvas = (Canvas) p.args[0];
                    // View.draw(Canvas) begins with Android's scroll transform:
                    // on the Q25 canvas matrix was translated -3850px while
                    // our icon cells use viewport coordinates 0..720.
                    // Cancel ONLY the native pager's scroll for this one
                    // BBUI-owned drawing pass. Save/restore leaves Android's
                    // native rendering state and task/lifecycle untouched.
                    int checkpoint = canvas.save();
                    try {
                        canvas.translate(root.getScrollX(), root.getScrollY());
                        paintGrid(root, canvas);
                    } finally {
                        canvas.restoreToCount(checkpoint);
                    }
                    p.setResult(null); // bypass RecentsView.draw AND ViewGroup children
                }
            });

            // Take the input stream before native carousel children receive
            // any touch. Coordinates now refer to BBUI icon cells only.
            XposedBridge.hookAllMethods(paged, "onInterceptTouchEvent",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (enabled() && recents.isInstance(p.thisObject))
                        p.setResult(true);
                }
            });
            XposedBridge.hookAllMethods(recents, "onTouchEvent", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!enabled() || !(p.args[0] instanceof MotionEvent)) return;
                    handleTouch((ViewGroup) p.thisObject, (MotionEvent) p.args[0]);
                    p.setResult(true);
                }
            });
            XposedBridge.hookAllMethods(recents, "dispatchKeyEvent",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!enabled() || !(p.args[0] instanceof KeyEvent)) return;
                    if (handleKey((ViewGroup) p.thisObject, (KeyEvent) p.args[0]))
                        p.setResult(true);
                }
            });

            // An entry transition still follows Android's real Overview
            // lifecycle. Only the presentation is ours.
            XposedBridge.hookAllMethods(recents, "setOverviewStateEnabled",
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length != 1) return;
                    ViewGroup root = (ViewGroup) p.thisObject;
                    if (!Boolean.TRUE.equals(p.args[0])) {
                        if (enabled()) RecentsSelectionBridge.leaveOverview(root.getContext());
                        state(root).lastPublishedSelection = null;
                        return;
                    }
                    if (!enabled()) return;
                    // The previous Overview's selection must never survive entry.
                    RecentsSelectionBridge.clear(root.getContext());
                    GridState state = state(root);
                    state.selected = -1;
                    state.page = 0;
                    state.lastSelectionLogKey = null;
                    state.lastPublishedSelection = null;
                    root.setFocusable(true);
                    root.post(() -> {
                        if (!enabled()) return;
                        removeStockChrome(root);
                        root.requestFocus();
                        root.invalidate();
                        // Native TaskViews are the only source of task IDs.
                        publishSelectedTask(root);
                        settleLiveTile(root);
                        // Read-only snapshot after Overview entry settles,
                        // not just when a scrim colour is assigned.
                        root.postDelayed(() -> {
                            if (enabled() && root.isAttachedToWindow()) {
                                reportNativeScrim(root, "settled");
                            }
                        }, 1200);
                    });
                }
            });

            // Native Quickstep can insert a temporary HOME TaskView during
            // entry. It is not an application to show in the icon switcher.
            XposedBridge.hookAllMethods(recents, "showCurrentTask",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!enabled() || p.args.length < 1 || p.args[0] == null) return;
                    try {
                        Object info = XposedHelpers.callMethod(
                                p.args[0], "getTaskInfo1");
                        if (info != null && ((Number) XposedHelpers.callMethod(
                                info, "getActivityType")).intValue() == 2) {
                            p.setResult(null);
                        }
                    } catch (Throwable ignored) {
                        // Keep native task lifecycle if task type is unknown.
                    }
                }
            });

            // Native task data/icon updates invalidate this *one* painter.
            XposedBridge.hookAllMethods(recents, "onLayout", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (enabled()) {
                        ViewGroup root = (ViewGroup) p.thisObject;
                        root.invalidate();
                        root.post(() -> publishSelectedTask(root));
                    }
                }
            });
            Class<?> chip = XposedHelpers.findClassIfExists(
                    "com.android.quickstep.views.IconAppChipView", lp.classLoader);
            if (chip != null) {
                XposedBridge.hookAllMethods(chip, "setDrawable",
                        new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!enabled() || !(p.thisObject instanceof View)) return;
                        View view = (View) p.thisObject;
                        for (int i = 0; view != null && i < 8; i++) {
                            if (recents.isInstance(view)) {
                                view.invalidate();
                                break;
                            }
                            view = view.getParent() instanceof View
                                    ? (View) view.getParent() : null;
                        }
                    }
                });
            }
            // Launcher3 owns the wallpaper tint through its full-screen
            // ScrimView, positioned BELOW the RecentsView in launcher.xml.
            // Turn off the native Overview depth blur, but let the native
            // ScrimView apply BBUI's black-with-85%-opacity tint.
            // When the flag is disabled, both hooks defer to Launcher3.
            Class<?> overviewState = XposedHelpers.findClassIfExists(
                    "com.android.launcher3.uioverrides.states.OverviewState",
                    lp.classLoader);
            Class<?> scrimColors = XposedHelpers.findClassIfExists(
                    "com.android.launcher3.views.ScrimColors", lp.classLoader);
            if (overviewState != null) {
                XposedBridge.hookAllMethods(overviewState, "getDepthUnchecked",
                        new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (enabled()) p.setResult(0f);
                    }
                });
            }
            if (overviewState != null && scrimColors != null) {
                XposedBridge.hookAllMethods(overviewState,
                        "getWorkspaceScrimColor", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!enabled()) return;
                        // WorkspaceStateTransitionAnimation.setScrim()
                        // forwards these values to ScrimView.setScrimColors.
                        // This is the actual background plane, not the
                        // task-icons Canvas which must remain transparent.
                        p.setResult(XposedHelpers.newInstance(scrimColors,
                                OVERVIEW_SCRIM_TINT, Color.TRANSPARENT));
                    }
                });
            }
            // Read-only confirmation that Launcher3 applied the intended
            // tint to the existing native scrim. No extra View is created.
            Class<?> scrimView = XposedHelpers.findClassIfExists(
                    "com.android.launcher3.views.ScrimView", lp.classLoader);
            if (scrimView != null) {
                XposedBridge.hookAllMethods(scrimView, "setScrimColors",
                        new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!enabled() || scrimAppliedLogged) return;
                        try {
                            Object value = XposedHelpers.callMethod(
                                    p.thisObject, "getBackgroundColor");
                            if (value instanceof Integer
                                    && ((Integer) value) == OVERVIEW_SCRIM_TINT) {
                                scrimAppliedLogged = true;
                                Log.i(TAG, "SCRIM-APPLIED native Launcher3 ScrimView "
                                        + "background=#"
                                        + Integer.toHexString(OVERVIEW_SCRIM_TINT));
                            }
                        } catch (Throwable error) {
                            Log.w(TAG, "Native scrim verification unavailable", error);
                        }
                    }
                });
            }
            if (scrimView != null) {
                XposedBridge.hookAllMethods(scrimView, "onDraw",
                        new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!enabled() || !(p.thisObject instanceof View)) return;
                        View v = (View) p.thisObject;
                        int count = SCRIM_DRAW_COUNTS.containsKey(v)
                                ? SCRIM_DRAW_COUNTS.get(v) : 0;
                        SCRIM_DRAW_COUNTS.put(v, count + 1);
                        if (count == 0 && p.args.length > 0
                                && p.args[0] instanceof Canvas) {
                            Rect bounds = new Rect();
                            ((Canvas) p.args[0]).getClipBounds(bounds);
                            Log.i(TAG, "SCRIM-DRAW first canvasClip="
                                    + bounds.toShortString()
                                    + " alpha=" + v.getAlpha()
                                    + " visible=" + v.isShown());
                        }
                    }
                });
            }
            Log.i(TAG, "Native Overview background hooks: depth="
                    + (overviewState != null) + " scrim="
                    + (overviewState != null && scrimColors != null)
                    + " nativeScrimView=" + (scrimView != null));
            Log.i(TAG, "Exclusive native Overview painter registered; requested=" + enabled());
        } catch (Throwable error) {
            Log.e(TAG, "Could not register native Overview replacement", error);
        }
    }

    private static GridState state(View root) {
        GridState result = STATES.get(root);
        if (result == null) {
            result = new GridState();
            STATES.put(root, result);
        }
        return result;
    }

    private static List<View> tasks(ViewGroup root) {
        List<View> result = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (nativeTaskType != null && nativeTaskType.isInstance(child))
                result.add(child);
        }
        return result;
    }

    private static RecentsIconGridLayout geometry(ViewGroup root, int count) {
        float density = root.getResources().getDisplayMetrics().density;
        return RecentsIconGridLayout.calculate(root.getWidth(), root.getHeight(),
                density, 0, count);
    }

    private static void normalize(GridState state, int count) {
        if (count <= 0) {
            state.selected = -1;
            state.page = 0;
            return;
        }
        if (state.selected < 0 || state.selected >= count) state.selected = 0;
        int maxPage = (count - 1) / RecentsIconGridLayout.PAGE_SIZE;
        state.page = Math.min(Math.max(state.page, 0), maxPage);
        if (state.selected / RecentsIconGridLayout.PAGE_SIZE != state.page)
            state.selected = state.page * RecentsIconGridLayout.PAGE_SIZE;
    }

    /**
     * The task identity is read directly from Quickstep's bound TaskContainer.
     * It is exactly the key used by TaskView.launchWithoutAnimation.
     */
    private static final class TaskIdentity {
        final int id;
        final int user;
        final String pkg;
        final String component;

        TaskIdentity(int id, int user, String pkg, String component) {
            this.id = id;
            this.user = user;
            this.pkg = pkg;
            this.component = component;
        }

        String cacheKey() { return pkg + "|" + user; }

        @Override public String toString() {
            return "taskId=" + id + " user=" + user + " package=" + pkg
                    + " component=" + component;
        }
    }

    private static TaskIdentity taskIdentity(View task, int containerIndex) {
        try {
            Object containers = XposedHelpers.callMethod(task, "getTaskContainers");
            if (!(containers instanceof List)) return null;
            List<?> all = (List<?>) containers;
            if (containerIndex < 0 || containerIndex >= all.size()) return null;
            Object bound = XposedHelpers.callMethod(all.get(containerIndex), "getTask");
            Object key = XposedHelpers.getObjectField(bound, "key");
            Intent intent = (Intent) XposedHelpers.getObjectField(key, "baseIntent");
            if (intent == null) return null;
            String pkg = intent.getComponent() == null ? intent.getPackage()
                    : intent.getComponent().getPackageName();
            if (pkg == null) return null;
            int id = XposedHelpers.getIntField(key, "id");
            int user;
            try {
                user = XposedHelpers.getIntField(key, "userId");
            } catch (Throwable ignored) {
                user = -1;
            }
            return new TaskIdentity(id, user, pkg,
                    intent.getComponent() == null ? "(none)"
                            : intent.getComponent().flattenToShortString());
        } catch (Throwable error) {
            return null;
        }
    }

    private static Drawable nativeIcon(View task, int containerIndex) {
        try {
            Object containers = XposedHelpers.callMethod(task, "getTaskContainers");
            if (!(containers instanceof List)) return null;
            List<?> all = (List<?>) containers;
            if (containerIndex < 0 || containerIndex >= all.size()) return null;
            Object chip = XposedHelpers.callMethod(all.get(containerIndex), "getIconView");
            Object drawable = XposedHelpers.callMethod(chip, "getDrawable");
            if (drawable instanceof Drawable) return (Drawable) drawable;

            // Native icons are null on the Q25 build. A Quickstep TaskView
            // may be reused for a DIFFERENT app: the old view-keyed cache
            // returned the previous app's icon while launching the new task.
            TaskIdentity identity = taskIdentity(task, containerIndex);
            if (identity == null) return null;
            Drawable cached = PACKAGE_ICONS.get(identity.cacheKey());
            if (cached != null) return cached;
            Drawable installed = task.getContext().getPackageManager()
                    .getApplicationIcon(identity.pkg);
            PACKAGE_ICONS.put(identity.cacheKey(), installed);
            return installed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String appLabel(View task, TaskIdentity identity) {
        if (identity == null) return "Unknown application";
        String cached = PACKAGE_LABELS.get(identity.cacheKey());
        if (cached != null) return cached;
        String label = identity.pkg;
        try {
            android.content.pm.PackageManager pm =
                    task.getContext().getPackageManager();
            CharSequence displayName = pm.getApplicationLabel(
                    pm.getApplicationInfo(identity.pkg, 0));
            if (displayName != null && displayName.length() > 0) {
                label = displayName.toString();
            }
        } catch (Throwable ignored) {
            // Task package remains the canonical diagnostic fallback.
        }
        PACKAGE_LABELS.put(identity.cacheKey(), label);
        return label;
    }

    private static void logSelectionIfChanged(ViewGroup root, GridState state,
            List<View> all) {
        if (state.selected < 0 || state.selected >= all.size()) return;
        View task = all.get(state.selected);
        TaskIdentity identity = taskIdentity(task, 0);
        String key = "index=" + state.selected + " page=" + state.page
                + " view=" + System.identityHashCode(task)
                + " " + (identity == null ? "UNBOUND" : identity.toString());
        if (!key.equals(state.lastSelectionLogKey)) {
            state.lastSelectionLogKey = key;
            Log.i(TAG, "SELECT " + key
                    + " label=" + appLabel(task, identity));
            // Do not perform cross-process IPC inside Canvas.draw().
            root.post(() -> publishSelectedTask(root));
        }
    }

    /**
     * Publish only a real, currently bound native Quickstep task selection.
     * This is an ephemeral identity bridge, not a second task model.
     */
    private static void publishSelectedTask(ViewGroup root) {
        if (!enabled() || !root.isShown()) return;
        try {
            if (!XposedHelpers.getBooleanField(root, "mOverviewStateEnabled")) return;
        } catch (Throwable ignored) {
            return; // Unknown host state: fail closed, do not publish.
        }
        GridState state = state(root);
        List<View> all = tasks(root);
        normalize(state, all.size());
        // A split/group TaskView binds several tasks. Do not silently close
        // only its first member: stock swipe dismisses the complete group.
        TaskIdentity identity = state.selected >= 0 && state.selected < all.size()
                && iconCount(all.get(state.selected)) == 1
                ? taskIdentity(all.get(state.selected), 0) : null;
        String key = identity == null ? "" : identity.toString();
        if (key.equals(state.lastPublishedSelection)) return;
        boolean ok = identity == null
                ? RecentsSelectionBridge.clear(root.getContext())
                : RecentsSelectionBridge.publish(root.getContext(),
                        identity.id, identity.user, identity.pkg);
        if (ok) state.lastPublishedSelection = key;
    }

    private static int iconCount(View task) {
        try {
            Object containers = XposedHelpers.callMethod(task, "getTaskContainers");
            return containers instanceof List ? ((List<?>) containers).size() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void paintGrid(ViewGroup root, Canvas canvas) {
        // This canvas owns ONLY BBUI's icon grid, heading and focus.
        // The wallpaper tint is composed by Launcher3's native ScrimView
        // below this view, not by painting black into the icon surface.
        List<View> all = tasks(root);
        if (all.isEmpty()) {
            GridState state = state(root);
            if (!"".equals(state.lastPublishedSelection)) {
                root.post(() -> publishSelectedTask(root));
            }
            return;
        }
        RecentsIconGridLayout grid = geometry(root, all.size());
        if (grid == null) return;
        GridState state = state(root);
        normalize(state, all.size());
        logSelectionIfChanged(root, state, all);
        if (!state.logged) {
            state.logged = true;
            Log.i(TAG, "BBUI-only Recents drawing: " + all.size()
                    + " tasks, 4x2 icon slots, native cards NOT painted");
        }

        int drawn = 0;
        int bound = 0;
        int pageOffset = state.page * grid.pageWidth;
        int first = state.page * RecentsIconGridLayout.PAGE_SIZE;
        int end = Math.min(all.size(), first + RecentsIconGridLayout.PAGE_SIZE);
        int normalIcon = Math.min(dp(root, 55),
                Math.min(grid.cellWidth, grid.cellHeight) - dp(root, 18));
        for (int index = first; index < end; index++) {
            View task = all.get(index);
            int number = Math.min(4, iconCount(task));
            if (number > 0) bound++;
            if (number == 0) continue;
            int columns = number == 1 ? 1 : 2;
            int rows = (number + columns - 1) / columns;
            int iconSize = number == 1 ? normalIcon : Math.max(1, normalIcon / 2);
            int sep = number == 1 ? 0 : dp(root, 3);
            int iconsWide = columns * iconSize + (columns - 1) * sep;
            int iconsTall = rows * iconSize + (rows - 1) * sep;
            int x = grid.x(index) - pageOffset
                    + (grid.cellWidth - iconsWide) / 2;
            int y = grid.y(index) + (grid.cellHeight - iconsTall) / 2;

            if (state.selected == index) {
                float padding = dp(root, 7);
                GridSelectionHighlight.draw(canvas,
                        x - padding, y - padding,
                        x + iconsWide + padding, y + iconsTall + padding,
                        root.getResources().getDisplayMetrics().density);
            }

            for (int j = 0; j < number; j++) {
                Drawable icon = nativeIcon(task, j);
                if (icon == null) continue;
                Rect restore = new Rect(icon.getBounds());
                int left = x + (j % columns) * (iconSize + sep);
                int top = y + (j / columns) * (iconSize + sep);
                try {
                    icon.setBounds(left, top, left + iconSize, top + iconSize);
                    icon.draw(canvas);
                    drawn++;
                } finally {
                    icon.setBounds(restore);
                }
            }
        }
        // One selected-app heading for the whole grid, not one label below
        // each icon. Matches the BlackBerry OS 6 heading/divider arrangement.
        if (state.selected >= 0 && state.selected < all.size()) {
            paintSelectedAppHeader(root, canvas, grid, all.get(state.selected));
        }
        // A small, centered page indicator directly below the icon grid.
        // Keyboard and touch paging both update state.page and invalidate
        // this same renderer, so no separate view or paging model is needed.
        paintPageCounter(root, canvas, grid, state.page);
        // Bounded diagnostics identify the real failing lookup path. They do
        // not modify the native task data or replace the presentation again.
        if (state.diagnosticCount < 4
                && (state.diagnosticCount == 0
                || drawn != state.lastDrawableCount)) {
            state.diagnosticCount++;
            state.lastDrawableCount = drawn;
            Log.i(TAG, "GRID-DIAG root=" + root.getWidth() + "x"
                    + root.getHeight() + " density="
                    + root.getResources().getDisplayMetrics().density
                    + " cell=" + grid.cellWidth + "x" + grid.cellHeight
                    + " tasks=" + all.size() + " page=" + state.page
                    + " boundTasksOnPage=" + bound + " iconsPainted=" + drawn);
            if (!SURFACE_DIAG.containsKey(root)) {
                SURFACE_DIAG.put(root, Boolean.TRUE);
                reportVisibleSurface(root, canvas);
            }
            for (int i = first; i < Math.min(end, first + 8); i++) {
                Log.i(TAG, "ICON-DIAG " + i + " " + diagnoseIcon(all.get(i)));
            }
        }
    }

    /**
     * BlackBerry OS 6-style title above the icon grid: selected application
     * centered over a subtle horizontal separator. No native view or overlay
     * is introduced; this remains part of BBUI's sole Canvas renderer.
     */
    private static void paintSelectedAppHeader(ViewGroup root, Canvas canvas,
            RecentsIconGridLayout grid, View selectedTask) {
        TaskIdentity identity = taskIdentity(selectedTask, 0);
        String label = appLabel(selectedTask, identity);
        int dividerY = grid.top - dp(root, 4);
        int dividerHeight = Math.max(1, dp(root, 1));
        synchronized (PAINT) {
            PAINT.setStyle(Paint.Style.FILL);
            PAINT.setColor(0xff5a5d63);
            canvas.drawRect(grid.left, dividerY,
                    grid.left + grid.gridWidth(),
                    dividerY + dividerHeight, PAINT);
        }
        synchronized (LABEL_PAINT) {
            LABEL_PAINT.setColor(0xfff3f3f5);
            LABEL_PAINT.setTextSize(dp(root, 17));
            LABEL_PAINT.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            LABEL_PAINT.setTextAlign(Paint.Align.CENTER);
            String visibleLabel = TextUtils.ellipsize(label, LABEL_PAINT,
                    Math.max(1, grid.gridWidth() - dp(root, 16)),
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(visibleLabel, grid.centerX(),
                    dividerY - dp(root, 11), LABEL_PAINT);
        }
    }

    /** One-based page position beneath the grid, omitted for a single page. */
    private static void paintPageCounter(ViewGroup root, Canvas canvas,
            RecentsIconGridLayout grid, int currentPage) {
        String label = grid.pageCounterText(currentPage);
        if (label == null) return;
        synchronized (LABEL_PAINT) {
            LABEL_PAINT.setColor(0xffd4d7dc);
            LABEL_PAINT.setTextSize(dp(root, 12));
            LABEL_PAINT.setTypeface(android.graphics.Typeface.DEFAULT);
            LABEL_PAINT.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, grid.centerX(),
                    grid.gridBottom() + dp(root, 20), LABEL_PAINT);
        }
    }

    /**
     * Read-only compositor diagnosis. Inspect the native background view
     * after Overview settles; setScrimColors alone proves assignment only.
     */
    private static void reportNativeScrim(View recents, String phase) {
        try {
            View windowRoot = recents.getRootView();
            int id = recents.getResources().getIdentifier(
                    "scrim_view", "id", PACKAGE);
            View scrim = id == 0 ? null : windowRoot.findViewById(id);
            if (scrim == null) {
                Log.i(TAG, "SCRIM-STATE phase=" + phase
                        + " scrimView=MISSING id=" + id
                        + " windowRoot=" + windowRoot.getClass().getSimpleName());
                return;
            }
            Rect rect = new Rect();
            boolean intersectsWindow = scrim.getGlobalVisibleRect(rect);
            int color = scrim.getBackground() instanceof ColorDrawable
                    ? ((ColorDrawable) scrim.getBackground()).getColor()
                    : -1;
            int drawCount = SCRIM_DRAW_COUNTS.containsKey(scrim)
                    ? SCRIM_DRAW_COUNTS.get(scrim) : 0;
            ViewGroup parent = scrim.getParent() instanceof ViewGroup
                    ? (ViewGroup) scrim.getParent() : null;
            int scrimIndex = parent == null ? -1 : parent.indexOfChild(scrim);
            int recentsIndex = parent == recents.getParent()
                    ? parent.indexOfChild(recents) : -1;
            Log.i(TAG, "SCRIM-STATE phase=" + phase
                    + " color=#" + Integer.toHexString(color)
                    + " expected=#" + Integer.toHexString(OVERVIEW_SCRIM_TINT)
                    + " alpha=" + scrim.getAlpha()
                    + " visibility=" + scrim.getVisibility()
                    + " isShown=" + scrim.isShown()
                    + " windowVisibility=" + scrim.getWindowVisibility()
                    + " attached=" + scrim.isAttachedToWindow()
                    + " draws=" + drawCount
                    + " size=" + scrim.getWidth() + "x" + scrim.getHeight()
                    + " screenRect=" + rect.toShortString()
                    + " globalVisible=" + intersectsWindow
                    + " parent=" + (parent == null ? "none"
                            : parent.getClass().getSimpleName())
                    + " scrimIndex=" + scrimIndex
                    + " recentsIndex=" + recentsIndex);
            if (parent != null) {
                for (int i = scrimIndex + 1; i < parent.getChildCount(); i++) {
                    View sibling = parent.getChildAt(i);
                    Log.i(TAG, "SCRIM-ABOVE child=" + i
                            + " class=" + sibling.getClass().getSimpleName()
                            + " visible=" + sibling.getVisibility()
                            + " alpha=" + sibling.getAlpha()
                            + " background=" + (sibling.getBackground() == null
                                    ? "none" : sibling.getBackground()
                                            .getClass().getSimpleName())
                            + " size=" + sibling.getWidth() + "x"
                            + sibling.getHeight());
                }
            }
            View ancestor = scrim;
            for (int depth = 0; depth < 5 && ancestor != null; depth++) {
                Log.i(TAG, "SCRIM-PARENT depth=" + depth
                        + " class=" + ancestor.getClass().getSimpleName()
                        + " visibility=" + ancestor.getVisibility()
                        + " alpha=" + ancestor.getAlpha()
                        + " size=" + ancestor.getWidth() + "x"
                        + ancestor.getHeight());
                ancestor = ancestor.getParent() instanceof View
                        ? (View) ancestor.getParent() : null;
            }
        } catch (Throwable e) {
            Log.w(TAG, "SCRIM-STATE read-only inspection failed", e);
        }
    }

    /**
     * Inspect the actual Canvas clipping and visual ancestry at paint time.
     * icon.draw() returning does NOT establish that those pixels reached the
     * screen. A view can be clipped, translated, transparent, or obscured by
     * an upper sibling; these observations differentiate those cases.
     */
    private static void reportVisibleSurface(ViewGroup root, Canvas canvas) {
        Rect clip = new Rect();
        boolean hasClip = canvas.getClipBounds(clip);
        Rect screenBounds = new Rect();
        boolean screenVisible = root.getGlobalVisibleRect(screenBounds);
        Matrix matrix = new Matrix();
        canvas.getMatrix(matrix);
        float[] m = new float[9];
        matrix.getValues(m);
        Log.i(TAG, "SURFACE-DIAG canvasClip=" + clip.toShortString()
                + " hasClip=" + hasClip
                + " matrix=[" + m[0] + "," + m[1] + "," + m[2]
                + ";" + m[3] + "," + m[4] + "," + m[5] + "]"
                + " hardware=" + canvas.isHardwareAccelerated()
                + " rootShown=" + root.isShown()
                + " rootAlpha=" + root.getAlpha()
                + " rootVisibility=" + root.getVisibility()
                + " windowVisibility=" + root.getWindowVisibility()
                + " rootScreenBounds=" + screenBounds.toShortString()
                + " globalVisible=" + screenVisible
                + " scroll=" + root.getScrollX() + "," + root.getScrollY()
                + " context=" + root.getContext().getClass().getName());

        View current = root;
        for (int depth = 0; depth < 7 && current != null; depth++) {
            ViewGroup parent = current.getParent() instanceof ViewGroup
                    ? (ViewGroup) current.getParent() : null;
            Log.i(TAG, "VIEW-DIAG depth=" + depth
                    + " class=" + current.getClass().getSimpleName()
                    + " position=" + current.getLeft() + "," + current.getTop()
                    + " size=" + current.getWidth() + "x" + current.getHeight()
                    + " alpha=" + current.getAlpha()
                    + " visibility=" + current.getVisibility()
                    + " scale=" + current.getScaleX() + "," + current.getScaleY()
                    + " translation=" + current.getTranslationX()
                    + "," + current.getTranslationY()
                    + " childIndex=" + (parent != null
                            ? parent.indexOfChild(current) : -1));
            if (parent != null && depth < 3) {
                boolean above = false;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child == current) {
                        above = true;
                        continue;
                    }
                    if (!above || child.getVisibility() != View.VISIBLE) continue;
                    Log.i(TAG, "ABOVE-DIAG parent="
                            + parent.getClass().getSimpleName()
                            + " class=" + child.getClass().getSimpleName()
                            + " bounds=" + child.getLeft() + ","
                            + child.getTop() + ","
                            + child.getRight() + "," + child.getBottom()
                            + " alpha=" + child.getAlpha());
                }
            }
            current = parent;
        }
    }

    private static String diagnoseIcon(View task) {
        StringBuilder detail = new StringBuilder("class=")
                .append(task.getClass().getSimpleName());
        try {
            Object value = XposedHelpers.callMethod(task, "getTaskContainers");
            if (!(value instanceof List)) return detail + " containersType="
                    + (value == null ? "null" : value.getClass().getName());
            List<?> containers = (List<?>) value;
            detail.append(" containers=").append(containers.size());
            if (containers.isEmpty()) return detail.toString();
            Object nativeContainer = containers.get(0);
            detail.append(" nativeContainer=")
                    .append(nativeContainer.getClass().getSimpleName());
            Object chip = XposedHelpers.callMethod(nativeContainer, "getIconView");
            detail.append(" chip=").append(chip == null ? "null"
                    : chip.getClass().getSimpleName());
            if (chip != null) {
                Object icon = XposedHelpers.callMethod(chip, "getDrawable");
                detail.append(" nativeDrawable=")
                        .append(icon == null ? "null"
                                : icon.getClass().getSimpleName());
            }
            Object nativeTask = XposedHelpers.callMethod(nativeContainer, "getTask");
            detail.append(" nativeTask=")
                    .append(nativeTask == null ? "null"
                            : nativeTask.getClass().getSimpleName());
            if (nativeTask == null) return detail.toString();
            Object key = XposedHelpers.getObjectField(nativeTask, "key");
            detail.append(" key=").append(key == null ? "null"
                    : key.getClass().getSimpleName());
            if (key != null) {
                Object intent = XposedHelpers.getObjectField(key, "baseIntent");
                detail.append(" baseIntent=")
                        .append(intent == null ? "null"
                                : intent.getClass().getSimpleName());
                if (intent instanceof Intent) {
                    Intent base = (Intent) intent;
                    String pkg = base.getComponent() == null
                            ? base.getPackage() : base.getComponent().getPackageName();
                    detail.append(" package=").append(pkg);
                    if (pkg != null) {
                        try {
                            Drawable fallback = task.getContext().getPackageManager()
                                    .getApplicationIcon(pkg);
                            detail.append(" packageIcon=")
                                    .append(fallback == null ? "null"
                                            : fallback.getClass().getSimpleName());
                        } catch (Throwable error) {
                            detail.append(" packageIconError=")
                                    .append(error.getClass().getSimpleName())
                                    .append(":").append(error.getMessage());
                        }
                    }
                }
            }
            return detail.toString();
        } catch (Throwable error) {
            return detail + " FAIL=" + error.getClass().getName()
                    + ":" + error.getMessage();
        }
    }

    private static boolean handleKey(ViewGroup root, KeyEvent key) {
        int code = key.getKeyCode();
        boolean navigate = code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean activate = code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean nextPage = code == KeyEvent.KEYCODE_PAGE_DOWN;
        boolean previousPage = code == KeyEvent.KEYCODE_PAGE_UP;
        if (!navigate && !activate && !nextPage && !previousPage) return false;
        if (key.getAction() != KeyEvent.ACTION_DOWN) return true;

        List<View> all = tasks(root);
        if (all.isEmpty()) return true;
        GridState state = state(root);
        normalize(state, all.size());
        RecentsIconGridLayout grid = geometry(root, all.size());
        if (grid == null) return true;

        if (activate) {
            launchTask(all.get(state.selected), state.selected, state.page,
                    "keyboard");
            return true;
        }

        int selected = state.selected;
        if (nextPage || previousPage) {
            int page = state.page + (nextPage ? 1 : -1);
            if (page >= 0 && page < grid.pageCount) {
                state.page = page;
                state.selected = page * RecentsIconGridLayout.PAGE_SIZE;
            }
        } else {
            int direction;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP: direction = 0; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: direction = 1; break;
                case KeyEvent.KEYCODE_DPAD_LEFT: direction = 2; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: direction = 3; break;
                default: return true;
            }
            // The grid owns its geometry. Native Quickstep carousel
            // navigation, snaps and focus movement are NEVER used.
            int destination = grid.nextIndex(selected, direction);
            if (destination >= 0) {
                state.selected = destination;
                state.page = destination / RecentsIconGridLayout.PAGE_SIZE;
            }
        }
        publishSelectedTask(root);
        root.invalidate();
        return true;
    }

    private static void handleTouch(ViewGroup root, MotionEvent event) {
        List<View> all = tasks(root);
        GridState state = state(root);
        normalize(state, all.size());
        RecentsIconGridLayout grid = geometry(root, all.size());
        if (grid == null) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                state.startX = event.getX();
                state.startY = event.getY();
                state.hadDown = true;
                state.swiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (state.hadDown && Math.abs(event.getX() - state.startX)
                        > dp(root, 20)) state.swiping = true;
                break;
            case MotionEvent.ACTION_UP:
                if (!state.hadDown) break;
                float xDistance = event.getX() - state.startX;
                float yDistance = event.getY() - state.startY;
                if (Math.abs(xDistance) > root.getWidth() * 0.16f
                        && Math.abs(xDistance) > Math.abs(yDistance)) {
                    int next = state.page + (xDistance < 0 ? 1 : -1);
                    if (next >= 0 && next < grid.pageCount) {
                        state.page = next;
                        state.selected = next * RecentsIconGridLayout.PAGE_SIZE;
                        publishSelectedTask(root);
                        root.invalidate();
                    }
                } else if (!state.swiping && Math.abs(yDistance) < dp(root, 20)) {
                    int hit = hitTest(grid, state.page, event.getX(),
                            event.getY(), all.size());
                    if (hit >= 0) {
                        state.selected = hit;
                        root.invalidate();
                        launchTask(all.get(hit), hit, state.page, "touch");
                    }
                }
                state.hadDown = false;
                state.swiping = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                state.hadDown = false;
                state.swiping = false;
                break;
            default:
                break;
        }
    }

    private static int hitTest(RecentsIconGridLayout grid, int page,
            float x, float y, int count) {
        int begin = page * RecentsIconGridLayout.PAGE_SIZE;
        int end = Math.min(count, begin + RecentsIconGridLayout.PAGE_SIZE);
        for (int i = begin; i < end; i++) {
            int left = grid.x(i) - page * grid.pageWidth;
            int top = grid.y(i);
            if (x >= left && x < left + grid.cellWidth
                    && y >= top && y < top + grid.cellHeight) return i;
        }
        return -1;
    }

    /** Calls Android's own task-launch method, without the stock card animation. */
    private static void launchTask(View nativeTask, int index, int page,
            String source) {
        TaskIdentity identity = taskIdentity(nativeTask, 0);
        String target = "source=" + source + " index=" + index
                + " page=" + page
                + " view=" + System.identityHashCode(nativeTask)
                + " " + (identity == null ? "UNBOUND" : identity.toString());
        Log.i(TAG, "LAUNCH-REQUEST " + target);
        try {
            ClassLoader loader = nativeTask.getClass().getClassLoader();
            Class<?> callbackType = Class.forName("kotlin.jvm.functions.Function1",
                    false, loader);
            Class<?> unitType = Class.forName("kotlin.Unit", false, loader);
            Object unit = XposedHelpers.getStaticObjectField(unitType, "INSTANCE");
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            Log.i(TAG, "LAUNCH-RESULT " + target
                                    + " success=" + (args != null && args.length > 0
                                            ? args[0] : "unknown"));
                            return unit;
                        }
                        if ("toString".equals(method.getName())) return "BBUIRecentsCallback";
                        if ("hashCode".equals(method.getName()))
                            return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName()))
                            return proxy == args[0];
                        return null;
                    });
            XposedHelpers.callMethod(nativeTask, "launchWithoutAnimation",
                    false, callback);
        } catch (Throwable error) {
            Log.w(TAG, "LAUNCH-FALLBACK " + target
                    + " reason=" + error.getClass().getSimpleName());
            // Still the same Android task action, never start a new app intent.
            nativeTask.performClick();
        }
    }

    private static void removeStockChrome(ViewGroup root) {
        if (CHROME_REMOVED.containsKey(root)) return;
        CHROME_REMOVED.put(root, Boolean.TRUE);
        removeNamedView(root, "overview_actions_view");
        removeNamedView(root, "empty_recents_message_view");
    }

    private static void removeNamedView(View root, String name) {
        Context context = root.getContext();
        int id = context.getResources().getIdentifier(name, "id", PACKAGE);
        if (id == 0) return;
        View found = root.getRootView().findViewById(id);
        if (found != null && found.getParent() instanceof ViewGroup) {
            ((ViewGroup) found.getParent()).removeView(found);
            Log.i(TAG, "Detached stock Overview chrome: " + name);
        }
    }

    /** End stock live-tile remote drawing after native Overview entry. */
    private static void settleLiveTile(View root) {
        if (SURFACE_PENDING.containsKey(root)) return;
        try {
            if (!XposedHelpers.getBooleanField(root, "mOverviewStateEnabled")
                    || XposedHelpers.getObjectField(root, "mRecentsAnimationController") == null
                    || XposedHelpers.getBooleanField(root, "mRunningTaskShowScreenshot"))
                return;
        } catch (Throwable ignored) {
            return;
        }
        SURFACE_PENDING.put(root, Boolean.TRUE);
        try {
            XposedHelpers.callMethod(root, "switchToScreenshot", (Runnable) () -> {
                try {
                    XposedHelpers.callMethod(root, "finishRecentsAnimation",
                            true, false, (Runnable) () -> {
                                SURFACE_PENDING.remove(root);
                                root.invalidate();
                            });
                } catch (Throwable error) {
                    SURFACE_PENDING.remove(root);
                    Log.w(TAG, "Native animation completion unavailable", error);
                }
            });
        } catch (Throwable error) {
            SURFACE_PENDING.remove(root);
            Log.w(TAG, "Native screenshot conversion unavailable", error);
        }
    }

    private static int dp(View root, float value) {
        return Math.round(value * root.getResources().getDisplayMetrics().density);
    }

    private static boolean enabled() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return (Boolean) XposedHelpers.callStaticMethod(properties,
                    "getBoolean", FLAG, false);
        } catch (Throwable error) {
            return false;
        }
    }
}
