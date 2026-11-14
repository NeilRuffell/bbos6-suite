package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

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
    private static final int MAX_HOOKED_METHODS_PER_CLASS = 80;
    private static final int MAX_EVENTS_PER_ACTION = 24;
    private static final ThreadLocal<ActionScope> ACTIVE_ACTION = new ThreadLocal<>();

    private static final class ActionScope {
        final String kind;
        final Object nativeDelegate;
        int events;
        int depth = 1;

        ActionScope(String kind, Object nativeDelegate) {
            this.kind = kind;
            this.nativeDelegate = nativeDelegate;
        }
    }

    private MessagesNativeActionTrace() { }

    static void install(ClassLoader loader) {
        // Verified on Q25 by listener introspection. Exact class/method
        // signatures are checked at runtime before each hook is installed.
        hook(loader, "fzih", "onClick", false);
        hook(loader, "asvb", "onClick", false);
        hook(loader, "fzig", "onLongClick", true);
        hook(loader, "asvc", "onLongClick", true);
        // asvb/asvc were observed holding asvk (controller candidate) and
        // asvj (native holder). Capture the method call path reached during
        // a genuine user click, never invoke methods speculatively.
        traceControllerClass(loader, "asvk");
        traceControllerClass(loader, "asvj");
    }

    private static void startAction(String name, Object nativeDelegate) {
        ActionScope state = ACTIVE_ACTION.get();
        if (state == null) ACTIVE_ACTION.set(new ActionScope(name, nativeDelegate));
        else state.depth++;
    }

    private static void finishAction() {
        ActionScope state = ACTIVE_ACTION.get();
        if (state == null) return;
        if (--state.depth == 0) {
            Log.i(TAG, "TRACE_PATH end=" + state.kind
                + " controllerCalls=" + state.events);
            ACTIVE_ACTION.remove();
        }
    }

    /**
     * Hooks are pure observers. Only calls made synchronously within an
     * actual native asvb/asvc listener are logged; no arguments, object
     * values, messages or conversation IDs are inspected.
     */
    private static void traceControllerClass(ClassLoader loader, String name) {
        try {
            Class<?> target = XposedHelpers.findClassIfExists(name, loader);
            if (target == null) {
                Log.w(TAG, "TRACE_PATH unavailable=" + name);
                return;
            }
            Method[] declared = target.getDeclaredMethods();
            Arrays.sort(declared, Comparator.comparing(Method::toGenericString));
            if (declared.length > MAX_HOOKED_METHODS_PER_CLASS) {
                Log.w(TAG, "TRACE_PATH class=" + name
                    + " exceeds hook cap=" + MAX_HOOKED_METHODS_PER_CLASS
                    + "; no controller hooks installed");
                return;
            }
            int hooks = 0;
            for (Method method : declared) {
                // Only the signatures of methods observed in real Q25
                // actions are useful. Never read argument values or inspect
                // message/contact fields.
                if ("asvk".equals(name) && isObservedControllerMethod(method)) {
                    Log.i(TAG, "TRACE_SIGNATURE method=" + name + "."
                        + method.getName()
                        + " static=" + Modifier.isStatic(method.getModifiers())
                        + " parameterTypes=" + parameterTypeNames(method)
                        + " returnType=" + method.getReturnType().getName());
                }
                if (Modifier.isAbstract(method.getModifiers())
                    || Modifier.isNative(method.getModifiers())
                    || method.isSynthetic()) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam hook) {
                            ActionScope state = ACTIVE_ACTION.get();
                            if (state == null || state.events >= MAX_EVENTS_PER_ACTION) return;
                            state.events++;
                            Log.i(TAG, "TRACE_PATH action=" + state.kind
                                + " call=" + name + "." + method.getName()
                                + " params=" + method.getParameterCount()
                                + " return=" + method.getReturnType().getName());
                            // For Q/P only, report the runtime CLASSES of
                            // argument objects passed by Google itself.
                            // Do not print toString(), object identity,
                            // IDs, or any model payload.
                            if ("asvk".equals(name)
                                && ("Q".equals(method.getName())
                                    || "P".equals(method.getName()))) {
                                Log.i(TAG, "TRACE_ARGTYPE action=" + state.kind
                                    + " method=" + name + "." + method.getName()
                                    + " argClasses=" + argumentTypeNames(hook.args));
                                if ("Q".equals(method.getName())
                                    && "asvb.onClick".equals(state.kind)
                                    && hook.args != null && hook.args.length == 1) {
                                    MessagesNativeConversationIdentity.compareRealOpen(
                                        state.nativeDelegate, hook.args[0]
                                    );
                                }
                            }
                        }
                    });
                    hooks++;
                } catch (Throwable failure) {
                    Log.w(TAG, "TRACE_PATH hook unavailable class=" + name
                        + " method=" + method.getName()
                        + " error=" + failure.getClass().getSimpleName());
                }
            }
            Log.i(TAG, "TRACE_PATH installedClass=" + name
                + " hooks=" + hooks + " declared=" + declared.length);
        } catch (Throwable failure) {
            Log.w(TAG, "TRACE_PATH class unavailable=" + name
                + " error=" + failure.getClass().getSimpleName());
        }
    }

    private static boolean isObservedControllerMethod(Method method) {
        switch (method.getName()) {
            case "L":
            case "I":
            case "Q":
            case "G":
            case "P":
                return true;
            default:
                return false;
        }
    }

    private static String parameterTypeNames(Method method) {
        Class<?>[] types = method.getParameterTypes();
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < types.length; i++) {
            if (i != 0) result.append(",");
            result.append(types[i].getName());
        }
        return result.append("]").toString();
    }

    private static String argumentTypeNames(Object[] values) {
        if (values == null) return "[]";
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i != 0) result.append(",");
            result.append(values[i] == null
                ? "null" : values[i].getClass().getName());
        }
        return result.append("]").toString();
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
                    if ("asvb".equals(className) || "asvc".equals(className)) {
                        startAction(className + "." + methodName, hook.thisObject);
                    }
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
                    if ("asvb".equals(className) || "asvc".equals(className)) {
                        finishAction();
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
