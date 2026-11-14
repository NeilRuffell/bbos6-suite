package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Google Messages-specific adapter access; NOT a list or scrolling helper.
 *
 * The Q25 build uses an R8-obfuscated RecyclerView. Its adapter sits in
 * RecyclerView.n and has runtime class vv extending yv. The abstract native
 * adapter contract on yv is verified by inspecting method signatures, not
 * by assuming AndroidX/RecyclerView public Java method names survive R8.
 *
 * This bridge is read-only unless explicit native row binding is enabled for
 * a bounded diagnostic. No data is persisted or logged. Do not mount a
 * Bbos6PageList from only the native RecyclerView's visible children.
 */
final class GoogleMessagesAdapterBridge {
    private static final String TAG = "BBMessagesAdapter";
    private static final String NATIVE_RECYCLER =
        "android.support.v7.widget.RecyclerView";
    private static final String TARGET_PACKAGE =
        "com.google.android.apps.messaging";

    private final ViewGroup recycler;
    private final Object adapter;
    private final Method countMethod;
    private final Method itemTypeMethod;
    private final Method itemIdMethod;
    private final Method createHolderMethod;
    private final Method bindHolderMethod;

    private GoogleMessagesAdapterBridge(
        ViewGroup recycler, Object adapter, Method countMethod,
        Method itemTypeMethod, Method itemIdMethod, Method createHolderMethod,
        Method bindHolderMethod
    ) {
        this.recycler = recycler;
        this.adapter = adapter;
        this.countMethod = countMethod;
        this.itemTypeMethod = itemTypeMethod;
        this.itemIdMethod = itemIdMethod;
        this.createHolderMethod = createHolderMethod;
        this.bindHolderMethod = bindHolderMethod;
    }

    /**
     * Fail closed when Google changes its obfuscated RecyclerView API.
     * This identifies only the adapter, never a message/conversation object.
     */
    static GoogleMessagesAdapterBridge resolve(ViewGroup recycler) {
        try {
            if (!NATIVE_RECYCLER.equals(recycler.getClass().getName())) return null;
            Field field = recycler.getClass().getDeclaredField("n");
            if (!"yv".equals(field.getType().getName())) return null;
            field.setAccessible(true);
            Object adapter = field.get(recycler);
            if (adapter == null || !"vv".equals(adapter.getClass().getName())) {
                return null;
            }
            if (!"yv".equals(adapter.getClass().getSuperclass().getName())) {
                return null;
            }

            Class<?> base = adapter.getClass().getSuperclass();
            Method count = base.getDeclaredMethod("a");
            Method itemType = base.getDeclaredMethod("dR", int.class);
            Method itemId = base.getDeclaredMethod("hG", int.class);
            Method create = findMethod(base, "n", 2);
            Method bind = findMethod(base, "o", 2);
            if (create == null || bind == null
                || count.getReturnType() != int.class
                || itemType.getReturnType() != int.class
                || itemId.getReturnType() != long.class
                || create.getParameterTypes()[0] != ViewGroup.class
                || create.getParameterTypes()[1] != int.class
                || bind.getParameterTypes()[1] != int.class
                || bind.getReturnType() != void.class
                || !create.getReturnType().isAssignableFrom(
                    bind.getParameterTypes()[0])) {
                return null;
            }
            for (Method method : new Method[] {count, itemType, itemId, create, bind}) {
                method.setAccessible(true);
            }
            return new GoogleMessagesAdapterBridge(
                recycler, adapter, count, itemType, itemId, create, bind
            );
        } catch (Throwable failure) {
            Log.w(TAG, "Version-checked native adapter access unavailable: "
                + failure.getClass().getSimpleName());
            return null;
        }
    }

    int itemCount() throws ReflectiveOperationException {
        Object value = countMethod.invoke(adapter);
        if (!(value instanceof Integer)) throw new IllegalStateException("Invalid item count");
        int count = (Integer) value;
        if (count < 0) throw new IllegalStateException("Negative native item count");
        return count;
    }

    /**
     * Checks whether the native adapter claims usable, distinct item IDs.
     * The result is diagnostic, not proof that IDs remain stable across updates.
     */
    void inspectItemIdentity() {
        try {
            int count = itemCount();
            Log.i(TAG, "RESOLVED adapter=" + adapter.getClass().getName()
                + " count=" + count);
            if (count == 0) return;
            long firstId = ((Number) itemIdMethod.invoke(adapter, 0)).longValue();
            boolean firstValid = firstId != -1L;
            boolean lastDistinct = true;
            if (count > 1) {
                long lastId = ((Number) itemIdMethod.invoke(adapter, count - 1)).longValue();
                lastDistinct = firstId != lastId;
            }
            Object firstType = itemTypeMethod.invoke(adapter, 0);
            Log.i(TAG, "IDENTITY firstIdPresent=" + firstValid
                + " firstAndLastDistinct=" + lastDistinct
                + " firstItemTypeNumeric=" + (firstType instanceof Number)
                + " totalCount=" + count);
            // Intentionally never log the IDs or the type values.
        } catch (Throwable failure) {
            Log.w(TAG, "Native adapter identity inspection failed: "
                + failure.getClass().getSimpleName());
        }
    }

    /**
     * Opt-in-only diagnostic: create/bind at most two detached Google-owned
     * holders using the native adapter wrapper methods. It does NOT click,
     * insert, scroll or alter the existing inbox. Only logs structural flags.
     */
    void inspectOffscreenBinding() {
        try {
            int count = itemCount();
            if (count == 0) {
                Log.i(TAG, "BIND_PROBE skipped: native adapter empty");
                return;
            }
            probePosition(0, "first");
            if (count > 1) probePosition(count - 1, "last");
        } catch (Throwable failure) {
            Log.w(TAG, "BIND_PROBE failed: "
                + failure.getClass().getSimpleName());
        }
    }

    private void probePosition(int index, String label) {
        try {
            Object type = itemTypeMethod.invoke(adapter, index);
            if (!(type instanceof Integer)) {
                Log.w(TAG, "BIND_PROBE " + label + " invalid native row type");
                return;
            }
            Object holder = createHolderMethod.invoke(adapter, recycler, (Integer) type);
            if (holder == null) {
                Log.w(TAG, "BIND_PROBE " + label + " null native ViewHolder");
                return;
            }
            // Calls Google's normal final ViewHolder bind wrapper. No direct
            // access to its internal message records or conversation models.
            bindHolderMethod.invoke(adapter, holder, index);
            int senderId = recycler.getResources().getIdentifier(
                "conversation_name", "id", TARGET_PACKAGE
            );
            int snippetId = recycler.getResources().getIdentifier(
                "conversation_snippet", "id", TARGET_PACKAGE
            );
            View nativeRow = holderItemView(holder, senderId);
            if (nativeRow == null) {
                Log.w(TAG, "BIND_PROBE " + label + " holder has no row View");
                return;
            }
            boolean sender = senderId != 0
                && nativeRow.findViewById(senderId) instanceof TextView;
            boolean snippet = snippetId != 0
                && nativeRow.findViewById(snippetId) instanceof TextView;
            Log.i(TAG, "BIND_PROBE " + label
                + " rowClass=" + nativeRow.getClass().getName()
                + " senderField=" + sender + " snippetField=" + snippet
                + " clickListener=" + nativeRow.hasOnClickListeners()
                + " longClickable=" + nativeRow.isLongClickable()
                + " attached=" + nativeRow.isAttachedToWindow());
        } catch (Throwable failure) {
            Throwable actual = failure.getCause() == null
                ? failure : failure.getCause();
            Log.w(TAG, "BIND_PROBE " + label + " unavailable: "
                + actual.getClass().getSimpleName());
        }
    }

    private static View holderItemView(Object holder, int senderId) {
        Class<?> type = holder.getClass();
        View fallback = null;
        for (int depth = 0; type != null && type != Object.class
             && depth < 6; depth++, type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!View.class.isAssignableFrom(field.getType())
                    || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value instanceof View) {
                        View candidate = (View) value;
                        if (senderId != 0 &&
                            candidate.findViewById(senderId) instanceof TextView) {
                            return candidate;
                        }
                        if (fallback == null) fallback = candidate;
                    }
                } catch (Throwable ignored) { }
            }
        }
        return fallback;
    }

    private static Method findMethod(Class<?> type, String name, int params) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)
                && method.getParameterTypes().length == params) {
                return method;
            }
        }
        return null;
    }
}
