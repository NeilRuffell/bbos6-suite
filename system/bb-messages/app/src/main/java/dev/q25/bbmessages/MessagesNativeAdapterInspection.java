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
 * Logs Java class/method signatures and structural counts ONLY.
 * Never logs a conversation, contact, message, adapter element, object
 * toString(), or any message model/field value.
 *
 * This is required before replacing the native RecyclerView with the
 * existing shared Bbos6PageList controller: only the adapter can describe
 * off-screen conversations and preserve native actions.
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
            printRecyclerAdapterFields(recycler.getClass());
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
                // An integer count contains no message data.
                Log.i(TAG, "ADAPTER itemCount=" + count);
            } catch (Throwable failure) {
                Log.w(TAG, "Native getItemCount unavailable: "
                    + failure.getClass().getSimpleName());
            }

            try {
                Object stable = de.robv.android.xposed.XposedHelpers.callMethod(
                    adapter, "hasStableIds"
                );
                Log.i(TAG, "ADAPTER hasStableIds=" + stable);
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
                    Log.i(TAG, "ROW firstAdapterPosition=" + position);
                } catch (Throwable failure) {
                    Log.w(TAG, "Native getChildAdapterPosition unavailable: "
                        + failure.getClass().getSimpleName());
                }
            }
        } catch (Throwable failure) {
            Log.e(TAG, "Read-only native adapter inspection failed", failure);
        }
    }

    private static void printRecyclerAdapterFields(Class<?> recyclerType) {
        Class<?> type = recyclerType;
        int depth = 0;
        while (type != null && type != Object.class && depth++ < 3) {
            int emitted = 0;
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getName().contains("RecyclerView$")) continue;
                if (emitted++ >= 18) break;
                // Field types only. Never read or log private conversation models.
                Log.i(TAG, "RECYCLER_FIELD class=" + type.getName()
                    + " type=" + field.getType().getName()
                    + " name=" + field.getName());
            }
            type = type.getSuperclass();
        }
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
