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
