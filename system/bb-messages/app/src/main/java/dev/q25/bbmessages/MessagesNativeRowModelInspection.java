package dev.q25.bbmessages;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Read-only investigation of where Google's per-row ConversationId lives.
 *
 * Q25 proved asvj.r.f.H.H goes via asvk -> MainActivity -> aucu.
 * That last field is temporarily populated during a native click and
 * cleared when it finishes, so it is NOT a per-row conversation identity.
 *
 * Inspect bound-holder and click-result reference graphs while explicitly
 * excluding the controller, Activity, View and platform objects. An actual
 * Q(ConversationId) call supplies the comparison object. No IDs, text,
 * contacts, values, hashes, database operations or synthetic actions.
 */
final class MessagesNativeRowModelInspection {
    private static final String TAG = "BBMessagesModel";
    private static final String ID_INTERFACE =
        "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId";
    private static final int MAX_DEPTH = 5;
    private static final int MAX_OBJECTS = 128;
    private static final int MAX_FIELDS = 20;
    private static final int MAX_SHAPE_FIELDS = 18;

    private MessagesNativeRowModelInspection() { }

    static void describeHolder(Object holder, String phase) {
        if (holder == null || !"asvj".equals(holder.getClass().getName())) return;
        int emitted = 0;
        for (Class<?> type = holder.getClass();
             type != null && type != Object.class && emitted < MAX_SHAPE_FIELDS;
             type = type.getSuperclass()) {
            for (Field f : type.getDeclaredFields()) {
                if (emitted >= MAX_SHAPE_FIELDS) return;
                if (Modifier.isStatic(f.getModifiers())
                    || f.getType().isPrimitive()) continue;
                emitted++;
                String runtime = "inaccessible";
                try {
                    f.setAccessible(true);
                    Object value = f.get(holder);
                    runtime = value == null ? "null" : value.getClass().getName();
                } catch (Throwable ignored) { }
                Log.i(TAG, "MODEL_SHAPE phase=" + phase
                    + " field=" + f.getName()
                    + " declared=" + f.getType().getName()
                    + " runtime=" + runtime);
            }
        }
    }

    static void compareOnRealOpen(
        Object delegate, Object selectionModel, Object actualConversationId
    ) {
        if (delegate == null || !"asvb".equals(delegate.getClass().getName())
            || actualConversationId == null) return;
        try {
            Class<?> expectedType = Class.forName(
                ID_INTERFACE, false, delegate.getClass().getClassLoader()
            );
            if (!expectedType.isInstance(actualConversationId)) return;
            Object holder = getField(delegate, "b");
            if (holder == null || !"asvj".equals(holder.getClass().getName())) return;

            // Model object asvk.I(int) returns, if any.
            Search holderGraph = new Search(expectedType, actualConversationId);
            holderGraph.scan(holder, "holder", 0);
            Log.i(TAG, "MODEL_MATCH root=holder candidates=" + holderGraph.candidates
                + " referenceMatch=" + holderGraph.referenceMatch
                + " equalityMatch=" + holderGraph.equalityMatch
                + " matchingPath=" + holderGraph.matchingPath
                + " visited=" + holderGraph.visited.size()
                + " truncated=" + holderGraph.truncated);

            if (selectionModel != null) {
                Search selected = new Search(expectedType, actualConversationId);
                selected.scan(selectionModel, "resultOfI", 0);
                Log.i(TAG, "MODEL_MATCH root=resultOfI"
                    + " type=" + selectionModel.getClass().getName()
                    + " candidates=" + selected.candidates
                    + " referenceMatch=" + selected.referenceMatch
                    + " equalityMatch=" + selected.equalityMatch
                    + " matchingPath=" + selected.matchingPath
                    + " visited=" + selected.visited.size()
                    + " truncated=" + selected.truncated);
                describeObject(selectionModel, "resultOfI");
            } else {
                Log.i(TAG, "MODEL_MATCH root=resultOfI unavailable");
            }
        } catch (Throwable e) {
            Log.w(TAG, "MODEL_MATCH unavailable=" + e.getClass().getSimpleName());
        }
    }

    private static void describeObject(Object root, String phase) {
        int count = 0;
        for (Class<?> type = root.getClass();
             type != null && type != Object.class && count < MAX_SHAPE_FIELDS;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (count >= MAX_SHAPE_FIELDS) return;
                if (Modifier.isStatic(field.getModifiers())
                    || field.getType().isPrimitive()) continue;
                count++;
                String runtime = "inaccessible";
                try {
                    field.setAccessible(true);
                    Object v = field.get(root);
                    runtime = v == null ? "null" : v.getClass().getName();
                } catch (Throwable ignored) { }
                Log.i(TAG, "MODEL_SHAPE phase=" + phase
                    + " field=" + field.getName()
                    + " declared=" + field.getType().getName()
                    + " runtime=" + runtime);
            }
        }
    }

    private static Object getField(Object object, String name)
        throws ReflectiveOperationException {
        for (Class<?> type = object.getClass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())) return null;
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static final class Search {
        final Class<?> idType;
        final Object targetId;
        final Set<Object> visited =
            Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        int candidates;
        boolean referenceMatch;
        boolean equalityMatch;
        boolean truncated;
        String matchingPath = "none";

        Search(Class<?> idType, Object targetId) {
            this.idType = idType;
            this.targetId = targetId;
        }

        void scan(Object node, String path, int depth) {
            if (node == null) return;
            if (idType.isInstance(node)) {
                candidates++;
                if (node == targetId) referenceMatch = true;
                if (node == targetId || node.equals(targetId)) {
                    equalityMatch = true;
                    if ("none".equals(matchingPath)) matchingPath = path;
                }
                return;
            }
            if (depth > MAX_DEPTH || visited.size() >= MAX_OBJECTS) {
                truncated = true;
                return;
            }
            Class<?> t = node.getClass();
            String name = t.getName();
            if (node instanceof View || node instanceof Activity
                || t.isArray() || t.isEnum()
                || node instanceof CharSequence || node instanceof Number
                || node instanceof Class || "asvk".equals(name)
                || name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("kotlin.") || name.startsWith("android.")
                || name.startsWith("androidx.") || !visited.add(node)) return;
            for (Class<?> cls = t;
                 cls != null && cls != Object.class;
                 cls = cls.getSuperclass()) {
                int count = 0;
                for (Field f : cls.getDeclaredFields()) {
                    if (++count > MAX_FIELDS) {
                        truncated = true;
                        break;
                    }
                    if (Modifier.isStatic(f.getModifiers())
                        || f.getType().isPrimitive()) continue;
                    try {
                        f.setAccessible(true);
                        Object next = f.get(node);
                        if (next != null) scan(next, path + "." + f.getName(), depth + 1);
                    } catch (Throwable ignored) { }
                }
            }
        }
    }
}
