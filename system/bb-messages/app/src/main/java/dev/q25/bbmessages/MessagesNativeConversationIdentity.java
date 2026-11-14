package dev.q25.bbmessages;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A read-only link between Google's native click delegate, bound holder,
 * and ConversationId. Called ONLY while Google itself invokes asvk.Q.
 *
 * All output is structural: field names, class names, counts, and equality
 * booleans. Conversation identifiers/contacts/messages are never logged.
 * No setters, getters, navigation actions, or synthetic clicks are invoked.
 */
final class MessagesNativeConversationIdentity {
    private static final String TAG = "BBMessagesActions";
    private static final String CONTROLLER = "asvk";
    private static final String HOLDER = "asvj";
    private static final String CONVERSATION_ID =
        "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId";
    private static final int MAX_DEPTH = 3;
    private static final int MAX_OBJECTS = 48;
    private static final int MAX_FIELDS_PER_OBJECT = 24;

    private MessagesNativeConversationIdentity() { }

    static void compareRealOpen(Object delegate, Object actualConversationId) {
        if (delegate == null || !"asvb".equals(delegate.getClass().getName())
            || actualConversationId == null) return;
        try {
            Class<?> conversationClass = Class.forName(
                CONVERSATION_ID, false, delegate.getClass().getClassLoader()
            );
            if (!conversationClass.isInstance(actualConversationId)) {
                Log.w(TAG, "ID_LINK actual navigation argument is not ConversationId");
                return;
            }
            Object controller = findExactField(delegate, "a", CONTROLLER);
            Object holder = findExactField(delegate, "b", HOLDER);
            if (controller == null || holder == null) {
                Log.w(TAG, "ID_LINK native controller or ViewHolder not found");
                return;
            }
            Search search = new Search(conversationClass, actualConversationId);
            search.scan(holder, "asvj", 0);
            Log.i(TAG, "ID_LINK holderClass=" + holder.getClass().getName()
                + " controllerClass=" + controller.getClass().getName()
                + " candidateCount=" + search.candidates
                + " referenceMatch=" + search.referenceMatch
                + " equalityMatch=" + search.equalityMatch
                + " firstCandidatePath=" + (search.firstPath == null ? "none" : search.firstPath)
                + " objectsExamined=" + search.visited.size());
        } catch (Throwable failure) {
            Log.w(TAG, "ID_LINK read-only comparison failed: "
                + failure.getClass().getSimpleName());
        }
    }

    private static Object findExactField(Object owner, String name, String className) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            if (Modifier.isStatic(field.getModifiers())) return null;
            field.setAccessible(true);
            Object value = field.get(owner);
            return value != null && className.equals(value.getClass().getName())
                ? value : null;
        } catch (Throwable failure) {
            return null;
        }
    }

    private static final class Search {
        final Class<?> conversationClass;
        final Object expected;
        final Set<Object> visited =
            Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        int candidates;
        boolean referenceMatch;
        boolean equalityMatch;
        String firstPath;

        Search(Class<?> conversationClass, Object expected) {
            this.conversationClass = conversationClass;
            this.expected = expected;
        }

        void scan(Object instance, String path, int depth) {
            if (instance == null || depth > MAX_DEPTH || visited.size() >= MAX_OBJECTS)
                return;
            if (conversationClass.isInstance(instance)) {
                candidates++;
                if (firstPath == null) firstPath = path;
                if (instance == expected) referenceMatch = true;
                // Equality is checked only between two verified ConversationId
                // objects and never printed as an identifier.
                if (instance.equals(expected)) equalityMatch = true;
                return;
            }
            Class<?> runtime = instance.getClass();
            if (runtime.isPrimitive() || runtime.isEnum()
                || runtime.isArray()
                || instance instanceof String
                || instance instanceof Number
                || instance instanceof View
                || runtime.getName().startsWith("java.")
                || runtime.getName().startsWith("android.")
                || runtime.getName().startsWith("androidx.")
                || !visited.add(instance)) return;
            for (Class<?> type = runtime;
                 type != null && type != Object.class && depth <= MAX_DEPTH;
                 type = type.getSuperclass()) {
                int inspected = 0;
                for (Field field : type.getDeclaredFields()) {
                    if (inspected++ >= MAX_FIELDS_PER_OBJECT || visited.size() >= MAX_OBJECTS)
                        return;
                    if (Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(instance);
                        if (value == null) continue;
                        // No object values are logged or formatted.
                        scan(value, path + "." + field.getName(), depth + 1);
                    } catch (Throwable ignored) { }
                }
            }
        }
    }
}
