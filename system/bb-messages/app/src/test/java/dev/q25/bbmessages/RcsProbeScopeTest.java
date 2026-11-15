package dev.q25.bbmessages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The probe must remain opt-in and isolated to one target process. */
public final class RcsProbeScopeTest {
    @Test
    public void disabledProbeNeverInstalls() {
        assertFalse(RcsProbeScope.shouldInstall(
            RcsProbeScope.GOOGLE_MESSAGES, RcsProbeScope.GOOGLE_MESSAGES, false
        ));
    }

    @Test
    public void requiresExactMainGoogleMessagesProcess() {
        assertTrue(RcsProbeScope.shouldInstall(
            RcsProbeScope.GOOGLE_MESSAGES, RcsProbeScope.GOOGLE_MESSAGES, true
        ));
        assertFalse(RcsProbeScope.shouldInstall(
            RcsProbeScope.GOOGLE_MESSAGES,
            RcsProbeScope.GOOGLE_MESSAGES + ":secondary", true
        ));
        assertFalse(RcsProbeScope.shouldInstall(
            "com.android.messaging", RcsProbeScope.GOOGLE_MESSAGES, true
        ));
        assertFalse(RcsProbeScope.shouldInstall(
            RcsProbeScope.GOOGLE_MESSAGES, "com.android.systemui", true
        ));
    }
}
