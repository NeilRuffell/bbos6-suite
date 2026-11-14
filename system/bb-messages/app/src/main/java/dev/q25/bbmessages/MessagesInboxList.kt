package dev.q25.bbmessages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionItem
import dev.q25.bbos6.ui.compose.Bbos6PageList

/**
 * Application-specific display model. Does not query messages or hold a
 * Google Messages object. IDs and actions must come from a verified bridge.
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
 * The future Google Messages adapter owns the real inbox lifecycle, stable
 * conversation IDs, row updates, click/long-press action dispatch, and teardown.
 * This component makes no assumption about Google Messages internals.
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
