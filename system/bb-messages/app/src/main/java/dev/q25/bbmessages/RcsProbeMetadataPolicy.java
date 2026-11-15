package dev.q25.bbmessages;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Q25-observed runtime reference types; metadata inspection is opt-in only. */
final class RcsProbeMetadataPolicy {
    private static final Set<String> CANDIDATES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "fzii", "fzbv", "affa", "awei", "aweq", "gvvj",
            "ucc", "ujp", "yw"
        ))
    );

    // Q25 00:39: ucc declares these six types directly, with no
    // evidence yet about whether they belong to RCS.
    private static final Set<String> UCC_DECLARED_TYPES =
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "qe", "pk", "ujo", "ujl", "hbno", "uji"
        )));

    static final int MAX_DECLARED_TYPE_CLASSES = 6;
    static final int MAX_DECLARED_TYPE_FIELDS = 16;
    static final int MAX_DECLARED_TYPE_METHODS = 32;

    static boolean isObservedDeclaredTypeSource(String source) {
        return "ucc".equals(source);
    }

    static boolean isObservedUccDeclaredType(String typeName) {
        return UCC_DECLARED_TYPES.contains(typeName);
    }

    static final int MAX_CLASSES = 9;
    static final int MAX_FIELDS_PER_CLASS = 16;
    static final int MAX_METHODS_PER_CLASS = 24;
    static final int MAX_INTERFACES_PER_CLASS = 6;
    static final int MAX_PARAMETER_TYPES = 6;

    private RcsProbeMetadataPolicy() { }

    static boolean isKnownDirectReference(String runtimeClassName) {
        return CANDIDATES.contains(runtimeClassName);
    }
}
