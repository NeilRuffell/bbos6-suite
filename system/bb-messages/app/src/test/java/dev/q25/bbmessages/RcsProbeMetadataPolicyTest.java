package dev.q25.bbmessages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Only observed Q25 controller dependencies may be structurally described. */
public final class RcsProbeMetadataPolicyTest {
    @Test
    public void allowsObservedReferences() {
        assertTrue(RcsProbeMetadataPolicy.isKnownDirectReference("awei"));
        assertTrue(RcsProbeMetadataPolicy.isKnownDirectReference("aweq"));
        assertTrue(RcsProbeMetadataPolicy.isKnownDirectReference("gvvj"));
        assertTrue(RcsProbeMetadataPolicy.isKnownDirectReference("ujp"));
        assertTrue(RcsProbeMetadataPolicy.isKnownDirectReference("fzii"));
    }

    @Test
    public void excludesAndroidActivityConversationIdAndUnobservedClasses() {
        assertFalse(RcsProbeMetadataPolicy.isKnownDirectReference(
            "com.google.android.apps.messaging.main.MainActivity"
        ));
        assertFalse(RcsProbeMetadataPolicy.isKnownDirectReference(
            "com.google.android.apps.messaging.shared.api.messaging.conversation.bugle.BugleConversationId"
        ));
        assertFalse(RcsProbeMetadataPolicy.isKnownDirectReference("java.lang.String"));
        assertFalse(RcsProbeMetadataPolicy.isKnownDirectReference(
            "j$.util.Optional"
        ));
        assertFalse(RcsProbeMetadataPolicy.isKnownDirectReference("random"));
        assertFalse(RcsProbeMetadataPolicy.isKnownDirectReference(null));
    }
}
