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
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
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
 * The Google-specific integration extracts native rows and their typed
 * Google-owned conversation models, and refreshes after native layout.
 * A guarded Google controller bridge replaces the rejected detached
 * ViewHolder.performClick. For any unsupported contract, restore stock UI.
 *
 * Experimental and opt-in: debug.bbmessages.shared.page.enabled=true
 * Restore stock by setting false and force-stopping Google Messages.
 */
object MessagesInboxPageBridge {
    private const val TAG = "BBMessagesPage"
    private const val TARGET_ACTIVITY =
        "com.google.android.apps.messaging.main.MainActivity"
    private const val NATIVE_RECYCLER =
        "android.support.v7.widget.RecyclerView"
    private var currentSession: WeakReference<Session>? = null
    // Bounded wait for Google's asynchronous inbox population, without
    // painting the rejected native-Google-to-BBOS6 transition.
    private const val SOURCE_DRAW_ATTEMPTS = 90
    private val observedActivities =
        java.util.Collections.newSetFromMap(
            java.util.WeakHashMap<Activity, Boolean>()
        )

    /**
     * Google adapter bind callback (not a new scrolling/input controller).
     * Coalesce native data refreshes on the main thread.
     */
    @JvmStatic
    fun onNativeAdapterBound() {
        currentSession?.get()?.scheduleRefresh()
    }

    /**
     * Register while Activity.onResume is returning, before first draw.
     * Do not post a 750ms overlay over an already painted Google inbox.
     * If Google has not supplied a valid adapter snapshot within a bounded
     * number of draw passes, fail closed to the unmodified Google UI.
     */
    @JvmStatic
    fun watchFirstDraw(activity: Activity) {
        if (activity.javaClass.name != TARGET_ACTIVITY ||
            !observedActivities.add(activity)) return
        val decor = activity.window?.decorView ?: return
        val tree = decor.viewTreeObserver
        if (!tree.isAlive) return
        var passes = 0
        var lastSnapshotAt = 0L
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (activity.isFinishing || activity.isDestroyed) {
                    if (tree.isAlive) tree.removeOnPreDrawListener(this)
                    return true
                }
                if (currentSession?.get() != null) {
                    if (tree.isAlive) tree.removeOnPreDrawListener(this)
                    return true
                }
                // Snapshotting can bind many native holders. Retry at
                // bounded intervals, not on every 16ms pre-draw pass.
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastSnapshotAt >= 150L) {
                    lastSnapshotAt = now
                    if (tryMount(activity)) {
                        if (tree.isAlive) tree.removeOnPreDrawListener(this)
                        Log.i(TAG, "SOURCE_PAGE mounted before first native draw")
                        decor.postInvalidateOnAnimation()
                        return false
                    }
                }
                passes++
                if (passes >= SOURCE_DRAW_ATTEMPTS) {
                    if (tree.isAlive) tree.removeOnPreDrawListener(this)
                    Log.w(TAG, "SOURCE_PAGE unavailable; retained stock Google inbox")
                    return true
                }
                decor.postInvalidateOnAnimation()
                return false
            }
        }
        tree.addOnPreDrawListener(listener)
        Log.i(TAG, "SOURCE_PAGE watching first draw")
    }

    @JvmStatic
    fun tryMount(activity: Activity): Boolean {
        if (activity.javaClass.name != TARGET_ACTIVITY) return false
        try {
            val recycler = activity.findViewById<View>(android.R.id.list)
            if (recycler !is ViewGroup || recycler.javaClass.name != NATIVE_RECYCLER)
                return false
            val parent = recycler.parent as? LinearLayout ?: return false
            val adapter = GoogleMessagesAdapterBridge.resolve(recycler) ?: return false

            // No partial inbox. All adapter positions must bind to verified
            // native conversation rows and activation handlers.
            val snapshot = try {
                adapter.snapshot()
            } catch (failure: Throwable) {
                Log.d(TAG, "SOURCE_PAGE snapshot not ready: " +
                    failure.javaClass.simpleName)
                // No repeated per-row diagnostics in a pre-draw retry loop.
                return false
            }
            if (recycler.parent !== parent || !recycler.isAttachedToWindow)
                return false

            val session = Session(
                activity, parent, recycler, adapter, snapshot, adapter.itemCount()
            )
            session.mount()
            currentSession = WeakReference(session)
            return true
        } catch (failure: Throwable) {
            Log.w(TAG, "SOURCE_PAGE native contract rejected; Google inbox retained",
                failure)
            return false
        }
    }

    private class Session(
        private val activity: Activity,
        private val parent: LinearLayout,
        private val recycler: ViewGroup,
        private val adapter: GoogleMessagesAdapterBridge,
        originalRows: List<GoogleMessagesAdapterBridge.NativeConversationRow>,
        initialNativeItemCount: Int
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
        // The adapter can contain noninteractive Lottie rows. Its total
        // count is NOT the displayed conversation count; comparing these
        // would schedule a refresh every frame.
        private var nativeAdapterItemCount = initialNativeItemCount
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
                    if (adapter.itemCount() != nativeAdapterItemCount) scheduleRefresh()
                } catch (failure: Throwable) {
                    scheduleRefresh()
                }
            }
            true
        }
        private var observedTree: ViewTreeObserver? = null

        fun mount() {
            if (originalIndex < 0) throw IllegalStateException("Native inbox not attached")
            try {
                host.setViewTreeLifecycleOwner(owner)
                host.setViewTreeSavedStateRegistryOwner(owner)
                host.setViewTreeViewModelStoreOwner(owner)
                compose.setViewTreeLifecycleOwner(owner)
                compose.setViewTreeSavedStateRegistryOwner(owner)
                compose.setViewTreeViewModelStoreOwner(owner)
                // ComposeView normally creates a recomposer from the topmost
                // Google-owned window content view. Google Messages does not
                // expose a compatible AndroidX ViewTreeLifecycleOwner there,
                // even though our FrameLayout and ComposeView have one.
                //
                // Supply the documented lifecycle-aware recomposer explicitly
                // as this view's parent composition context. This keeps the
                // lifecycle entirely within our injected host, without
                // tagging or mutating Google's outer view hierarchy.
                compose.setParentCompositionContext(
                    host.createLifecycleAwareWindowRecomposer(
                        lifecycle = owner.lifecycle
                    )
                )
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
                        override fun onViewAttachedToWindow(v: View) {
                            // The inbox can detach during Google's own
                            // conversation navigation, then return on Back.
                            // Revalidate the live native adapter on reattach.
                            if (!closed && !restoring) scheduleRefresh()
                        }
                        override fun onViewDetachedFromWindow(v: View) {
                            // A temporary fragment/screen transition is NOT
                            // an error: don't replace BBOS6 with stock Google
                            // when the user opens a conversation and presses Back.
                            if (activity.isFinishing || activity.isDestroyed) {
                                release()
                            }
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
                val refreshedItemCount = adapter.itemCount()
                val presentation = asDisplayRows(latest)
                // Position keys are NOT conversation IDs. Compare the
                // previous typed Google models before replacing the snapshot,
                // including updates with unchanged sender/preview text.
                val identityChanged = nativeRows.size != latest.size ||
                    nativeRows.zip(latest).any { (before, after) ->
                        before.index != after.index ||
                            !GoogleMessagesControllerNavigation.sameConversation(
                                before.model, after.model
                            )
                    }
                nativeRows = latest
                nativeAdapterItemCount = refreshedItemCount
                if (presentation != displayRows || identityChanged) {
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
            val countStable = try {
                adapter.itemCount() == nativeAdapterItemCount
            } catch (_: Throwable) {
                false
            }
            if (!countStable || current == null || !adapter.activate(current, longPress)) {
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
