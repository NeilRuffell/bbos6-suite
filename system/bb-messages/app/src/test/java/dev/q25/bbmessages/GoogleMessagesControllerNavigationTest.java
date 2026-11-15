package dev.q25.bbmessages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure equality/owner contract; no Google app or message data required. */
public final class GoogleMessagesControllerNavigationTest {
    @Test
    public void sameModelAndControllerAcceptsEqualIds() {
        Object controller = new Object();
        GoogleMessagesControllerNavigation.BoundModel snapshot =
            new GoogleMessagesControllerNavigation.BoundModel(controller, "model-a");
        GoogleMessagesControllerNavigation.BoundModel refreshed =
            new GoogleMessagesControllerNavigation.BoundModel(
                controller, new String("model-a")
            );
        assertTrue(GoogleMessagesControllerNavigation.sameConversation(
            snapshot, refreshed
        ));
    }

    @Test
    public void changedControllerOrIdFailsClosed() {
        Object controller = new Object();
        GoogleMessagesControllerNavigation.BoundModel snapshot =
            new GoogleMessagesControllerNavigation.BoundModel(controller, "model-a");
        assertFalse(GoogleMessagesControllerNavigation.sameConversation(
            snapshot, new GoogleMessagesControllerNavigation.BoundModel(
                controller, "model-b"
            )
        ));
        assertFalse(GoogleMessagesControllerNavigation.sameConversation(
            snapshot, new GoogleMessagesControllerNavigation.BoundModel(
                new Object(), "model-a"
            )
        ));
        assertFalse(GoogleMessagesControllerNavigation.sameConversation(snapshot, null));
        assertFalse(GoogleMessagesControllerNavigation.sameConversation(
            new GoogleMessagesControllerNavigation.BoundModel(controller, null),
            snapshot
        ));
    }
}
