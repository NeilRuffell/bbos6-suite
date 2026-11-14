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

    // The adapter bind hook must ignore binds initiated by our own
    // full-page snapshot to avoid recursive UI refreshes.
    private static final ThreadLocal<Boolean> BRIDGE_BIND = new ThreadLocal<>();

    static boolean isSnapshotBinding() {
        return Boolean.TRUE.equals(BRIDGE_BIND.get());
    }

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


    /**
     * Complete native adapter snapshot for the existing Bbos6PageList.
     * Fail closed for unsupported row types or partial/inconsistent datasets.
     *
     * IDs are deliberately snapshot-position keys, NOT Google conversation IDs.
     * The Compose host resets its selection on dataset changes because this
     * Google build's hG(int) returns no stable IDs.
     */
    java.util.List<NativeConversationRow> snapshot() throws Exception {
        BRIDGE_BIND.set(true);
        try {
            return snapshotBoundRows();
        } finally {
            BRIDGE_BIND.remove();
        }
    }

    private java.util.List<NativeConversationRow> snapshotBoundRows() throws Exception {
        if (!isCurrent()) throw new IllegalStateException("Adapter changed");
        int count = itemCount();
        if (count > 1000) throw new IllegalStateException("Inbox exceeds safe snapshot cap");
        int nameId = recycler.getResources().getIdentifier(
            "conversation_name", "id", TARGET_PACKAGE
        );
        int snippetId = recycler.getResources().getIdentifier(
            "conversation_snippet", "id", TARGET_PACKAGE
        );
        int timestampId = recycler.getResources().getIdentifier(
            "conversation_timestamp_and_unread_badge", "id", TARGET_PACKAGE
        );
        if (nameId == 0 || snippetId == 0) {
            throw new IllegalStateException("Native conversation field IDs missing");
        }
        java.util.ArrayList<NativeConversationRow> rows =
            new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Object type = itemTypeMethod.invoke(adapter, index);
            if (!(type instanceof Integer)) {
                throw new IllegalStateException("Invalid native view type");
            }
            Object holder = createHolderMethod.invoke(adapter, recycler, (Integer) type);
            if (holder == null) throw new IllegalStateException("No native ViewHolder");
            bindHolderMethod.invoke(adapter, holder, index);
            View view = holderItemView(holder, nameId);
            if (view == null || !view.getClass().getName().equals(
                    "com.google.android.apps.messaging.home.list.ConversationListItemView")) {
                throw new IllegalStateException(
                    "Unexpected native row class at adapter index " + index
                );
            }
            View senderView = view.findViewById(nameId);
            View snippetView = view.findViewById(snippetId);
            if (!(senderView instanceof TextView) ||
                !(snippetView instanceof TextView) ||
                !view.hasOnClickListeners() || !view.isLongClickable()) {
                throw new IllegalStateException("Native row/action binding incomplete");
            }
            String sender = ((TextView) senderView).getText().toString();
            String preview = ((TextView) snippetView).getText().toString();
            if (sender.trim().isEmpty()) {
                throw new IllegalStateException("Empty conversation title");
            }
            String timestamp = null;
            if (timestampId != 0) {
                View container = view.findViewById(timestampId);
                TextView value = findTextDescendant(container);
                if (value != null) timestamp = value.getText().toString();
            }
            rows.add(new NativeConversationRow(index, sender, preview,
                timestamp, view, holder));
        }
        if (!isCurrent() || count != itemCount()) {
            throw new IllegalStateException("Adapter updated during snapshot");
        }
        return rows;
    }


    /**
     * Diagnoses all native row kinds when the strict all-conversation
     * snapshot is rejected. An adapter entry may be a date separator, banner,
     * announcement or other non-conversation UI. Never silently omit it.
     *
     * Logs only adapter index, row class, ViewHolder class and presence of
     * verified text/action controls. It does not log raw view type IDs,
     * conversation data, text contents or handler targets.
     */
    void inspectRowKinds() {
        if (!isCurrent()) return;
        BRIDGE_BIND.set(true);
        try {
            int count = itemCount();
            Log.i(TAG, "ROW_KINDS totalNativeItems=" + count);
            if (count > 64) {
                Log.w(TAG, "ROW_KINDS item count too large; no page replacement");
                return;
            }
            int nameId = recycler.getResources().getIdentifier(
                "conversation_name", "id", TARGET_PACKAGE
            );
            int snippetId = recycler.getResources().getIdentifier(
                "conversation_snippet", "id", TARGET_PACKAGE
            );
            int conversationRows = 0;
            for (int position = 0; position < count; position++) {
                try {
                    Object kind = itemTypeMethod.invoke(adapter, position);
                    if (!(kind instanceof Integer)) {
                        Log.w(TAG, "ROW_KIND index=" + position
                            + " invalid native type; preserving stock inbox");
                        continue;
                    }
                    Object holder = createHolderMethod.invoke(
                        adapter, recycler, (Integer) kind
                    );
                    if (holder == null) {
                        Log.w(TAG, "ROW_KIND index=" + position
                            + " holder=null; preserving stock inbox");
                        continue;
                    }
                    bindHolderMethod.invoke(adapter, holder, position);
                    View row = holderItemView(holder, nameId);
                    if (row == null) {
                        Log.w(TAG, "ROW_KIND index=" + position
                            + " row=null holderClass=" + holder.getClass().getName());
                        continue;
                    }
                    boolean sender = nameId != 0
                        && row.findViewById(nameId) instanceof TextView;
                    boolean snippet = snippetId != 0
                        && row.findViewById(snippetId) instanceof TextView;
                    boolean conversation = row.getClass().getName().equals(
                        "com.google.android.apps.messaging.home.list.ConversationListItemView"
                    );
                    if (conversation) conversationRows++;
                    Log.i(TAG, "ROW_KIND index=" + position
                        + " rowClass=" + row.getClass().getName()
                        + " holderClass=" + holder.getClass().getName()
                        + " conversation=" + conversation
                        + " senderField=" + sender
                        + " snippetField=" + snippet
                        + " clickable=" + row.hasOnClickListeners()
                        + " longClickable=" + row.isLongClickable());
                } catch (Throwable failure) {
                    Throwable cause = failure.getCause() == null
                        ? failure : failure.getCause();
                    Log.w(TAG, "ROW_KIND index=" + position + " failed="
                        + cause.getClass().getSimpleName());
                }
            }
            Log.i(TAG, "ROW_KINDS conversationRows=" + conversationRows
                + " otherRows=" + (count - conversationRows)
                + " (not hidden, stock inbox retained)");
        } catch (Throwable failure) {
            Log.w(TAG, "ROW_KINDS inspection failed: "
                + failure.getClass().getSimpleName());
        } finally {
            BRIDGE_BIND.remove();
        }
    }

    private boolean isCurrent() {
        try {
            Field field = recycler.getClass().getDeclaredField("n");
            field.setAccessible(true);
            return field.get(recycler) == adapter;
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Native click listeners can act on a detached bound ViewHolder, as a
     * property of Google's implementation, but functional success still
     * requires actual Q25 navigation testing. No homegrown conversation
     * intents or synthetic message IDs are used.
     */
    boolean activate(NativeConversationRow selected, boolean longPress) {
        try {
            if (!isCurrent() || selected == null ||
                selected.index < 0 || selected.index >= itemCount()) return false;
            Object type = itemTypeMethod.invoke(adapter, selected.index);
            if (!(type instanceof Integer)) return false;
            Object holder = createHolderMethod.invoke(adapter, recycler, (Integer) type);
            if (holder == null) return false;
            BRIDGE_BIND.set(true);
            try {
                bindHolderMethod.invoke(adapter, holder, selected.index);
            } finally {
                BRIDGE_BIND.remove();
            }
            int nameId = recycler.getResources().getIdentifier(
                "conversation_name", "id", TARGET_PACKAGE
            );
            View nativeRow = holderItemView(holder, nameId);
            if (nativeRow == null || !nativeRow.hasOnClickListeners()) return false;
            return longPress ? nativeRow.performLongClick() : nativeRow.performClick();
        } catch (Throwable failure) {
            Log.e(TAG, "Native conversation action failed; no synthetic fallback", failure);
            return false;
        }
    }

    static final class NativeConversationRow {
        final int index;
        final String sender;
        final String preview;
        final String timestamp;
        // Keep the native holder alive for this snapshot's bound action data.
        @SuppressWarnings("unused") final View nativeRow;
        @SuppressWarnings("unused") final Object holder;

        NativeConversationRow(
            int index, String sender, String preview, String timestamp,
            View nativeRow, Object holder
        ) {
            this.index = index;
            this.sender = sender;
            this.preview = preview;
            this.timestamp = timestamp;
            this.nativeRow = nativeRow;
            this.holder = holder;
        }

        public String getId() { return "position:" + index; }
        public String getSender() { return sender; }
        public String getPreview() { return preview; }
        public String getTimestamp() { return timestamp; }
    }

    private static TextView findTextDescendant(View view) {
        if (view instanceof TextView) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                TextView result = findTextDescendant(parent.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
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
