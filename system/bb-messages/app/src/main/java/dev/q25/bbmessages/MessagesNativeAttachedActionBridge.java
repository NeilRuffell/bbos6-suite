package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * Q25 Google Messages adapter/action glue, not a list navigation helper.
 *
 * The stock Google click path works for a real ATTACHED ViewHolder but
 * detached ViewHolder.performClick failed in Q25 testing. This bridge
 * observes Google's real vv.g(aac,int) calls and invokes only its original,
 * currently attached row listener. Never invent a conversation ID or call
 * the obfuscated asvk.I(int) accessor outside Google's own native click.
 *
 * If native binding/position/presentation is stale or unavailable,
 * no action is dispatched. Caller must restore the stock inbox.
 */
final class MessagesNativeAttachedActionBridge {
    private static final String TAG = "BBMessagesNavigation";
    private static final String ROW_CLASS =
        "com.google.android.apps.messaging.home.list.ConversationListItemView";
    private static final String RECYCLER_CLASS =
        "android.support.v7.widget.RecyclerView";
    private static final String PACKAGE = "com.google.android.apps.messaging";
    // Weak keys, integer-only values: no retained row/holder reference cycle.
    private static final Map<View, Integer> BOUND =
        Collections.synchronizedMap(new WeakHashMap<View, Integer>());

    // Read-only evidence about which Google controller method the original
    // listener actually calls. Thread-local scope excludes unrelated opens.
    private static final ThreadLocal<DispatchTrace> ACTIVE_DISPATCH = new ThreadLocal<>();

    private static final class DispatchTrace {
        final boolean longPress;
        int openCalls;
        int menuCalls;
        int completedCalls;
        int failedCalls;

        DispatchTrace(boolean longPress) {
            this.longPress = longPress;
        }
    }

    /**
     * Called by exact asvk.Q/P hooks ONLY during original attached-row
     * listener dispatch on the same thread. No IDs or objects are logged.
     */
    static void observeControllerAction(boolean longPress, boolean completed) {
        DispatchTrace state = ACTIVE_DISPATCH.get();
        if (state == null) return;
        if (longPress) state.menuCalls++;
        else state.openCalls++;
        if (completed) state.completedCalls++;
        else state.failedCalls++;
    }

    private MessagesNativeAttachedActionBridge() { }

    static void observeNativeBind(Object holder, int position) {
        if (holder == null || position < 0
            || !"asvj".equals(holder.getClass().getName())) return;
        try {
            Object candidate = XposedHelpers.getObjectField(holder, "a");
            if (!(candidate instanceof View)) return;
            View row = (View) candidate;
            if (!ROW_CLASS.equals(row.getClass().getName())) return;
            BOUND.put(row, position);
        } catch (Throwable unavailable) {
            // Google may change its obfuscated ViewHolder. Fail closed.
        }
    }

    /**
     * Selects from the currently ATTACHED native rows only, and never
     * creates/rebinds a detached row as an action source.
     *
     * Required caller guards: current native adapter/count, a freshly
     * rebound model at selected index with identical visible fields, and
     * a unique sender/preview/timestamp tuple in the shared snapshot.
     */
    static boolean activate(
        ViewGroup recycler, int adapterPosition, String sender,
        String preview, String timestamp, boolean longPress
    ) {
        if (recycler == null
            || !RECYCLER_CLASS.equals(recycler.getClass().getName())
            || adapterPosition < 0) return false;
        final int nameId = recycler.getResources().getIdentifier(
            "conversation_name", "id", PACKAGE
        );
        final int snippetId = recycler.getResources().getIdentifier(
            "conversation_snippet", "id", PACKAGE
        );
        final int timestampId = recycler.getResources().getIdentifier(
            "conversation_timestamp_and_unread_badge", "id", PACKAGE
        );
        if (nameId == 0 || snippetId == 0) return false;
        View match = null;
        int matches = 0;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View row = recycler.getChildAt(i);
            if (row == null || row.getParent() != recycler
                || !row.isAttachedToWindow() || !row.isEnabled()
                || !ROW_CLASS.equals(row.getClass().getName())) continue;
            Integer bound = BOUND.get(row);
            if (bound == null || bound != adapterPosition) continue;
            TextView name = textField(row, nameId);
            TextView message = textField(row, snippetId);
            if (name == null || message == null
                || !sender.contentEquals(name.getText())
                || !preview.contentEquals(message.getText())
                || !sameTimestamp(timestamp, row, timestampId)) {
                Log.i(TAG, "ATTACHED_OPEN denied: native presentation changed");
                return false;
            }
            match = row;
            matches++;
        }
        if (matches != 1 || match == null) {
            Log.i(TAG, "ATTACHED_OPEN unavailable: selected native row not attached uniquely");
            return false;
        }
        if (!match.hasOnClickListeners() ||
            (longPress && !match.isLongClickable())) return false;

        // Native listener dispatch is the only place Q25 testing observed
        // Google reliably populate the ConversationId before asvk.Q().
        DispatchTrace trace = new DispatchTrace(longPress);
        if (ACTIVE_DISPATCH.get() != null) {
            Log.w(TAG, "ATTACHED_OPEN nested Google action refused");
            return false;
        }
        ACTIVE_DISPATCH.set(trace);
        boolean invoked = false;
        try {
            invoked = longPress ? match.performLongClick() : match.performClick();
            return invoked;
        } finally {
            ACTIVE_DISPATCH.remove();
            Log.i(TAG, "ATTACHED_OPEN originalGoogleActionInvoked=" + invoked
                + " longPress=" + longPress
                + " nativeQCalls=" + trace.openCalls
                + " nativePCalls=" + trace.menuCalls
                + " nativeCompleted=" + trace.completedCalls
                + " nativeFailures=" + trace.failedCalls);
        }
    }

    private static TextView textField(View root, int id) {
        View candidate = root.findViewById(id);
        return candidate instanceof TextView ? (TextView) candidate : null;
    }

    private static boolean sameTimestamp(String expected, View row, int id) {
        if (id == 0) return expected == null;
        View container = row.findViewById(id);
        TextView actual = firstText(container);
        String value = actual == null ? null : actual.getText().toString();
        return expected == null ? value == null : expected.equals(value);
    }

    private static TextView firstText(View view) {
        if (view instanceof TextView) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = firstText(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
}
