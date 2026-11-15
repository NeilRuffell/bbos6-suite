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
    // Every MainActivity needs a persistent pre-draw observer, not a
    // one-shot watcher removed after the initial inbox mount. Google can
    // recreate its RecyclerView without recreating the Activity on Back.
    private val monitors =
        java.util.Collections.synchronizedMap(
            java.util.WeakHashMap<Activity, InboxDrawMonitor>()
        )

    /** Coalesce native adapter callbacks; list mechanics stay shared. */
    @JvmStatic
    fun onNativeAdapterBound() {
        currentSession?.get()?.scheduleRefresh()
    }

    /**
     * Register on every MainActivity resume and retain the monitor across
     * fragment transitions. It performs no work when the conversation
     * screen is showing and no VISIBLE native inbox exists.
     */
    @JvmStatic
    fun watchFirstDraw(activity: Activity) {
        if (activity.javaClass.name != TARGET_ACTIVITY) return
        val monitor = synchronized(monitors) {
            monitors[activity] ?: InboxDrawMonitor(activity).also {
                monitors[activity] = it
            }
        }
        monitor.observeCurrentTree()
    }

    private class InboxDrawMonitor(activity: Activity) :
        ViewTreeObserver.OnPreDrawListener {
        private val activityRef = WeakReference(activity)
        private var tree: ViewTreeObserver? = null
        private var attempts = 0
        private var lastSnapshotAt = 0L
        private var fallbackLogged = false
        private var insideMount = false

        fun observeCurrentTree() {
            val current = activityRef.get()?.window?.decorView
                ?.viewTreeObserver?.takeIf { it.isAlive } ?: return
            if (tree === current) return
            tree?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            current.addOnPreDrawListener(this)
            tree = current
            Log.i(TAG, "SOURCE_PAGE persistent inbox draw monitor attached")
        }

        private fun detach() {
            tree?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            tree = null
            activityRef.get()?.let { activity ->
                synchronized(monitors) {
                    if (monitors[activity] === this) monitors.remove(activity)
                }
            }
        }

        override fun onPreDraw(): Boolean {
            if (insideMount) return true
            val activity = activityRef.get()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                detach()
                return true
            }

            // This is the decisive Back fix: the one-time startup watcher
            // was previously removed after mounting, leaving later Google
            // inbox RecyclerViews completely unskinned on Back.
            // A visible stock inbox is a remount candidate on EVERY draw;
            // the hidden RecyclerView inside our own host is not.
            val recycler = findVisibleNativeInbox(activity.window?.decorView)
            if (recycler == null) {
                attempts = 0
                lastSnapshotAt = 0L
                fallbackLogged = false
                return true
            }

            val now = android.os.SystemClock.uptimeMillis()
            if (attempts < SOURCE_DRAW_ATTEMPTS &&
                now - lastSnapshotAt >= 150L) {
                lastSnapshotAt = now
                insideMount = true
                val mounted = try {
                    tryMount(activity, recycler)
                } finally {
                    insideMount = false
                }
                if (mounted) {
                    attempts = 0
                    fallbackLogged = false
                    Log.i(TAG, "SOURCE_PAGE intercepted native inbox before draw"
                        + " returnMount=true")
                    activity.window?.decorView?.postInvalidateOnAnimation()
                    return false
                }
            }
            attempts++
            if (attempts >= SOURCE_DRAW_ATTEMPTS) {
                if (!fallbackLogged) {
                    fallbackLogged = true
                    Log.w(TAG, "SOURCE_PAGE unsupported inbox retained for safety"
                        + " after bounded draw attempts")
                }
                return true
            }
            activity.window?.decorView?.postInvalidateOnAnimation()
            return false
        }
    }

    /**
     * Inspect only the visible, attached native inbox, not the hidden
     * recycler retained inside the BBOS6 host or a conversation thread.
     * This handles returning fragments that allocate a NEW recycler with
     * the same android.R.id.list, which findViewById would otherwise miss.
     */
    private fun findVisibleNativeInbox(root: View?): ViewGroup? {
        if (root == null) return null
        var inspected = 0
        fun find(view: View, depth: Int): ViewGroup? {
            if (depth > 24 || ++inspected > 1500 || !view.isShown) return null
            if (view is ViewGroup) {
                if (view.id == android.R.id.list &&
                    view.javaClass.name == NATIVE_RECYCLER &&
                    view.parent is LinearLayout && view.isAttachedToWindow &&
                    GoogleMessagesAdapterBridge.resolve(view) != null) {
                    return view
                }
                for (i in 0 until view.childCount) {
                    find(view.getChildAt(i), depth + 1)?.let { return it }
                }
            }
            return null
        }
        return find(root, 0)
    }

    @JvmStatic
    fun tryMount(activity: Activity): Boolean =
        findVisibleNativeInbox(activity.window?.decorView)?.let {
            tryMount(activity, it)
        } ?: false

    private fun tryMount(activity: Activity, recycler: ViewGroup): Boolean {
        if (activity.javaClass.name != TARGET_ACTIVITY) return false
        try {
            if (!recycler.isAttachedToWindow || !recycler.isShown ||
                recycler.javaClass.name != NATIVE_RECYCLER) return false
            val parent = recycler.parent as? LinearLayout ?: return false
            val adapter = GoogleMessagesAdapterBridge.resolve(recycler) ?: return false
            // Recreated native inbox: do not confuse a stale detached
            // session with a mounted page. Keep the stock row hidden as
            // soon as this new shared session is installed.
            currentSession?.get()?.let { previous ->
                if (!previous.owns(recycler)) previous.abandonIfDetached()
            }

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

        fun owns(candidate: ViewGroup): Boolean =
            !closed && recycler === candidate && recycler.parent === host &&
                host.parent === parent

        fun abandonIfDetached() {
            if (!closed && !host.isAttachedToWindow) {
                Log.i(TAG, "SOURCE_PAGE retiring detached native inbox session")
                release()
            }
        }

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
                            Log.i(TAG, "SOURCE_PAGE shared inbox host reattached"
                                + " closed=" + closed)
                            if (!closed && !restoring) scheduleRefresh()
                        }
                        override fun onViewDetachedFromWindow(v: View) {
                            Log.i(TAG, "SOURCE_PAGE shared inbox host detached"
                                + " finishing=" + activity.isFinishing
                                + " destroyed=" + activity.isDestroyed)
                            // Google may remove the inbox fragment when a
                            // thread opens and allocate a fresh native inbox
                            // when Back is pressed. The persistent pre-draw
                            // monitor remounts BBOS6 before that new inbox
                            // can become visible.
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
                            (before.model == null) != (after.model == null) ||
                            (before.model != null && after.model != null &&
                                !GoogleMessagesControllerNavigation.sameConversation(
                                    before.model, after.model
                                ))
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
            // With no independently readable Google ConversationId, we
            // only allow delegation when this displayed tuple is unique.
            // A duplicate could refer to another thread after a reorder;
            // in that case the stock inbox is safer than an inferred ID.
            val uniquePresentation = if (current == null) false else
                nativeRows.count {
                    it.sender == current.sender &&
                        it.preview == current.preview &&
                        it.timestamp == current.timestamp
                } == 1
            if (!countStable || !uniquePresentation || current == null ||
                !adapter.activate(current, longPress)) {
                Log.w(TAG, "Native conversation action unavailable or ambiguous; restoring Google inbox")
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
                Log.i(TAG, "SOURCE_PAGE fallback restored native Google inbox")
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
