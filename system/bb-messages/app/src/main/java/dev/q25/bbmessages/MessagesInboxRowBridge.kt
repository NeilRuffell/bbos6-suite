package dev.q25.bbmessages

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import dev.q25.bbos6.ui.compose.Bbos6MediaListRow

/**
 * Experimental, read-only visual bridge to a verified native
 * ConversationListItemView inside Google Messages' existing RecyclerView.
 *
 * Google owns the RecyclerView, data binding, scrolling, accessibility and
 * row click/long-click actions. This paints the *canonical shared* BBOS6
 * row face into a non-interactive ViewGroupOverlay. No second list controller
 * or message repository is created.
 *
 * This bridge must be explicitly enabled by the Vector module's system
 * property. Disable scope or revert the property for immediate rollback.
 */
internal object MessagesInboxRowBridge {
    private const val TAG = "BBMessagesInbox"
    private const val GOOGLE_PACKAGE = "com.google.android.apps.messaging"
    private const val ROW_HEIGHT_DP = 52

    @JvmStatic
    fun attachToRow(row: ViewGroup) {
        val painter = RowPainter(row)
        row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            painter.sync()
        }
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                painter.registerFocusObserver()
                row.post { painter.sync() }
            }

            override fun onViewDetachedFromWindow(view: View) {
                painter.unregisterFocusObserver()
            }
        })
        if (row.isAttachedToWindow) {
            painter.registerFocusObserver()
            row.post { painter.sync() }
        }
    }

    private data class FaceText(
        val sender: String,
        val preview: String?,
        val timestamp: String?
    )

    private class RowPainter(private val row: ViewGroup) {
        private var text by mutableStateOf(FaceText("", null, null))
        private var focused by mutableStateOf(false)
        private var composeView: ComposeView? = null
        private var senderField: TextView? = null
        private var previewField: TextView? = null
        private var timestampField: TextView? = null
        private var focusObserver: ViewTreeObserver? = null
        private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
            focused = row.hasFocus()
        }

        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                row.post { updateText() }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        fun registerFocusObserver() {
            if (focusObserver != null || !row.viewTreeObserver.isAlive) return
            focusObserver = row.viewTreeObserver
            focusObserver?.addOnGlobalFocusChangeListener(focusListener)
            focused = row.hasFocus()
        }

        fun unregisterFocusObserver() {
            val observer = focusObserver
            if (observer != null && observer.isAlive) {
                observer.removeOnGlobalFocusChangeListener(focusListener)
            }
            focusObserver = null
        }

        fun sync() {
            try {
                if (!isVerifiedInboxRow() || row.width <= 0 || row.height <= 0) return
                val sender = findField("conversation_name") ?: return
                watchSender(sender)
                val preview = findField("conversation_snippet")
                if (preview !== previewField) {
                    previewField?.removeTextChangedListener(watcher)
                    previewField = preview
                    previewField?.addTextChangedListener(watcher)
                }

                val timestamp = findTimestamp()
                if (timestamp !== timestampField) {
                    timestampField?.removeTextChangedListener(watcher)
                    timestampField = timestamp
                    timestampField?.addTextChangedListener(watcher)
                }

                updateText()
                if (composeView == null) installPaintOverlay()
                resizeRow()
                syncOverlaySize()
                focused = row.hasFocus()
            } catch (failure: Throwable) {
                Log.e(TAG, "Failed to paint inbox row; native row preserved", failure)
            }
        }

        private fun watchSender(field: TextView) {
            if (field === senderField) return
            senderField?.removeTextChangedListener(watcher)
            senderField = field
            senderField?.addTextChangedListener(watcher)
        }

        private fun updateText() {
            val sender = senderField?.text?.toString().orEmpty()
            if (sender.isBlank()) return
            text = FaceText(
                sender = sender,
                preview = previewField?.text?.toString()?.takeIf { it.isNotBlank() },
                timestamp = timestampField?.text?.toString()?.takeIf { it.isNotBlank() }
            )
        }

        private fun isVerifiedInboxRow(): Boolean {
            var parent = row.parent
            while (parent is View) {
                if (parent.id == android.R.id.list &&
                    parent.javaClass.name == "android.support.v7.widget.RecyclerView"
                ) return true
                parent = parent.parent
            }
            return false
        }

        private fun findField(name: String): TextView? {
            val id = row.resources.getIdentifier(name, "id", GOOGLE_PACKAGE)
            return if (id == 0) null else row.findViewById<View>(id) as? TextView
        }

        // The inspection log verifies the timestamp container, not its
        // text-view implementation. Treat a missing text child as absent.
        private fun findTimestamp(): TextView? {
            val id = row.resources.getIdentifier(
                "conversation_timestamp_and_unread_badge", "id", GOOGLE_PACKAGE
            )
            val container = if (id == 0) null
                else row.findViewById<View>(id)
            return descendantTextView(container)
        }

        private fun descendantTextView(view: View?): TextView? {
            if (view is TextView) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    descendantTextView(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }

        private fun installPaintOverlay() {
            val paint = ComposeView(row.context).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setContent { PaintFace() }
            }
            // A ViewGroupOverlay does not receive input. The original row
            // remains the only touch, trackpad, click and scroll owner.
            row.overlay.add(paint)
            composeView = paint
            Log.i(TAG, "Mounted BBOS6 row face in native inbox")
        }

        @Composable
        private fun PaintFace() {
            val data = text
            val hasFocus = focused
            Bbos6MediaListRow(
                title = data.sender,
                subtitle = data.preview,
                duration = data.timestamp,
                trackNumber = null,
                showArtwork = false,
                artwork = null,
                artworkContentDescription = "",
                menuArtwork = false,
                highlighted = hasFocus,
                showRemoveAction = false,
                onRemove = null,
                rowHeightDp = ROW_HEIGHT_DP,
                textHorizontalPaddingDp = 8
            )
        }

        private fun resizeRow() {
            val targetHeight = (ROW_HEIGHT_DP * row.resources.displayMetrics.density)
                .toInt().coerceAtLeast(1)
            val params = row.layoutParams ?: return
            if (params.height != targetHeight) {
                params.height = targetHeight
                row.layoutParams = params
            }
        }

        private fun syncOverlaySize() {
            val view = composeView ?: return
            val width = row.width
            val height = row.height
            if (width <= 0 || height <= 0) return
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
        }
    }
}
