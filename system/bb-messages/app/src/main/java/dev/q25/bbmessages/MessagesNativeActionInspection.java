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
