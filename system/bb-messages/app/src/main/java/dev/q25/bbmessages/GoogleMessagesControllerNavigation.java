package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Q25 Google Messages-specific controller/model contract, NOT a UI helper.
 *
 * Google's real asvb click invokes asvk.I(adapterPosition), then passes
 * the ConversationId at aszh.a.I.c into asvk.Q(ConversationId).
 * Two physical Q25 taps confirmed this argument and index correspondence.
 *
 * This connector is opt-in only for the replacement inbox. Every activation
 * re-resolves the current model and compares it against the snapshot's ID.
 * No ID, body, contact, hash or object value is logged.
 *
 * Obfuscated APIs are version-specific and may change with Google updates.
 */
final class GoogleMessagesControllerNavigation {
    private static final String TAG = "BBMessagesNavigation";
    private static final String ID_INTERFACE =
        "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId";
    // Structural diagnostics only: distinct failure stages are printed once.
    // No identifiers, model objects, contacts or text enter the logs.
    private static final Set<String> REPORTED_STAGES =
        Collections.synchronizedSet(new HashSet<String>());

    private static void unavailable(String stage) {
        if (REPORTED_STAGES.add(stage)) {
            Log.i(TAG, "MODEL_RESOLVE_STAGE unavailable=" + stage);
        }
    }

    static final class BoundModel {
        final Object controller;
        final Object id;

        BoundModel(Object controller, Object id) {
            this.controller = controller;
            this.id = id;
        }
    }

    private GoogleMessagesControllerNavigation() { }

    /**
     * Invoke Google's verified native model accessor for a bound row index.
     * Returns null for any mismatched API or model shape.
     *
     * Do not invoke this outside the explicitly opted-in shared inbox mode:
     * Google's internal I(int) method is not a public, stable read-only API.
     */
    static BoundModel resolve(View row, Object holder, int position) {
        if (position < 0) {
            unavailable("invalid_position");
            return null;
        }
        GoogleMessagesConversationBinding.BindingCheck binding =
            GoogleMessagesConversationBinding.inspectBoundConversation(row, holder);
        if (binding.handle == null) {
            unavailable("native_binding_" + binding.reason);
            return null;
        }
        Object controller = binding.handle.controller;
        try {
            Class<?> type = controller.getClass();
            if (!"asvk".equals(type.getName())) {
                unavailable("controller_class");
                return null;
            }
            Method accessor = type.getDeclaredMethod("I", int.class);
            Class<?> idType = Class.forName(
                ID_INTERFACE, false, type.getClassLoader()
            );
            if (!"aszr".equals(accessor.getReturnType().getName())
                || Modifier.isStatic(accessor.getModifiers())) {
                unavailable("accessor_signature");
                return null;
            }
            accessor.setAccessible(true);
            Object result = accessor.invoke(controller, position);
            if (result == null) {
                unavailable("accessor_result_null");
                return null;
            }
            if (!"aszh".equals(result.getClass().getName())) {
                unavailable("accessor_result_class_" + result.getClass().getName());
                return null;
            }
            Object a = readField(result, "a");
            if (a == null) {
                unavailable("model_a_null");
                return null;
            }
            Object i = readField(a, "I");
            if (i == null) {
                unavailable("model_a_I_null");
                return null;
            }
            Object id = readField(i, "c");
            if (!idType.isInstance(id)) {
                unavailable(id == null ? "model_id_null" : "model_id_type");
                return null;
            }
            return new BoundModel(controller, id);
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException
                && ((InvocationTargetException) error).getCause() != null
                ? ((InvocationTargetException) error).getCause() : error;
            unavailable("exception_" + cause.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Only dispatch when the same controller and conversation ID are still
     * present at this position. Caller independently verifies that Google's
     * native adapter, count, current row kind and presentation are unchanged.
     */
    static boolean open(BoundModel snapshot, BoundModel current) {
        if (snapshot == null || current == null
            || snapshot.controller != current.controller
            || snapshot.id == null || current.id == null
            || !snapshot.id.equals(current.id)) {
            Log.w(TAG, "OPEN denied: controller/model changed");
            return false;
        }
        try {
            Class<?> type = current.controller.getClass();
            if (!"asvk".equals(type.getName())) return false;
            Class<?> idType = Class.forName(
                ID_INTERFACE, false, type.getClassLoader()
            );
            if (!idType.isInstance(current.id)) return false;
            Method open = type.getDeclaredMethod("Q", idType);
            if (open.getReturnType() != void.class
                || Modifier.isStatic(open.getModifiers())) return false;
            open.setAccessible(true);
            open.invoke(current.controller, current.id);
            Log.i(TAG, "OPEN invoked verified Google controller");
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "OPEN failed=" + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean sameConversation(BoundModel a, BoundModel b) {
        return a != null && b != null
            && a.controller == b.controller
            && a.id != null && b.id != null && a.id.equals(b.id);
    }

    private static Object readField(Object owner, String name)
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
            } catch (NoSuchFieldException missing) { }
        }
        return null;
    }
}
