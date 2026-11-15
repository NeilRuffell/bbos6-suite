package dev.q25.bbmessages;

import android.app.Activity;
import android.app.Instrumentation;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Read-only diagnostics for the installed Google Messages UI.
 *
 * Intentionally does not inspect TextView text, Compose semantics, message
 * data, accounts, or contact details. Does not modify any target View.
 * Removing this Vector module or disabling its target scope restores the
 * untouched Google Messages application.
 */
public final class MessagesInspectionHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.google.android.apps.messaging";
    private static final String TAG = "BBMessagesInspect";
    private static final String INBOX_PROP = "debug.bbmessages.inbox.enabled";
    private static final String PAGE_PROP = "debug.bbmessages.page.enabled";
    private static final String ACTION_PROBE_PROP =
        "debug.bbmessages.actionprobe.enabled";
    private static final String NATIVE_ROW_PROP =
        "debug.bbmessages.native.rows.enabled";
    private static final String SHARED_PAGE_PROP =
        "debug.bbmessages.shared.page.enabled";
    private static final String ROW_CLASS =
        "com.google.android.apps.messaging.home.list.ConversationListItemView";
    private static final int MAX_VIEWS = 240;
    private static final int MAX_DEPTH = 14;
    private static final int MAX_CHILDREN_PER_GROUP = 45;

    // An Activity instance is logged once, avoiding repeated log spam after Back.
    private final Set<Activity> inspected = Collections.newSetFromMap(
        new WeakHashMap<Activity, Boolean>()
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET.equals(param.packageName) || !TARGET.equals(param.processName)) {
            return;
        }

        try {
            // Framework lifecycle hook avoids assuming obfuscated Google
            // Messages Activity class names, which vary between app versions.
            XposedBridge.hookAllMethods(
                Instrumentation.class,
                "callActivityOnResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam hook) {
                        if (hook.hasThrowable()
                            || hook.args.length == 0
                            || !(hook.args[0] instanceof Activity)) {
                            return;
                        }
                        Activity activity = (Activity) hook.args[0];
                        if (!inspected.add(activity)) {
                            return;
                        }

                        View decor = activity.getWindow() == null
                            ? null : activity.getWindow().getDecorView();
                        if (decor == null) {
                            return;
                        }

                        if (propertyEnabled(SHARED_PAGE_PROP)
                            && !isActionProbeEnabled()) {
                            // Attach before first draw. Never show a Google
                            // inbox and replace it 750ms later.
                            MessagesInboxPageBridge.watchFirstDraw(activity);
                        } else {
                            // Existing read-only inspection mode only.
                            decor.postDelayed(() -> inspect(activity), 750L);
                        }
                    }
                }
            );
            Log.i(TAG, "Inspection enabled for main Google Messages process");
            if (isActionProbeEnabled()) {
                Log.i(TAG, "Native action diagnostics enabled; stock Google UI retained");
                MessagesNativeActionTrace.install(param.classLoader);
            } else if (propertyEnabled(SHARED_PAGE_PROP)) {
                // Source-time shared Bbos6PageList takes precedence over
                // the incomplete Google-RecyclerView row-skin experiment.
                hookAdapterBindings(param.classLoader);
                hookNativeControllerOutcome(param.classLoader);
                Log.i(TAG, "SOURCE_PAGE shared BBOS6 controller opted in");
            } else if (propertyEnabled(NATIVE_ROW_PROP)) {
                // This is not the rejected delayed page-swap or a row overlay.
                // Reuse Google's own RecyclerView and row actions; insert the
                // shared BBOS6 composable as a native row child at bind time.
                hookNativeRowFaces(param.classLoader);
            } else if (isPageEnabled() || isInboxPaintEnabled()) {
                // The 750ms delayed full-page view swap and row overlay
                // were rejected by Q25 acceptance: the native Google inbox
                // flashes first, a second list is mounted over it, and
                // detached ViewHolder clicks cannot open conversations.
                // Do not silently restore that invalid architecture.
                Log.w(TAG, "Legacy overlay modes disabled; native Google inbox retained");
            } else {
                Log.i(TAG, "Controller-only investigation mode; native UI retained");
            }
        } catch (Throwable failure) {
            Log.e(TAG, "Unable to install read-only inspection hook", failure);
        }
    }

    /**
     * One opt-in, version-checked native binder hook. Fails closed on
     * unsupported Google versions and does not alter navigation or data.
     *
     * Google bind runs before RecyclerView draws a newly bound row, avoiding
     * the rejected post-resume full-page swap and independent overlay list.
     */
    private static void hookNativeRowFaces(ClassLoader loader) {
        try {
            Class<?> adapter = XposedHelpers.findClassIfExists("vv", loader);
            Class<?> holder = XposedHelpers.findClassIfExists("aac", loader);
            if (adapter == null || holder == null
                || adapter.getSuperclass() == null
                || !"yv".equals(adapter.getSuperclass().getName())) {
                Log.w(TAG, "NATIVE_ROW version contract unavailable; Google UI retained");
                return;
            }
            java.lang.reflect.Method bind = adapter.getDeclaredMethod(
                "g", holder, int.class
            );
            if (bind.getReturnType() != void.class) {
                Log.w(TAG, "NATIVE_ROW incompatible bind signature; Google UI retained");
                return;
            }
            XposedBridge.hookMethod(bind, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam hook) {
                    if (hook.hasThrowable()
                        || GoogleMessagesAdapterBridge.isSnapshotBinding()
                        || hook.args.length != 2) return;
                    try {
                        MessagesInboxNativeRowBridge.onNativeBound(hook.args[0]);
                    } catch (Throwable failure) {
                        Log.e(TAG, "Native BBOS6 row failed; Google row retained", failure);
                    }
                }
            });
            Log.i(TAG, "NATIVE_ROW binding enabled: shared face in Google RecyclerView");
        } catch (Throwable failure) {
            Log.w(TAG, "NATIVE_ROW failed to install; Google UI retained", failure);
        }
    }

    private static boolean isInboxPaintEnabled() {
        return propertyEnabled(INBOX_PROP);
    }

    private static boolean isPageEnabled() {
        return propertyEnabled(PAGE_PROP);
    }

    private static boolean isActionProbeEnabled() {
        return propertyEnabled(ACTION_PROBE_PROP);
    }

    private static boolean propertyEnabled(String name) {
        try {
            Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object value = XposedHelpers.callStaticMethod(
                properties, "getBoolean", name, false
            );
            return Boolean.TRUE.equals(value);
        } catch (Throwable failure) {
            Log.w(TAG, "Cannot read inbox opt-in property; preserving native inbox", failure);
            return false;
        }
    }

    private static void hookInboxRows(ClassLoader loader) {
        try {
            Class<?> rowClass = XposedHelpers.findClassIfExists(ROW_CLASS, loader);
            if (rowClass == null) {
                Log.w(TAG, "Verified Google Messages row class not found; native inbox retained");
                return;
            }
            XposedBridge.hookAllConstructors(rowClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof ViewGroup)) return;
                    try {
                        MessagesInboxRowBridge.attachToRow((ViewGroup) param.thisObject);
                    } catch (Throwable failure) {
                        Log.e(TAG, "Row renderer failed; native row retained", failure);
                    }
                }
            });
            Log.i(TAG, "BBOS6 inbox row rendering enabled for verified native list");
        } catch (Throwable failure) {
            Log.e(TAG, "Unable to hook verified inbox row class; native list retained",
                failure);
        }
    }

    /**
     * Observe the original Google controller's Q/P calls only while the
     * shared list delegates to an attached native row. The hook neither
     * changes an argument nor calls any method. Do not log identifiers.
     */
    private static void hookNativeControllerOutcome(ClassLoader loader) {
        try {
            Class<?> controller = XposedHelpers.findClassIfExists("asvk", loader);
            Class<?> conversationId = XposedHelpers.findClassIfExists(
                "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId",
                loader
            );
            if (controller == null || conversationId == null) {
                Log.w(TAG, "ATTACHED_TRACE Google controller contract unavailable");
                return;
            }
            java.lang.reflect.Method open = controller.getDeclaredMethod(
                "Q", conversationId
            );
            if (open.getReturnType() != void.class
                || java.lang.reflect.Modifier.isStatic(open.getModifiers())) {
                Log.w(TAG, "ATTACHED_TRACE unsupported Q signature");
                return;
            }
            XposedBridge.hookMethod(open, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    MessagesNativeAttachedActionBridge.observeControllerAction(
                        false, !param.hasThrowable()
                    );
                }
            });
            boolean menuInstalled = false;
            Class<?> selection = XposedHelpers.findClassIfExists("eggp", loader);
            if (selection != null) {
                try {
                    java.lang.reflect.Method menu = controller.getDeclaredMethod(
                        "P", selection
                    );
                    if (menu.getReturnType() == void.class
                        && !java.lang.reflect.Modifier.isStatic(menu.getModifiers())) {
                        XposedBridge.hookMethod(menu, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                MessagesNativeAttachedActionBridge.observeControllerAction(
                                    true, !param.hasThrowable()
                                );
                            }
                        });
                        menuInstalled = true;
                    }
                } catch (NoSuchMethodException missing) {
                    // Do not guess a replacement obfuscated menu action.
                }
            }
            Log.i(TAG, "ATTACHED_TRACE Q installed P installed=" + menuInstalled);
        } catch (Throwable failure) {
            Log.w(TAG, "ATTACHED_TRACE unavailable="
                + failure.getClass().getSimpleName());
        }
    }

    private static void hookAdapterBindings(ClassLoader loader) {
        try {
            Class<?> adapter = XposedHelpers.findClassIfExists("vv", loader);
            if (adapter == null || adapter.getSuperclass() == null
                || !"yv".equals(adapter.getSuperclass().getName())) {
                Log.w(TAG, "Verified native adapter class unavailable; page stays native");
                return;
            }
            Class<?> holder = XposedHelpers.findClassIfExists("aac", loader);
            if (holder == null) {
                Log.w(TAG, "Verified native holder class unavailable; page stays native");
                return;
            }
            // Hook the exact verified vv.g(aac,int) binder only, not every
            // obfuscated method sharing the single-letter name "g".
            XposedHelpers.findAndHookMethod(adapter, "g", holder, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam hook) {
                        if (!hook.hasThrowable()
                            && !GoogleMessagesAdapterBridge.isSnapshotBinding()
                            && hook.args.length == 2
                            && hook.args[1] instanceof Integer) {
                            // Only Google's REAL adapter binds, never our
                            // detached presentation snapshots. Store weak
                            // row→position association for live native actions.
                            MessagesNativeAttachedActionBridge.observeNativeBind(
                                hook.args[0], ((Integer) hook.args[1]).intValue()
                            );
                            MessagesInboxPageBridge.onNativeAdapterBound();
                        }
                    }
                }
            );
            Log.i(TAG, "Watching native adapter bind callbacks for full-page refresh");
        } catch (Throwable failure) {
            Log.e(TAG, "Native adapter binding watcher unavailable", failure);
        }
    }

    private static void inspect(Activity activity) {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (isActionProbeEnabled()) {
                MessagesNativeActionInspection.inspect(activity);
                return;
            }
            View root = activity.getWindow().getDecorView();
            Log.i(TAG, "ACTIVITY " + activity.getClass().getName());
            int[] total = { 0 };
            inspectTree(root, 0, "root", total);
            Log.i(TAG, "END views=" + total[0]
                + (total[0] >= MAX_VIEWS ? " (truncated)" : ""));
            // Do not mount a second list over Google's RecyclerView.
            // Keep structural inspection only until direct native
            // navigation/controller bindings and pre-draw replacement
            // have been verified on Q25.
            MessagesNativeAdapterInspection.inspect(activity);
        } catch (Throwable failure) {
            Log.e(TAG, "Read-only view inspection failed", failure);
        }
    }

    private static void inspectTree(View view, int depth, String position, int[] total) {
        if (view == null || depth > MAX_DEPTH || total[0] >= MAX_VIEWS) {
            return;
        }
        total[0]++;

        // Only structural metadata is recorded. Never call getText().
        String resourceId = "-";
        if (view.getId() != View.NO_ID) {
            try {
                resourceId = view.getResources().getResourceName(view.getId());
            } catch (Throwable ignored) {
                resourceId = Integer.toHexString(view.getId());
            }
        }
        Log.i(TAG, position + " " + view.getClass().getName()
            + " id=" + resourceId
            + " visible=" + (view.getVisibility() == View.VISIBLE)
            + " focusable=" + view.isFocusable());

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), MAX_CHILDREN_PER_GROUP);
            for (int i = 0; i < count && total[0] < MAX_VIEWS; i++) {
                inspectTree(group.getChildAt(i), depth + 1,
                    position + "/" + i, total);
            }
        }
    }
}
