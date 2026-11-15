package dev.q25.bbmessages;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Third, separately gated metadata-only diagnostic.
 *
 * Read the DECLARED Java Class types of observed ucc fields; never read
 * those fields' runtime values or follow actual object references. Only
 * six Q25-observed type names are eligible. This does not prove RCS access.
 */
final class RcsDeclaredTypeProbe {
    private static final String TAG = "BBMessagesRcsProbe";
    private static final Set<String> REPORTED = new HashSet<String>();

    private RcsDeclaredTypeProbe() { }

    static void describeFrom(Class<?> source) {
        if (source == null ||
            !RcsProbeMetadataPolicy.isObservedDeclaredTypeSource(
                source.getName())) return;
        try {
            for (Field field : source.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                    continue;
                Class<?> type = field.getType();
                String name = type.getName();
                if (!RcsProbeMetadataPolicy.isObservedUccDeclaredType(name)
                    || !REPORTED.add(name)) continue;
                if (REPORTED.size() >
                    RcsProbeMetadataPolicy.MAX_DECLARED_TYPE_CLASSES) return;
                describeType(type);
            }
        } catch (Throwable unavailable) {
            Log.w(TAG, "RCS_TYPES source unavailable: "
                + unavailable.getClass().getSimpleName());
        }
    }

    private static void describeType(Class<?> type) {
        String name = type.getName();
        if (!RcsProbeMetadataPolicy.isObservedUccDeclaredType(name)) return;
        try {
            Class<?> parent = type.getSuperclass();
            Log.i(TAG, "RCS_TYPES class=" + name
                + " superclass=" + (parent == null ? "none" : parent.getName())
                + " interface=" + type.isInterface());
            Class<?>[] interfaces = type.getInterfaces();
            int maxInterfaces = Math.min(interfaces.length,
                RcsProbeMetadataPolicy.MAX_INTERFACES_PER_CLASS);
            for (int i = 0; i < maxInterfaces; i++) {
                Log.i(TAG, "RCS_TYPES implemented class=" + name
                    + " type=" + interfaces[i].getName());
            }

            Field[] fields = type.getDeclaredFields();
            int fieldCount = 0;
            for (Field field : fields) {
                if (fieldCount >=
                    RcsProbeMetadataPolicy.MAX_DECLARED_TYPE_FIELDS) break;
                if (field.isSynthetic() ||
                    Modifier.isStatic(field.getModifiers())) continue;
                Log.i(TAG, "RCS_TYPES field class=" + name
                    + " name=" + field.getName()
                    + " type=" + field.getType().getName());
                fieldCount++;
            }
            Log.i(TAG, "RCS_TYPES fieldSummary class=" + name
                + " inspected=" + fieldCount
                + " declaredTotal=" + fields.length);

            Method[] methods = type.getDeclaredMethods();
            int methodCount = 0;
            for (Method method : methods) {
                if (methodCount >=
                    RcsProbeMetadataPolicy.MAX_DECLARED_TYPE_METHODS) break;
                if (method.isSynthetic() || method.isBridge()
                    || Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] params = method.getParameterTypes();
                StringBuilder signature = new StringBuilder();
                int limit = Math.min(params.length,
                    RcsProbeMetadataPolicy.MAX_PARAMETER_TYPES);
                for (int i = 0; i < limit; i++) {
                    if (i != 0) signature.append(',');
                    signature.append(params[i].getName());
                }
                if (params.length > limit) signature.append(",...");
                Log.i(TAG, "RCS_TYPES method class=" + name
                    + " name=" + method.getName()
                    + " returns=" + method.getReturnType().getName()
                    + " params=" + signature);
                methodCount++;
            }
            Log.i(TAG, "RCS_TYPES methodSummary class=" + name
                + " inspected=" + methodCount
                + " declaredTotal=" + methods.length);
        } catch (Throwable unavailable) {
            Log.w(TAG, "RCS_TYPES inspect unavailable class=" + name
                + " cause=" + unavailable.getClass().getSimpleName());
        }
    }
}
