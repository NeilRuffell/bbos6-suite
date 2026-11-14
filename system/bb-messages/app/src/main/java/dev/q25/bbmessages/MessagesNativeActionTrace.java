package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Read-only, Q25-version-specific trace of the *actual* attached inbox click
 * path. No synthetic click is dispatched, no hook changes arguments/results,
 * and no conversations, contacts, IDs, or message data are logged.
 *
 * Trigger with a physical tap on a stock Google Messages inbox row.
 */
final class MessagesNativeActionTrace {
    private static final String TAG = "BBMessagesActions";

    private MessagesNativeActionTrace() { }

    static void install(ClassLoader loader) {
        // Verified on Q25 by listener introspection. Exact class/method
        // signatures are checked at runtime before each hook is installed.
        hook(loader, "fzih", "onClick", false);
        hook(loader, "asvb", "onClick", false);
        hook(loader, "fzig", "onLongClick", true);
        hook(loader, "asvc", "onLongClick", true);
    }

    private static void hook(
        ClassLoader loader, String className, String methodName,
        boolean longClick
    ) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, loader);
            if (clazz == null) {
                Log.w(TAG, "TRACE unavailable class=" + className);
                return;
            }
            Method method = clazz.getDeclaredMethod(methodName, View.class);
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                method.setAccessible(true);
            }
            if (longClick && method.getReturnType() != boolean.class) {
                Log.w(TAG, "TRACE unsupported signature class=" + className);
                return;
            }
            if (!longClick && method.getReturnType() != void.class) {
                Log.w(TAG, "TRACE unsupported signature class=" + className);
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam hook) {
                    boolean attached = hook.args.length == 1
                        && hook.args[0] instanceof View
                        && ((View) hook.args[0]).isAttachedToWindow();
                    Log.i(TAG, "TRACE enter=" + className + "." + methodName
                        + " nativeViewAttached=" + attached);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam hook) {
                    if (hook.hasThrowable()) {
                        Log.w(TAG, "TRACE exception=" + className + "." + methodName
                            + " class=" + hook.getThrowable().getClass().getSimpleName());
                    } else if (longClick) {
                        Log.i(TAG, "TRACE exit=" + className + "." + methodName
                            + " handled=" + Boolean.TRUE.equals(hook.getResult()));
                    } else {
                        Log.i(TAG, "TRACE exit=" + className + "." + methodName);
                    }
                }
            });
            Log.i(TAG, "TRACE installed=" + className + "." + methodName);
        } catch (NoSuchMethodException failure) {
            Log.w(TAG, "TRACE method unavailable class=" + className
                + " method=" + methodName);
        } catch (Throwable failure) {
            Log.w(TAG, "TRACE install failed class=" + className
                + " error=" + failure.getClass().getSimpleName());
        }
    }
}
