package dev.q25.bbmessages;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Read-only examination of the Google-owned inbox action contract.
 *
 * The previous full-page experiment invokes performClick() on detached
 * ViewHolders. Q25 testing confirmed that it does not open conversations.
 * Before replacing Google's UI at its creation point, we must locate the
 * Google controller that performs navigation, and validate how the native
 * inbox invokes it. We do not click, launch, send, modify or log message data.
 *
 * Enable ONLY for a stock inbox:
 * debug.bbmessages.actionprobe.enabled=true
 */
final class MessagesNativeActionInspection {
    private static final String TAG = "BBMessagesActions";
    private static final String CONVERSATION_ROW =
        "com.google.android.apps.messaging.home.list.ConversationListItemView";
    private static final int MAX_ATTACHED = 5;
    private static final int MAX_CAPTURE_FIELDS = 16;

    private MessagesNativeActionInspection() { }

    static void inspect(Activity activity) {
        try {
            View list = activity.findViewById(android.R.id.list);
            if (!(list instanceof ViewGroup)
                || !"android.support.v7.widget.RecyclerView"
                    .equals(list.getClass().getName())) {
                Log.w(TAG, "Verified native inbox unavailable");
                return;
            }
            ViewGroup recycler = (ViewGroup) list;
            GoogleMessagesAdapterBridge bridge =
                GoogleMessagesAdapterBridge.resolve(recycler);
            if (bridge == null) {
                Log.w(TAG, "Verified native adapter unavailable");
                return;
            }
            Log.i(TAG, "NATIVE inboxAttached=" + recycler.isAttachedToWindow()
                + " visibleChildCount=" + recycler.getChildCount()
                + " adapterItemCount=" + bridge.itemCount());

            int attached = 0;
            for (int i = 0; i < recycler.getChildCount()
                 && attached < MAX_ATTACHED; i++) {
                View row = findConversationRow(recycler.getChildAt(i), 0);
                if (row == null) continue;
                describe(row, "attached", attached++);
            }
            bridge.inspectDetachedActionShape();
            // A class name alone does not establish native controller access.
            // This phase only verifies what callback objects exist.
        } catch (Throwable failure) {
            Log.w(TAG, "Read-only action introspection unavailable: "
                + failure.getClass().getSimpleName());
        }
    }

    static void describe(View row, String source, int ordinal) {
        Log.i(TAG, "ACTION source=" + source + " ordinal=" + ordinal
            + " rowClass=" + row.getClass().getName()
            + " attached=" + row.isAttachedToWindow()
            + " click=" + row.hasOnClickListeners()
            + " longClick=" + row.isLongClickable());
        Object info;
        try {
            info = de.robv.android.xposed.XposedHelpers.getObjectField(
                row, "mListenerInfo"
            );
        } catch (Throwable failure) {
            Log.w(TAG, "ACTION listenerInfo unavailable: "
                + failure.getClass().getSimpleName());
            return;
        }
        if (info == null) return;
        describeHandler(info, "mOnClickListener", source, ordinal);
        describeHandler(info, "mOnLongClickListener", source, ordinal);
    }

    private static void describeHandler(
        Object info, String name, String source, int ordinal
    ) {
        try {
            Object handler = de.robv.android.xposed.XposedHelpers.getObjectField(
                info, name
            );
            if (handler == null) return;
            Class<?> type = handler.getClass();
            Log.i(TAG, "HANDLER source=" + source + " ordinal=" + ordinal
                + " role=" + name + " class=" + type.getName());
            if (ordinal == 0) {
                // The real Google controller is likely captured by the
                // inner delegate rather than the fzih/fzig view wrapper.
                // Report the next level of reference *types* only.
                inspectDelegateShape(handler, name, source);
            }
            int emitted = 0;
            for (Class<?> cursor = type;
                 cursor != null && cursor != Object.class && emitted < MAX_CAPTURE_FIELDS;
                 cursor = cursor.getSuperclass()) {
                for (Field field : cursor.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) continue;
                    if (emitted++ >= MAX_CAPTURE_FIELDS) break;
                    String capturedClass = "unavailable";
                    try {
                        field.setAccessible(true);
                        Object instance = field.get(handler);
                        capturedClass = instance == null
                            ? "null" : instance.getClass().getName();
                    } catch (Throwable failure) {
                        capturedClass = "inaccessible";
                    }
                    // Class names ONLY. Never print fields' values, object
                    // identities, phone numbers, conversation data or text.
                    Log.i(TAG, "HANDLER_FIELD role=" + name
                        + " class=" + cursor.getName()
                        + " field=" + field.getName()
                        + " declared=" + field.getType().getName()
                        + " runtime=" + capturedClass);
                }
            }
        } catch (Throwable failure) {
            Log.w(TAG, "HANDLER unavailable role=" + name + " error="
                + failure.getClass().getSimpleName());
        }
    }

    /**
     * Wrapper fzih/fzig delegates to Q25-observed asvb/asvc callbacks.
     * Inspect their captured reference class types to locate the actual
     * Google navigation controller without reading any captured data.
     */
    private static void inspectDelegateShape(
        Object wrapper, String role, String source
    ) {
        try {
            for (Field field : wrapper.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                    || field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object candidate = field.get(wrapper);
                if (candidate == null) continue;
                String typeName = candidate.getClass().getName();
                if (!"asvb".equals(typeName) && !"asvc".equals(typeName)) continue;

                int reported = 0;
                Class<?> type = candidate.getClass();
                for (Field captured : type.getDeclaredFields()) {
                    if (Modifier.isStatic(captured.getModifiers())
                        || captured.getType().isPrimitive()) continue;
                    if (reported++ >= 12) break;
                    String runtime = "inaccessible";
                    try {
                        captured.setAccessible(true);
                        Object value = captured.get(candidate);
                        runtime = value == null
                            ? "null" : value.getClass().getName();
                    } catch (Throwable ignored) { }
                    Log.i(TAG, "DELEGATE_FIELD source=" + source
                        + " role=" + role
                        + " delegate=" + typeName
                        + " capturedName=" + captured.getName()
                        + " declared=" + captured.getType().getName()
                        + " runtime=" + runtime);
                }
                for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                    if (method.getParameterTypes().length != 1
                        || method.getParameterTypes()[0] != View.class) continue;
                    Log.i(TAG, "DELEGATE_METHOD class=" + typeName
                        + " name=" + method.getName()
                        + " returns=" + method.getReturnType().getName());
                }
            }
        } catch (Throwable failure) {
            Log.w(TAG, "Delegate structural inspection unavailable: "
                + failure.getClass().getSimpleName());
        }
    }

    private static View findConversationRow(View view, int depth) {
        if (view == null || depth > 5) return null;
        if (CONVERSATION_ROW.equals(view.getClass().getName())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findConversationRow(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }
}
