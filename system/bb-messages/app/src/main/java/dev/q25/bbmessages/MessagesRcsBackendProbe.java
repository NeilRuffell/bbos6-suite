package dev.q25.bbmessages;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Read-only structural research into the installed Google Messages build.
 *
 * This first experiment observes the Q25-verified native conversation-open
 * controller method, not an RCS transport API. The controller-to-service
 * reference types provide evidence for choosing the next data-layer probe.
 *
 * It does not call the original method, change arguments or return values,
 * navigate, inspect message contents/IDs, or interact with RecyclerView.
 * Every logged payload is a constant, a class/field type name or a boolean.
 */
public final class MessagesRcsBackendProbe implements IXposedHookLoadPackage {
    private static final String TAG = "BBMessagesRcsProbe";
    private static final String PROPERTY = "debug.bbmessages.rcsprobe.enabled";
    private static final String SCHEMA_PROPERTY =
        "debug.bbmessages.rcsprobe.schema.enabled";
    private static final String TYPES_PROPERTY =
        "debug.bbmessages.rcsprobe.types.enabled";
    private static final String CONTROLLER = "asvk";
    private static final String CONVERSATION_ID =
        "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId";
    private static final int MAX_FIELDS = 48;
    private static final int MAX_METHODS = 24;
    private static final AtomicBoolean DESCRIBED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!RcsProbeScope.GOOGLE_MESSAGES.equals(param.packageName)
            || !RcsProbeScope.GOOGLE_MESSAGES.equals(param.processName)) {
            return;
        }
        if (!RcsProbeScope.shouldInstall(
                param.packageName, param.processName, isEnabled())) {
            Log.i(TAG, "RCS_PROBE disabled; stock Google Messages unchanged");
            return;
        }
        try {
            Class<?> controller = XposedHelpers.findClassIfExists(
                CONTROLLER, param.classLoader
            );
            Class<?> conversationId = XposedHelpers.findClassIfExists(
                CONVERSATION_ID, param.classLoader
            );
            if (controller == null || conversationId == null) {
                Log.w(TAG, "RCS_PROBE unavailable: verified conversation types absent");
                return;
            }
            Method open = controller.getDeclaredMethod("Q", conversationId);
            if (Modifier.isStatic(open.getModifiers())
                || open.getReturnType() != void.class) {
                Log.w(TAG, "RCS_PROBE unavailable: native open signature changed");
                return;
            }
            XposedBridge.hookMethod(open, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam hook) {
                    // This is an observation of Google's OWN invocation,
                    // not a synthetic action or a generated conversation ID.
                    if (!DESCRIBED.compareAndSet(false, true)) return;
                    try {
                        Log.i(TAG, "RCS_PROBE originalConversationOpenObserved=true"
                            + " completed=" + !hook.hasThrowable());
                        boolean includeSchema = propertyEnabled(SCHEMA_PROPERTY);
                        describeReferenceTypes(
                            hook.thisObject, controller, includeSchema,
                            includeSchema && propertyEnabled(TYPES_PROPERTY)
                        );
                        describeConversationType(conversationId);
                    } catch (Throwable failure) {
                        Log.w(TAG, "RCS_PROBE structural inspection failed: "
                            + failure.getClass().getSimpleName());
                    }
                }
            });
            Log.i(TAG, "RCS_PROBE installed: read-only native open observer"
                + " (not evidence of RCS backend access)");
        } catch (Throwable unavailable) {
            Log.w(TAG, "RCS_PROBE unavailable: "
                + unavailable.getClass().getSimpleName());
        }
    }

    private static boolean isEnabled() {
        return propertyEnabled(PROPERTY);
    }

    private static boolean propertyEnabled(String property) {
        try {
            Class<?> properties = XposedHelpers.findClass(
                "android.os.SystemProperties", null
            );
            Object result = XposedHelpers.callStaticMethod(
                properties, "getBoolean", property, false
            );
            return Boolean.TRUE.equals(result);
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * Examine bounded direct reference-field *types* from the controller.
     * Reading the Java class of a referenced object is not reading its
     * conversation content. Never call methods on those objects or log them.
     */
    private static void describeReferenceTypes(
        Object instance, Class<?> expected,
        boolean includeSchema, boolean includeDeclaredTypes
    ) {
        if (instance == null || !expected.isInstance(instance)) {
            Log.w(TAG, "RCS_PROBE controller instance unavailable");
            return;
        }
        int emitted = 0;
        for (Class<?> cursor = instance.getClass();
             cursor != null && cursor != Object.class && emitted < MAX_FIELDS;
             cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (emitted >= MAX_FIELDS) break;
                if (Modifier.isStatic(field.getModifiers())
                    || field.getType().isPrimitive()) continue;
                String runtimeType = "unavailable";
                Object reference = null;
                try {
                    field.setAccessible(true);
                    reference = field.get(instance);
                    runtimeType = reference == null
                        ? "null" : reference.getClass().getName();
                } catch (Throwable inaccessible) {
                    // A field may be inaccessible on a different Google build.
                }
                Log.i(TAG, "RCS_PROBE controllerReference"
                    + " declared=" + field.getType().getName()
                    + " runtime=" + runtimeType);
                // Only Class metadata from known Q25 reference types;
                // never inspect the instance's fields a second time,
                // traverse its object graph or call a Google method.
                if (includeSchema && reference != null) {
                    RcsServiceMetadataProbe.describe(reference.getClass());
                    // A separate opt-in reads only Class metadata on
                    // allowlisted types declared by the observed ucc class.
                    // No nested instance-field values are ever accessed.
                    if (includeDeclaredTypes) {
                        RcsDeclaredTypeProbe.describeFrom(reference.getClass());
                    }
                }
                emitted++;
            }
        }
        Log.i(TAG, "RCS_PROBE controllerReferenceCount=" + emitted);
    }

    private static void describeConversationType(Class<?> idType) {
        Class<?>[] interfaces = idType.getInterfaces();
        Log.i(TAG, "RCS_PROBE conversationIdType=" + idType.getName()
            + " interfaceCount=" + interfaces.length);
        int emitted = 0;
        for (Method method : idType.getDeclaredMethods()) {
            if (emitted >= MAX_METHODS) break;
            // Only structural signature metadata; no reflection invocation.
            Log.i(TAG, "RCS_PROBE conversationIdMethod"
                + " name=" + method.getName()
                + " returnType=" + method.getReturnType().getName()
                + " parameterCount=" + method.getParameterTypes().length);
            emitted++;
        }
        Log.i(TAG, "RCS_PROBE conversationIdMethodCount=" + emitted);
    }
}
