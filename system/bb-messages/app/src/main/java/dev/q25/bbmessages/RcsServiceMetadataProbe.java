package dev.q25.bbmessages;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * One-level, metadata-only class shape discovery.
 *
 * DO NOT invoke target methods, read instance field values, recurse into
 * object references, serialize class objects, or log method arguments.
 * These are candidates seen in the Q25 `asvk` controller, NOT verified
 * RCS services. The feature is disabled unless schema opt-in is set.
 */
final class RcsServiceMetadataProbe {
    private static final String TAG = "BBMessagesRcsProbe";
    private static final Set<String> REPORTED = new HashSet<String>();

    private RcsServiceMetadataProbe() { }

    static void describe(Class<?> clazz) {
        String className = clazz.getName();
        if (!RcsProbeMetadataPolicy.isKnownDirectReference(className)) return;
        if (!REPORTED.add(className)) return;
        if (REPORTED.size() > RcsProbeMetadataPolicy.MAX_CLASSES) return;

        try {
            Class<?> parent = clazz.getSuperclass();
            Log.i(TAG, "RCS_SCHEMA class=" + className
                + " superclass=" + (parent == null ? "none" : parent.getName()));
            Class<?>[] interfaces = clazz.getInterfaces();
            int interfaceLimit = Math.min(
                interfaces.length, RcsProbeMetadataPolicy.MAX_INTERFACES_PER_CLASS
            );
            for (int i = 0; i < interfaceLimit; i++) {
                Log.i(TAG, "RCS_SCHEMA interface class=" + className
                    + " type=" + interfaces[i].getName());
            }
            Field[] fields = clazz.getDeclaredFields();
            int fieldCount = 0;
            for (Field field : fields) {
                if (fieldCount >= RcsProbeMetadataPolicy.MAX_FIELDS_PER_CLASS) break;
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()))
                    continue;
                Log.i(TAG, "RCS_SCHEMA field class=" + className
                    + " name=" + field.getName()
                    + " type=" + field.getType().getName());
                fieldCount++;
            }
            Log.i(TAG, "RCS_SCHEMA fieldSummary class=" + className
                + " inspected=" + fieldCount + " declaredTotal=" + fields.length);

            Method[] methods = clazz.getDeclaredMethods();
            int methodCount = 0;
            for (Method method : methods) {
                if (methodCount >= RcsProbeMetadataPolicy.MAX_METHODS_PER_CLASS)
                    break;
                if (method.isSynthetic() || method.isBridge()
                    || Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                StringBuilder signature = new StringBuilder();
                int parameterLimit = Math.min(
                    parameters.length, RcsProbeMetadataPolicy.MAX_PARAMETER_TYPES
                );
                for (int i = 0; i < parameterLimit; i++) {
                    if (i > 0) signature.append(',');
                    signature.append(parameters[i].getName());
                }
                if (parameters.length > parameterLimit) signature.append(",...");
                Log.i(TAG, "RCS_SCHEMA method class=" + className
                    + " name=" + method.getName()
                    + " returns=" + method.getReturnType().getName()
                    + " params=" + signature.toString());
                methodCount++;
            }
            Log.i(TAG, "RCS_SCHEMA methodSummary class=" + className
                + " inspected=" + methodCount
                + " declaredTotal=" + methods.length);
        } catch (Throwable failure) {
            Log.w(TAG, "RCS_SCHEMA inspection unavailable class=" + className
                + " cause=" + failure.getClass().getSimpleName());
        }
    }
}
