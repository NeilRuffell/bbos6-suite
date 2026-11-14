package dev.q25.bbmessages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionItem
import dev.q25.bbos6.ui.compose.Bbos6PageList

/**
 * Application-specific display model. Does not query messages directly.
 * The Google adapter bridge supplies complete snapshots, temporary position
 * keys and native action callbacks. Those keys are not conversation IDs.
 */
data class MessagesInboxRow(
    val id: String,
    val sender: String,
    val preview: String? = null,
    val timestamp: String? = null
)

/**
 * Compact inbox presentation using the existing BBOS6 list controller.
 *
 * GoogleMessagesAdapterBridge and MessagesInboxPageBridge own target-app
 * data extraction, native action dispatch, refresh and teardown. This
 * composable delegates all scrolling, trackpad/DPAD, touch and selection
 * mechanics to the canonical shared Bbos6PageList used by Files.
 */
@Composable
fun MessagesInboxList(
    rows: List<MessagesInboxRow>,
    onOpenConversation: (String) -> Unit,
    onConversationMenu: ((String) -> Unit)? = null,
    initialSelectedId: String? = null,
    modifier: Modifier = Modifier
) {
    require(rows.all { it.id.isNotBlank() && it.sender.isNotBlank() }) {
        "Inbox rows require a stable ID and a sender label"
    }
    val byId = remember(rows) { rows.associateBy { it.id } }
    require(byId.size == rows.size) { "Duplicate inbox conversation ID" }

    Bbos6PageList(
        items = rows.map { Bbos6ListSelectionItem(id = it.id, label = it.sender) },
        onActivate = onOpenConversation,
        modifier = modifier,
        rowHeightDp = 52,
        textHorizontalPaddingDp = 4,
        secondaryTextForId = { byId[it]?.preview },
        trailingTextForId = { byId[it]?.timestamp },
        onLongPress = onConversationMenu,
        initialSelectedId = initialSelectedId
    )
}
