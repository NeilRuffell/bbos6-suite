package dev.q25.bbmessages;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Read-only, version-specific Google Messages adapter inspection.
 *
 * Logs Java class/method signatures, structural counts, and native
 * adapter capability flags only. Never logs a conversation, contact,
 * message, adapter element, object toString(), or model/field value.
 *
 * A separately opted-in bounded bind probe may create/bind detached native
 * ViewHolders, but never clicks, replaces or scrolls the stock inbox.
 * This inspection precedes wiring the existing shared Bbos6PageList to
 * off-screen conversation data and native action dispatch.
 */
final class MessagesNativeAdapterInspection {
    private static final String TAG = "BBMessagesAdapter";
    private static final String RECYCLER =
        "android.support.v7.widget.RecyclerView";

    private MessagesNativeAdapterInspection() { }

    static void inspect(Activity activity) {
        try {
            View list = activity.findViewById(android.R.id.list);
            if (!(list instanceof ViewGroup)
                || !RECYCLER.equals(list.getClass().getName())) {
                Log.w(TAG, "Verified inbox RecyclerView not found; no page swap");
                return;
            }

            ViewGroup recycler = (ViewGroup) list;
            Log.i(TAG, "INBOX recycler=" + recycler.getClass().getName()
                + " parent=" + (recycler.getParent() == null
                    ? "none" : recycler.getParent().getClass().getName())
                + " attached=" + recycler.isAttachedToWindow()
                + " visibleNativeRows=" + recycler.getChildCount());

            // Prefer the exact Q25-verified obfuscated adapter signature.
            // The full-page shared helper will use the same bridge once its
            // data and native action bindings pass device tests.
            GoogleMessagesAdapterBridge nativeBridge =
                GoogleMessagesAdapterBridge.resolve(recycler);
            if (nativeBridge != null) {
                nativeBridge.inspectItemIdentity();
                if (isBoundRowProbeEnabled()) {
                    nativeBridge.inspectOffscreenBinding();
                } else {
                    Log.i(TAG, "BIND_PROBE disabled (opt-in system property)");
                }
                return;
            }

            // Fallback structural inspection for an unsupported Google build.
            Object adapter = null;
            try {
                // Introspection only; never invoke adapter binding or edits.
                adapter = de.robv.android.xposed.XposedHelpers.callMethod(
                    recycler, "getAdapter"
                );
            } catch (Throwable failure) {
                Log.w(TAG, "Native getAdapter unavailable: "
                    + failure.getClass().getSimpleName());
            }

            printRecyclerAdapterAccessors(recycler.getClass());
            printRecyclerAdapterFields(recycler);
            profileObfuscatedAdapterCandidates(recycler);
            if (adapter == null) {
                Log.w(TAG, "Adapter not identified; retain Google inbox");
                return;
            }

            Class<?> adapterType = adapter.getClass();
            Log.i(TAG, "ADAPTER class=" + adapterType.getName()
                + " superclass=" + (adapterType.getSuperclass() == null
                    ? "none" : adapterType.getSuperclass().getName()));

            try {
                Object count = de.robv.android.xposed.XposedHelpers.callMethod(
                    adapter, "getItemCount"
                );
                // Log only a numeric count, never an arbitrary object.
                if (count instanceof Number) {
                    Log.i(TAG, "ADAPTER itemCount=" + ((Number) count).intValue());
                } else {
                    Log.w(TAG, "ADAPTER getItemCount returned non-numeric result");
                }
            } catch (Throwable failure) {
                Log.w(TAG, "Native getItemCount unavailable: "
                    + failure.getClass().getSimpleName());
            }

            try {
                Object stable = de.robv.android.xposed.XposedHelpers.callMethod(
                    adapter, "hasStableIds"
                );
                if (stable instanceof Boolean) {
                    Log.i(TAG, "ADAPTER hasStableIds=" + stable);
                } else {
                    Log.w(TAG, "ADAPTER hasStableIds returned non-boolean result");
                }
            } catch (Throwable failure) {
                Log.w(TAG, "Native hasStableIds unavailable: "
                    + failure.getClass().getSimpleName());
            }

            // Report method signatures only. Obfuscated names may differ
            // between Google Messages versions, but type shapes are useful.
            Class<?> type = adapterType;
            int depth = 0;
            while (type != null && type != Object.class && depth++ < 5) {
                int emitted = 0;
                for (Method method : type.getDeclaredMethods()) {
                    if (!isAdapterMethodCandidate(method)) continue;
                    if (emitted++ >= 30) {
                        Log.i(TAG, "METHODS truncated class=" + type.getName());
                        break;
                    }
                    Log.i(TAG, "METHOD class=" + type.getName()
                        + " " + methodSignature(method));
                }
                type = type.getSuperclass();
            }

            if (recycler.getChildCount() > 0) {
                View first = recycler.getChildAt(0);
                Log.i(TAG, "ROW view=" + first.getClass().getName()
                    + " clickable=" + first.hasOnClickListeners()
                    + " longClickable=" + first.isLongClickable()
                    + " focusable=" + first.isFocusable());
                try {
                    Object position = de.robv.android.xposed.XposedHelpers.callMethod(
                        recycler, "getChildAdapterPosition", first
                    );
                    if (position instanceof Number) {
                        Log.i(TAG, "ROW firstAdapterPosition="
                            + ((Number) position).intValue());
                    } else {
                        Log.w(TAG, "ROW position returned non-numeric result");
                    }
                } catch (Throwable failure) {
                    Log.w(TAG, "Native getChildAdapterPosition unavailable: "
                        + failure.getClass().getSimpleName());
                }
            }
        } catch (Throwable failure) {
            Log.e(TAG, "Read-only native adapter inspection failed", failure);
        }
    }

    private static boolean isBoundRowProbeEnabled() {
        try {
            Class<?> properties = de.robv.android.xposed.XposedHelpers.findClass(
                "android.os.SystemProperties", null
            );
            Object enabled = de.robv.android.xposed.XposedHelpers.callStaticMethod(
                properties, "getBoolean", "debug.bbmessages.bindprobe.enabled", false
            );
            return Boolean.TRUE.equals(enabled);
        } catch (Throwable failure) {
            return false;
        }
    }

    private static void printRecyclerAdapterFields(ViewGroup recycler) {
        Class<?> recyclerType = recycler.getClass();
        Class<?> type = recyclerType;
        int depth = 0;
        // ProGuard/R8 may rename both adapter accessors AND adapter field
        // types. Inspect each instance field's declared/runtime *class*;
        // never print field values or invoke message/data access methods.
        while (type != null && type != Object.class && depth++ < 2) {
            if (!type.getName().startsWith("android.support.v7.widget.RecyclerView")) {
                break; // avoid traversal of Android framework View fields
            }

            int emitted = 0;
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())
                    || field.getType().isPrimitive()) continue;
                if (emitted++ >= 130) {
                    Log.i(TAG, "FIELDS truncated class=" + type.getName());
                    break;
                }
                // Reading an object reference only for class identification
                // does not read message data or adapter elements.
                String runtimeType = "unavailable";
                try {
                    field.setAccessible(true);
                    Object value = field.get(recycler);
                    runtimeType = value == null ? "null" : value.getClass().getName();
                } catch (Throwable failure) {
                    runtimeType = "inaccessible:" + failure.getClass().getSimpleName();
                }
                Log.i(TAG, "FIELD class=" + type.getName()
                    + " name=" + field.getName()
                    + " declared=" + field.getType().getName()
                    + " runtime=" + runtimeType);
            }
            Log.i(TAG, "FIELDS inspectedClass=" + type.getName()
                + " declaredCount=" + fields.length);
            type = type.getSuperclass();
        }
    }

    /**
     * Since this Google Messages build obfuscates RecyclerView's adapter API,
     * identify likely adapter types by their superclass and method signatures.
     * Never invoke any candidate methods: some obfuscated no-arg functions can
     * mutate adapter state. This instrumentation is strictly structural.
     */
    private static void profileObfuscatedAdapterCandidates(ViewGroup recycler) {
        Class<?> recyclerClass = recycler.getClass();
        int profiles = 0;
        for (Field field : recyclerClass.getDeclaredFields()) {
            Class<?> declared = field.getType();
            if (Modifier.isStatic(field.getModifiers()) || declared.isPrimitive()
                || declared.isArray() || declared.getName().startsWith("java.")
                || declared.getName().startsWith("android.")) {
                continue;
            }

            Object instance;
            try {
                field.setAccessible(true);
                instance = field.get(recycler);
            } catch (Throwable failure) {
                continue;
            }
            if (instance == null) continue;
            if (profiles++ >= 24) {
                Log.i(TAG, "CANDIDATES truncated after 24 active native fields");
                break;
            }

            Class<?> runtimeType = instance.getClass();
            StringBuilder hierarchy = new StringBuilder();
            Class<?> parent = runtimeType;
            for (int depth = 0; parent != null && depth < 6; depth++) {
                if (depth != 0) hierarchy.append(" > ");
                hierarchy.append(parent.getName());
                parent = parent.getSuperclass();
            }

            Log.i(TAG, "CANDIDATE field=" + field.getName()
                + " declared=" + declared.getName()
                + " runtime=" + runtimeType.getName()
                + " hierarchy=" + hierarchy);

            // List only signatures suggestive of adapter count/IDs or
            // ViewHolder creation/binding; no method or field values.
            int signatures = 0;
            Class<?> type = runtimeType;
            for (int depth = 0; type != null && type != Object.class && depth < 4;
                 depth++, type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!isAdapterShape(method)) continue;
                    if (signatures++ >= 14) break;
                    Log.i(TAG, "CANDIDATE_METHOD field=" + field.getName()
                        + " class=" + type.getName()
                        + " " + methodSignature(method));
                }
                if (signatures >= 14) {
                    Log.i(TAG, "CANDIDATE_METHODS field=" + field.getName()
                        + " truncated=14");
                    break;
                }
            }
        }
    }

    private static boolean isAdapterShape(Method method) {
        Class<?>[] params = method.getParameterTypes();
        Class<?> result = method.getReturnType();
        // Adapter implementations generally expose a zero-arg integer item
        // count, an int -> long stable ID, and methods involving a holder
        // plus an integer position, often with all names minified.
        if (params.length == 0
            && (result == int.class || result == long.class)) return true;
        if (params.length == 1 && params[0] == int.class
            && (result == int.class || result == long.class)) return true;
        if (params.length == 2 && params[1] == int.class
            && !params[0].isPrimitive()) return true;
        if (params.length == 2 && params[0] == ViewGroup.class
            && params[1] == int.class) return true;
        return isAdapterMethodCandidate(method);
    }

    private static void printRecyclerAdapterAccessors(Class<?> recyclerType) {
        Class<?> type = recyclerType;
        int depth = 0;
        while (type != null && type != Object.class && depth++ < 3) {
            int emitted = 0;
            for (Method method : type.getDeclaredMethods()) {
                Class<?> resultType = method.getReturnType();
                // Include potential minified getAdapter accessors by type.
                boolean returnsAdapter = resultType.getName().contains(
                    "RecyclerView$"
                );
                boolean relevantName = method.getName().equals("getAdapter")
                    || method.getName().equals("getChildAdapterPosition")
                    || method.getName().equals("findViewHolderForAdapterPosition")
                    || method.getName().equals("scrollToPosition");
                if (!returnsAdapter && !relevantName) continue;
                if (emitted++ >= 24) break;
                Log.i(TAG, "RECYCLER_METHOD class=" + type.getName()
                    + " " + methodSignature(method));
            }
            type = type.getSuperclass();
        }
    }

    private static boolean isAdapterMethodCandidate(Method method) {
        Class<?>[] params = method.getParameterTypes();
        String name = method.getName();
        if (name.equals("getItemCount") || name.equals("getItemId")
            || name.equals("getItemViewType") || name.equals("onBindViewHolder")
            || name.equals("onCreateViewHolder")) return true;
        if (params.length == 0 &&
            (method.getReturnType() == int.class ||
             method.getReturnType() == long.class)) return true;
        if (params.length >= 1 && params.length <= 3) {
            for (Class<?> param : params) {
                String typeName = param.getName();
                if (typeName.contains("RecyclerView$")
                    || typeName.equals("android.view.ViewGroup")) return true;
            }
        }
        return false;
    }

    private static String methodSignature(Method method) {
        StringBuilder out = new StringBuilder();
        out.append(Modifier.toString(method.getModifiers()))
            .append(" ").append(method.getReturnType().getName())
            .append(" ").append(method.getName()).append("(");
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i != 0) out.append(", ");
            out.append(params[i].getName());
        }
        return out.append(")").toString();
    }
}
