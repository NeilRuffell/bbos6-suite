package dev.q25.bbmessages

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.q25.bbos6.ui.compose.Bbos6MediaListRow
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.lang.ref.WeakReference

/**
 * Opt-in BBOS6 rendering *inside* Google's existing conversation row.
 *
 * The Google RecyclerView, holder, adapter, conversation data, row click and
 * long-click listeners remain untouched. No ViewGroupOverlay, second list,
 * synthetic click, alternate navigation, or message store is created.
 *
 * Only enabled by debug.bbmessages.native.rows.enabled on the Q25.
 * On failure the original Google row children retain their alpha/layout.
 */
object MessagesInboxNativeRowBridge {
    private const val TAG = "BBMessagesNativeRows"
    private const val ROW_CLASS =
        "com.google.android.apps.messaging.home.list.ConversationListItemView"
    private const val PACKAGE = "com.google.android.apps.messaging"
    private const val ROW_DP = 52
    // Weak VALUE references prevent the map retaining the row through a
    // painter -> row backreference. Native row listeners retain their painter.
    private val painters = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, WeakReference<NativePainter>>()
    )

    /** Called strictly after Google's verified vv.g(aac,int) row bind. */
    @JvmStatic
    fun onNativeBound(holder: Any) {
        if (holder.javaClass.name != "asvj") return
        try {
            val field = holder.javaClass.getDeclaredField("a")
            field.isAccessible = true
            val row = field.get(holder) as? ViewGroup ?: return
            if (row.javaClass.name != ROW_CLASS) return
            val painter = synchronized(painters) {
                painters[row]?.get() ?: NativePainter(row).also {
                    painters[row] = WeakReference(it)
                }
            }
            painter.bind()
        } catch (failure: Throwable) {
            Log.w(TAG, "Native row bridge unavailable: ${failure.javaClass.simpleName}")
        }
    }

    private data class Face(val title: String, val subtitle: String?, val timestamp: String?)

    private class NativePainter(private val row: ViewGroup) {
        private var face by mutableStateOf(Face("", null, null))
        private var focused by mutableStateOf(false)
        private var compose: ComposeView? = null
        private var owner: InboxRowCompositionOwner? = null
        private var name: TextView? = null
        private var snippet: TextView? = null
        private var clock: TextView? = null
        private val originalAlpha = IdentityHashMap<View, Float>()
        private var originalHeight: Int? = null
        private var failed = false
        private var focusedObserver: ViewTreeObserver? = null

        private val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateText()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
            focused = row.hasFocus()
        }
        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            setNativeRowHeight()
            positionFace()
        }
        private val attachmentListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                observeFocus()
                setNativeRowHeight()
                positionFace()
            }
            override fun onViewDetachedFromWindow(view: View) {
                stopFocusObserver()
                // Keep the native child across recycling; Google's next bind
                // updates its text without tearing down the Compose surface.
            }
        }

        fun bind() {
            if (failed) return
            try {
                val title = field("conversation_name") ?: return
                updateWatchers(
                    title,
                    field("conversation_snippet"),
                    timestamp()
                )
                updateText()
                if (face.title.isBlank()) return
                if (compose == null) installFace()
                else concealNativeChildren()
                if (row.isAttachedToWindow) observeFocus()
                focused = row.hasFocus()
            } catch (failure: Throwable) {
                failed = true
                rollback()
                Log.e(TAG, "Native BBOS6 row unavailable; restored Google row", failure)
            }
        }

        private fun installFace() {
            val localOwner = InboxRowCompositionOwner()
            var newCompose: ComposeView? = null
            try {
                // ComposeView is final and cannot override input dispatch.
                // Render only passive shared-row content; retain Google's
                // existing native row listeners and validate input on Q25.
                val skin = ComposeView(row.context).apply {
                    isClickable = false
                    isLongClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setViewTreeLifecycleOwner(localOwner)
                    setViewTreeSavedStateRegistryOwner(localOwner)
                    setViewTreeViewModelStoreOwner(localOwner)
                    setParentCompositionContext(
                        row.createLifecycleAwareWindowRecomposer(lifecycle = localOwner.lifecycle)
                    )
                    setContent { NativeFace() }
                }
                newCompose = skin
                row.addView(
                    skin,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                compose = skin
                owner = localOwner
                row.addOnAttachStateChangeListener(attachmentListener)
                row.addOnLayoutChangeListener(layoutListener)
                concealNativeChildren()
                setNativeRowHeight()
                positionFace()
                Log.i(TAG, "NATIVE_ROW mounted shared Bbos6MediaListRow")
            } catch (failure: Throwable) {
                if (newCompose != null) {
                    try { row.removeView(newCompose) } catch (_: Throwable) { }
                    newCompose.disposeComposition()
                }
                localOwner.destroy()
                throw failure
            }
        }

        /** Native Google data and listeners remain bound, just not painted. */
        private fun concealNativeChildren() {
            val skin = compose ?: return
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                if (child !== skin) {
                    if (!originalAlpha.containsKey(child)) originalAlpha[child] = child.alpha
                    child.alpha = 0f
                }
            }
        }

        private fun positionFace() {
            val skin = compose ?: return
            if (row.width <= 0 || row.height <= 0) return
            try {
                val w = row.width
                val h = row.height
                if (skin.measuredWidth != w || skin.measuredHeight != h) {
                    skin.measure(
                        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                    )
                }
                skin.layout(0, 0, w, h)
                skin.bringToFront()
            } catch (failure: Throwable) {
                failed = true
                rollback()
                Log.w(TAG, "NATIVE_ROW face layout failed: ${failure.javaClass.simpleName}")
            }
        }

        private fun setNativeRowHeight() {
            val heightPx = (ROW_DP * row.resources.displayMetrics.density).toInt()
            val params = row.layoutParams ?: return
            if (originalHeight == null) originalHeight = params.height
            if (params.height != heightPx) {
                params.height = heightPx
                row.layoutParams = params
            }
        }

        private fun updateWatchers(
            newName: TextView, newSnippet: TextView?, newClock: TextView?
        ) {
            if (name !== newName) {
                name?.removeTextChangedListener(textWatcher)
                name = newName
                newName.addTextChangedListener(textWatcher)
            }
            if (snippet !== newSnippet) {
                snippet?.removeTextChangedListener(textWatcher)
                snippet = newSnippet
                newSnippet?.addTextChangedListener(textWatcher)
            }
            if (clock !== newClock) {
                clock?.removeTextChangedListener(textWatcher)
                clock = newClock
                newClock?.addTextChangedListener(textWatcher)
            }
        }

        private fun updateText() {
            val title = name?.text?.toString().orEmpty()
            if (title.isBlank()) return
            face = Face(
                title,
                snippet?.text?.toString()?.takeIf(String::isNotBlank),
                clock?.text?.toString()?.takeIf(String::isNotBlank)
            )
        }

        private fun field(idName: String): TextView? {
            val id = row.resources.getIdentifier(idName, "id", PACKAGE)
            return if (id == 0) null else row.findViewById<View>(id) as? TextView
        }

        private fun timestamp(): TextView? {
            val id = row.resources.getIdentifier(
                "conversation_timestamp_and_unread_badge", "id", PACKAGE
            )
            if (id == 0) return null
            fun search(view: View?): TextView? {
                if (view is TextView) return view
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        search(view.getChildAt(index))?.let { return it }
                    }
                }
                return null
            }
            return search(row.findViewById<View>(id))
        }

        private fun observeFocus() {
            if (focusedObserver != null || !row.viewTreeObserver.isAlive) return
            focusedObserver = row.viewTreeObserver
            focusedObserver?.addOnGlobalFocusChangeListener(focusListener)
        }

        private fun stopFocusObserver() {
            focusedObserver?.takeIf { it.isAlive }
                ?.removeOnGlobalFocusChangeListener(focusListener)
            focusedObserver = null
        }

        private fun rollback() {
            stopFocusObserver()
            row.removeOnAttachStateChangeListener(attachmentListener)
            row.removeOnLayoutChangeListener(layoutListener)
            name?.removeTextChangedListener(textWatcher)
            snippet?.removeTextChangedListener(textWatcher)
            clock?.removeTextChangedListener(textWatcher)
            name = null
            snippet = null
            clock = null
            val skin = compose
            compose = null
            try {
                if (skin != null) {
                    row.removeView(skin)
                    skin.disposeComposition()
                }
            } finally {
                for ((view, alpha) in originalAlpha) view.alpha = alpha
                originalAlpha.clear()
                originalHeight?.let { height ->
                    row.layoutParams?.let { params ->
                        if (params.height != height) {
                            params.height = height
                            row.layoutParams = params
                        }
                    }
                }
                originalHeight = null
                owner?.destroy()
                owner = null
            }
        }

        @Composable
        private fun NativeFace() {
            Bbos6MediaListRow(
                title = face.title,
                subtitle = face.subtitle,
                duration = face.timestamp,
                trackNumber = null,
                showArtwork = false,
                artwork = null,
                artworkContentDescription = "",
                menuArtwork = false,
                highlighted = focused,
                showRemoveAction = false,
                onRemove = null,
                rowHeightDp = ROW_DP,
                textHorizontalPaddingDp = 8
            )
        }
    }
}
