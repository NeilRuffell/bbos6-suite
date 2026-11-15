package dev.q25.bbmessages;

/**
 * Pure allowlist policy for the research module. Never attach to another app
 * or a secondary Google Messages process, and never run without opt-in.
 */
final class RcsProbeScope {
    static final String GOOGLE_MESSAGES = "com.google.android.apps.messaging";

    private RcsProbeScope() { }

    static boolean shouldInstall(
        String packageName, String processName, boolean enabled
    ) {
        return enabled && GOOGLE_MESSAGES.equals(packageName)
            && GOOGLE_MESSAGES.equals(processName);
    }
}
