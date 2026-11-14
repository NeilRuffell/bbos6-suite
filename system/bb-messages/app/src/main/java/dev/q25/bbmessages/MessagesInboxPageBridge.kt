package dev.q25.bbmessages

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.lang.ref.WeakReference

/**
 * A real BBOS6 inbox: ONE shared Bbos6PageList controller.
 *
 * Google Messages remains the only SMS/MMS/RCS backend and the owner of the
 * native adapter and conversation actions. The native RecyclerView stays
 * attached, measured and bound, but INVISIBLE underneath our full-page
 * Compose list. We never create an app-specific scroll/selection controller.
 *
 * The Google-specific integration only extracts native rows, refreshes them
 * after native layout, and delegates activation to Google's bound handlers.
 * For an unsupported data/action contract we restore Google's whole inbox.
 *
 * Experimental and opt-in: debug.bbmessages.page.enabled=true
 * Restore stock by setting false and force-stopping Google Messages.
 */
object MessagesInboxPageBridge {
    private const val TAG = "BBMessagesPage"
    private const val TARGET_ACTIVITY =
        "com.google.android.apps.messaging.main.MainActivity"
    private const val NATIVE_RECYCLER =
        "android.support.v7.widget.RecyclerView"
    private var currentSession: WeakReference<Session>? = null

    /**
     * Google adapter bind callback (not a new scrolling/input controller).
     * Coalesce native data refreshes on the main thread.
     */
    @JvmStatic
    fun onNativeAdapterBound() {
        currentSession?.get()?.scheduleRefresh()
    }

    @JvmStatic
    fun tryMount(activity: Activity) {
        if (activity.javaClass.name != TARGET_ACTIVITY) return
        try {
            val recycler = activity.findViewById<View>(android.R.id.list)
            if (recycler !is ViewGroup || recycler.javaClass.name != NATIVE_RECYCLER) return
            val parent = recycler.parent as? LinearLayout ?: return
            val adapter = GoogleMessagesAdapterBridge.resolve(recycler) ?: return

            // No partial inbox. All adapter positions must bind to verified
            // native conversation rows and activation handlers.
            val snapshot = adapter.snapshot()
            if (recycler.parent !== parent || !recycler.isAttachedToWindow) return

            val session = Session(parent, recycler, adapter, snapshot)
            session.mount()
            currentSession = WeakReference(session)
        } catch (failure: Throwable) {
            Log.e(TAG, "Shared inbox mount rejected; Google inbox retained", failure)
        }
    }

    private class Session(
        private val parent: LinearLayout,
        private val recycler: ViewGroup,
        private val adapter: GoogleMessagesAdapterBridge,
        originalRows: List<GoogleMessagesAdapterBridge.NativeConversationRow>
    ) {
        private val owner = InboxRowCompositionOwner()
        private val originalLayoutParams = recycler.layoutParams
        private val originalVisibility = recycler.visibility
        private val originalFocusable = recycler.isFocusable
        private val originalFocusableInTouchMode = recycler.isFocusableInTouchMode
        private val originalIndex = parent.indexOfChild(recycler)
        private val host = FrameLayout(parent.context)
        private val compose = ComposeView(parent.context)
        private var nativeRows = originalRows
        private var displayRows by mutableStateOf(asDisplayRows(originalRows))
        private var revision by mutableIntStateOf(0)
        private var scheduled = false
        private var closed = false
        private var restoring = false

        private val refreshListener = View.OnLayoutChangeListener {
                _, _, _, _, _, _, _, _, _ ->
            scheduleRefresh()
        }
        private val countObserver = ViewTreeObserver.OnPreDrawListener {
            // Adapter updates which remove the last native row may not
            // trigger any bind callback. Check only the integer count here.
            // Snapshot work always remains deferred after this draw pass.
            if (!closed) {
                try {
                    if (adapter.itemCount() != nativeRows.size) scheduleRefresh()
                } catch (failure: Throwable) {
                    scheduleRefresh()
                }
            }
            true
        }
        private var observedTree: ViewTreeObserver? = null

        fun mount() {
            if (originalIndex < 0) return
            try {
                host.setViewTreeLifecycleOwner(owner)
                host.setViewTreeSavedStateRegistryOwner(owner)
                host.setViewTreeViewModelStoreOwner(owner)
                compose.setViewTreeLifecycleOwner(owner)
                compose.setViewTreeSavedStateRegistryOwner(owner)
                compose.setViewTreeViewModelStoreOwner(owner)
                compose.isFocusable = true
                compose.isFocusableInTouchMode = true
                compose.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // All selection, touch and scroll behavior is owned by
                // Bbos6PageList. No custom row-scrolling implementation.
                compose.setContent {
                    val rows = displayRows
                    val generation = revision
                    key(generation) {
                        MessagesInboxList(
                            rows = rows,
                            onOpenConversation = { id -> activate(id, false) },
                            onConversationMenu = { id -> activate(id, true) }
                        )
                    }
                }

                parent.removeView(recycler)
                host.layoutParams = originalLayoutParams
                parent.addView(host, originalIndex)

                recycler.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                host.addView(recycler)
                // INVISIBLE still receives layout/bind updates, unlike GONE,
                // and does not receive touch/focus or draw over Compose.
                recycler.visibility = View.INVISIBLE
                recycler.isFocusable = false
                recycler.isFocusableInTouchMode = false
                host.addView(compose)
                recycler.addOnLayoutChangeListener(refreshListener)
                observedTree = host.viewTreeObserver.takeIf { it.isAlive }
                observedTree?.addOnPreDrawListener(countObserver)

                host.addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) = Unit
                        override fun onViewDetachedFromWindow(v: View) {
                            // On Activity teardown release our compose lifecycle.
                            // Rollback never mutates Google's conversation data.
                            if (!restoring) rollback()
                        }
                    }
                )
                Log.i(TAG, "Mounted shared Bbos6PageList count=" + nativeRows.size)
            } catch (failure: Throwable) {
                rollback()
                throw failure
            }
        }

        fun scheduleRefresh() {
            if (closed || scheduled) return
            scheduled = true
            host.post {
                scheduled = false
                refresh()
            }
        }

        private fun refresh() {
            if (closed || !recycler.isAttachedToWindow) return
            try {
                val latest = adapter.snapshot()
                val presentation = asDisplayRows(latest)
                // Position IDs cannot be treated as stable conversation IDs.
                // Reset shared selection whenever Google's dataset changes.
                nativeRows = latest
                if (presentation != displayRows) {
                    displayRows = presentation
                    revision++
                    Log.i(TAG, "Refreshed shared inbox count=" + presentation.size)
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "Native dataset refresh failed; restoring Google inbox", failure)
                rollback()
            }
        }

        private fun activate(id: String, longPress: Boolean) {
            if (closed) return
            val current = nativeRows.firstOrNull { it.id == id }
            if (current == null || !adapter.activate(current, longPress)) {
                Log.w(TAG, "Native conversation action unavailable; restoring Google inbox")
                rollback()
            }
        }

        private fun rollback() {
            if (restoring) return
            restoring = true
            release()
            try {
                if (recycler.parent === host) host.removeView(recycler)
                if (host.parent === parent) {
                    val index = parent.indexOfChild(host)
                    parent.removeView(host)
                    parent.addView(recycler, index, originalLayoutParams)
                } else if (recycler.parent == null && parent.isAttachedToWindow) {
                    parent.addView(
                        recycler,
                        originalIndex.coerceAtMost(parent.childCount),
                        originalLayoutParams
                    )
                }
                recycler.visibility = originalVisibility
                recycler.isFocusable = originalFocusable
                recycler.isFocusableInTouchMode = originalFocusableInTouchMode
                Log.i(TAG, "Restored native Google inbox")
            } catch (failure: Throwable) {
                Log.e(TAG, "Could not restore native Google inbox view", failure)
            }
        }

        private fun release() {
            if (closed) return
            closed = true
            if (currentSession?.get() === this) currentSession = null
            recycler.removeOnLayoutChangeListener(refreshListener)
            observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(countObserver)
            observedTree = null
            try {
                compose.disposeComposition()
            } finally {
                owner.destroy()
            }
        }

        private fun asDisplayRows(
            native: List<GoogleMessagesAdapterBridge.NativeConversationRow>
        ): List<MessagesInboxRow> = native.map {
            MessagesInboxRow(
                id = it.id,
                sender = it.sender,
                preview = it.preview?.takeIf(String::isNotBlank),
                timestamp = it.timestamp?.takeIf(String::isNotBlank)
            )
        }
    }
}
