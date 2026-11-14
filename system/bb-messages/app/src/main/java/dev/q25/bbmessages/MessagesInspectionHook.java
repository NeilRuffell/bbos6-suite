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

                        // Let the view tree mount after Activity resume.
                        decor.postDelayed(() -> inspect(activity), 750L);
                    }
                }
            );
            Log.i(TAG, "Inspection enabled for main Google Messages process");
            if (isInboxPaintEnabled()) {
                hookInboxRows(param.classLoader);
            } else {
                Log.i(TAG, "BBOS6 inbox rendering disabled (set " + INBOX_PROP + "=true)");
            }
        } catch (Throwable failure) {
            Log.e(TAG, "Unable to install read-only inspection hook", failure);
        }
    }

    private static boolean isInboxPaintEnabled() {
        try {
            Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object value = XposedHelpers.callStaticMethod(
                properties, "getBoolean", INBOX_PROP, false
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

    private static void inspect(Activity activity) {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            View root = activity.getWindow().getDecorView();
            Log.i(TAG, "ACTIVITY " + activity.getClass().getName());
            int[] total = { 0 };
            inspectTree(root, 0, "root", total);
            Log.i(TAG, "END views=" + total[0]
                + (total[0] >= MAX_VIEWS ? " (truncated)" : ""));
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
