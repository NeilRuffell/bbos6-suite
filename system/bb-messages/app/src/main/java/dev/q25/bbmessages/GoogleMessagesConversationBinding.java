package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;

import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Q25-version-specific, Google-owned conversation identity extraction.
 *
 * A real Q25 stock inbox click passed the exact same ConversationId object
 * found at asvj.r.f.H.H into asvk.Q(ConversationId). This bridge follows
 * that observed field path directly; it never guesses an identifier from
 * display text, list position, or a detached click handler.
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

    /** A validated native action/data binding, never a fabricated row ID. */
    static final class VerifiedHandle {
        final Object controller;
        final Object conversationId;
        final Object nativeHolder;

        VerifiedHandle(Object controller, Object conversationId, Object nativeHolder) {
            this.controller = controller;
            this.conversationId = conversationId;
            this.nativeHolder = nativeHolder;
        }
    }

    /**
     * Links a native bound holder to its actual controller and ConversationId.
     * This does not invoke Q or P. The caller must ensure the adapter has not
     * changed and refresh/revalidate selection before future navigation.
     */
    static VerifiedHandle fromBoundConversation(View row, Object holder) {
        if (row == null || holder == null
            || !"com.google.android.apps.messaging.home.list.ConversationListItemView"
                .equals(row.getClass().getName())
            || !HOLDER.equals(holder.getClass().getName())) return null;
        try {
            Object info = XposedHelpers.getObjectField(row, "mListenerInfo");
            if (info == null) return null;
            Object clickWrapper = XposedHelpers.getObjectField(info, "mOnClickListener");
            if (clickWrapper == null || !"fzih".equals(clickWrapper.getClass().getName()))
                return null;
            Object delegate = readInstanceField(clickWrapper, "c");
            if (delegate == null
                || !CLICK_DELEGATE.equals(delegate.getClass().getName())
                || readInstanceField(delegate, "b") != holder) return null;
            Object controller = readInstanceField(delegate, "a");
            if (controller == null || !CONTROLLER.equals(controller.getClass().getName()))
                return null;
            Object id = conversationIdFromHolder(holder);
            if (id == null) return null;
            Class<?> idClass = Class.forName(
                CONVERSATION_ID, false, holder.getClass().getClassLoader()
            );
            java.lang.reflect.Method open = controller.getClass()
                .getDeclaredMethod("Q", idClass);
            if (open.getReturnType() != void.class
                || Modifier.isStatic(open.getModifiers())) return null;
            return new VerifiedHandle(controller, id, holder);
        } catch (Throwable failure) {
            return null;
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
