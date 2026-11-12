package dev.q25.bb7pulldown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class QuickSettingsPressPolicyTest {
    @Test public void shortToggleAndLongSettingsForVerifiedConnectivityTiles() {
        for (String spec : new String[] {"wifi", "bt", "cell"}) {
            assertEquals(QuickSettingsPressPolicy.Action.SECONDARY,
                    QuickSettingsPressPolicy.select(spec, true, false));
            assertEquals(QuickSettingsPressPolicy.Action.LONG_PRESS,
                    QuickSettingsPressPolicy.select(spec, true, true));
        }
    }

    @Test public void missingSecondaryActionNeverAttemptsNativeToggle() {
        assertEquals(QuickSettingsPressPolicy.Action.PRIMARY,
                QuickSettingsPressPolicy.select("wifi", false, false));
        assertEquals(QuickSettingsPressPolicy.Action.LONG_PRESS,
                QuickSettingsPressPolicy.select("wifi", false, true));
    }

    @Test public void shadeDismissesOnlyAfterSuccessfulNativeLongPress() {
        assertTrue(QuickSettingsPressPolicy.shouldDismissShade(
                NativeQuickSettingsTiles.Action.LONG_PRESS, true));
        assertFalse(QuickSettingsPressPolicy.shouldDismissShade(
                NativeQuickSettingsTiles.Action.LONG_PRESS, false));
        for (NativeQuickSettingsTiles.Action action : new NativeQuickSettingsTiles.Action[]{
                NativeQuickSettingsTiles.Action.PRIMARY,
                NativeQuickSettingsTiles.Action.SECONDARY}) {
            assertFalse(QuickSettingsPressPolicy.shouldDismissShade(action, true));
            assertFalse(QuickSettingsPressPolicy.shouldDismissShade(action, false));
        }
    }

    @Test public void otherTilesRetainTheirOriginalActions() {
        for (String spec : new String[] {"internet", "flashlight", "custom(app/pkg)"}) {
            assertEquals(QuickSettingsPressPolicy.Action.PRIMARY,
                    QuickSettingsPressPolicy.select(spec, true, false));
            assertEquals(QuickSettingsPressPolicy.Action.LONG_PRESS,
                    QuickSettingsPressPolicy.select(spec, true, true));
        }
    }
}
