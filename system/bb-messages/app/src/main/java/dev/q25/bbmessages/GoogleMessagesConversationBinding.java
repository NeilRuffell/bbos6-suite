package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;

import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Q25-version-specific, Google-owned conversation identity extraction.
 *
 * During a real Q25 inbox click, an activity-scoped transient selection
 * at asvj.r.f.H.H matches the object passed to asvk.Q(ConversationId).
 * That is NOT the bound row's stable identity. This bridge validates only
 * controller/holder structure before a click; the real per-row ID must be
 * resolved through Google's model for the selected adapter position.
 *
 * This class NEVER invokes Google's controller, and its outputs must be
 * validated against real Google actions and refreshed bound model state
 * before a BBOS6 activation implementation can be enabled.
 */
final class GoogleMessagesConversationBinding {
    private static final String TAG = "BBMessagesActions";
    private static final String HOLDER = "asvj";
    private static final String CONTROLLER = "asvk";
    private static final String CLICK_DELEGATE = "asvb";
    private static final String CONVERSATION_ID =
        "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId";
    private static final String[] PATH = {"r", "f", "H", "H"};

    private GoogleMessagesConversationBinding() { }

    static Object conversationIdFromHolder(Object holder) {
        if (holder == null || !HOLDER.equals(holder.getClass().getName())) {
            return null;
        }
        try {
            Object value = holder;
            for (String fieldName : PATH) {
                value = readInstanceField(value, fieldName);
                if (value == null) return null;
            }
            Class<?> idInterface = Class.forName(
                CONVERSATION_ID, false, holder.getClass().getClassLoader()
            );
            return idInterface.isInstance(value) ? value : null;
        } catch (Throwable failure) {
            Log.w(TAG, "ID_DIRECT expected holder field path unavailable: "
                + failure.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Read-only lifecycle probe for the exact Q25 field path. Reports the
     * type of each hop (or the missing/inaccessible stage), never object
     * values, identity hashes, conversation IDs or message contents.
     *
     * A real Google click proved that asvj.r.f.H.H can be the Q argument,
     * but both attached and detached pre-click holders lacked an ID.
     * This trace determines when Google populates that field path.
     */
    static void logHolderPathStage(Object holder, String phase) {
        if (holder == null || !HOLDER.equals(holder.getClass().getName())) {
            Log.i(TAG, "ID_STAGE phase=" + phase + " root=invalid");
            return;
        }
        StringBuilder types = new StringBuilder("ID_STAGE phase=")
            .append(phase).append(" root=").append(HOLDER);
        Object value = holder;
        for (int i = 0; i < PATH.length; i++) {
            try {
                value = readInstanceField(value, PATH[i]);
                types.append(" hop").append(i + 1).append("=")
                    .append(value == null ? "null" : value.getClass().getName());
                if (value == null) break;
            } catch (Throwable failure) {
                types.append(" hop").append(i + 1)
                    .append("=unavailable_")
                    .append(failure.getClass().getSimpleName());
                break;
            }
        }
        Log.i(TAG, types.toString());
    }

    static void logDelegatePathStage(Object delegate, String phase) {
        if (delegate == null || !"asvb".equals(delegate.getClass().getName())) {
            Log.i(TAG, "ID_STAGE phase=" + phase + " delegate=invalid");
            return;
        }
        try {
            logHolderPathStage(readInstanceField(delegate, "b"), phase);
        } catch (Throwable failure) {
            Log.i(TAG, "ID_STAGE phase=" + phase
                + " delegateHolder=unavailable_"
                + failure.getClass().getSimpleName());
        }
    }

    static void logAttachedPathForProbe(View row) {
        if (row == null || !row.isAttachedToWindow()) return;
        try {
            Object info = XposedHelpers.getObjectField(row, "mListenerInfo");
            if (info == null) return;
            Object wrapper = XposedHelpers.getObjectField(info, "mOnClickListener");
            if (wrapper == null || !"fzih".equals(wrapper.getClass().getName())) return;
            logDelegatePathStage(readInstanceField(wrapper, "c"), "attached_before_click");
        } catch (Throwable failure) {
            Log.i(TAG, "ID_STAGE phase=attached_before_click unavailable="
                + failure.getClass().getSimpleName());
        }
    }

    /**
     * STRUCTURAL controller/holder reference only. Never treat it as a
     * ConversationId, valid navigation action or durable adapter identity.
     */
    static final class VerifiedHandle {
        final Object controller;
        final Object nativeHolder;

        VerifiedHandle(Object controller, Object nativeHolder) {
            this.controller = controller;
            this.nativeHolder = nativeHolder;
        }
    }

    static final class BindingCheck {
        final VerifiedHandle handle;
        final String reason;

        BindingCheck(VerifiedHandle handle, String reason) {
            this.handle = handle;
            this.reason = reason;
        }
    }

    /**
     * Classifies a bound native row without invoking any Google controller.
     * Distinguishing binding failures matters: the Q25 attached callback
     * produced a valid ID but detached snapshot binds verified 0/8 rows.
     */
    static BindingCheck inspectBoundConversation(View row, Object holder) {
        if (row == null || !"com.google.android.apps.messaging.home.list.ConversationListItemView"
            .equals(row.getClass().getName())) {
            return new BindingCheck(null, "row_type");
        }
        if (holder == null || !HOLDER.equals(holder.getClass().getName())) {
            return new BindingCheck(null, "holder_type");
        }
        try {
            Object info = XposedHelpers.getObjectField(row, "mListenerInfo");
            if (info == null) return new BindingCheck(null, "listener_info");
            Object wrapper = XposedHelpers.getObjectField(info, "mOnClickListener");
            if (wrapper == null || !"fzih".equals(wrapper.getClass().getName())) {
                return new BindingCheck(null, "click_wrapper");
            }
            Object delegate = readInstanceField(wrapper, "c");
            if (delegate == null ||
                !CLICK_DELEGATE.equals(delegate.getClass().getName())) {
                return new BindingCheck(null, "click_delegate");
            }
            if (readInstanceField(delegate, "b") != holder) {
                return new BindingCheck(null, "holder_reference");
            }
            Object controller = readInstanceField(delegate, "a");
            if (controller == null || !CONTROLLER.equals(controller.getClass().getName())) {
                return new BindingCheck(null, "controller_type");
            }
            Class<?> idClass = Class.forName(
                CONVERSATION_ID, false, holder.getClass().getClassLoader()
            );
            java.lang.reflect.Method select = controller.getClass()
                .getDeclaredMethod("I", int.class);
            java.lang.reflect.Method open = controller.getClass()
                .getDeclaredMethod("Q", idClass);
            if (select.getReturnType() == void.class
                || !"aszr".equals(select.getReturnType().getName())
                || Modifier.isStatic(select.getModifiers())) {
                return new BindingCheck(null, "selection_signature");
            }
            if (open.getReturnType() != void.class
                || Modifier.isStatic(open.getModifiers())) {
                return new BindingCheck(null, "open_signature");
            }
            // NO ConversationId is stored here. The previous
            // asvj.r.f.H.H path is activity-global and transient.
            return new BindingCheck(new VerifiedHandle(controller, holder),
                "structural_only");
        } catch (Throwable failure) {
            return new BindingCheck(null,
                "reflection_" + failure.getClass().getSimpleName());
        }
    }

    static VerifiedHandle fromBoundConversation(View row, Object holder) {
        return inspectBoundConversation(row, holder).handle;
    }

    /**
     * Inspect the original *attached* Google's row without needing an
     * adapter-position mapping. Its own click delegate identifies its holder.
     * Never invoke the callback. Only for read-only native diagnostics.
     */
    static BindingCheck inspectAttachedConversation(View row) {
        if (row == null || !row.isAttachedToWindow()) {
            return new BindingCheck(null, "not_attached");
        }
        try {
            Object info = XposedHelpers.getObjectField(row, "mListenerInfo");
            if (info == null) return new BindingCheck(null, "listener_info");
            Object wrapper = XposedHelpers.getObjectField(info, "mOnClickListener");
            if (wrapper == null || !"fzih".equals(wrapper.getClass().getName())) {
                return new BindingCheck(null, "click_wrapper");
            }
            Object delegate = readInstanceField(wrapper, "c");
            if (delegate == null ||
                !CLICK_DELEGATE.equals(delegate.getClass().getName())) {
                return new BindingCheck(null, "click_delegate");
            }
            return inspectBoundConversation(row, readInstanceField(delegate, "b"));
        } catch (Throwable failure) {
            return new BindingCheck(null,
                "reflection_" + failure.getClass().getSimpleName());
        }
    }

    static boolean matchesGoogleOpen(Object delegate, Object actualId) {
        if (delegate == null || actualId == null
            || !CLICK_DELEGATE.equals(delegate.getClass().getName())) {
            Log.w(TAG, "ID_DIRECT expected native click delegate missing");
            return false;
        }
        try {
            Object holder = readInstanceField(delegate, "b");
            Object controller = readInstanceField(delegate, "a");
            if (controller == null || !CONTROLLER.equals(controller.getClass().getName())
                || holder == null || !HOLDER.equals(holder.getClass().getName())) {
                Log.w(TAG, "ID_DIRECT native controller/holder mismatch");
                return false;
            }
            Object extracted = conversationIdFromHolder(holder);
            boolean referenceMatch = extracted != null && extracted == actualId;
            boolean equalityMatch = extracted != null && extracted.equals(actualId);
            Log.i(TAG, "ID_DIRECT present=" + (extracted != null)
                + " referenceMatch=" + referenceMatch
                + " equalityMatch=" + equalityMatch
                + " controllerVerified=true");
            return referenceMatch && equalityMatch;
        } catch (Throwable failure) {
            Log.w(TAG, "ID_DIRECT verified native callback path unavailable: "
                + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static Object readInstanceField(Object owner, String name)
        throws ReflectiveOperationException {
        if (owner == null) return null;
        for (Class<?> type = owner.getClass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())
                    || field.getType().isPrimitive()) return null;
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException missing) {
                // The Q25 native holder may inherit part of this path.
            }
        }
        return null;
    }
}
