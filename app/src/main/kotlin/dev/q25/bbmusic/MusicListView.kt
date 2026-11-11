package dev.q25.bbmusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import dev.q25.bbos6.ui.Bbos6FocusPolicy
import kotlin.math.roundToInt

/**
 * Kotlin owner for the existing BBOS6 ListView navigation contract.
 *
 * The row faces and headers are already shared Kotlin Compose views.
 * Native Android ListView retains tested Q25 trackpad scrolling, page-edge
 * alignment, item click/long-click timeouts, adapter recycling and focus.
 * Data/page navigation stays in MusicActivity; artwork loader remains
 * asynchronous and ignores stale recycled-row callbacks.
 */
class MusicListView(context: Context, private val data: DataSource) : ListView(context) {
    interface DataSource {
        fun size(): Int
        fun rowAt(position: Int): RowData
        fun activate(position: Int)
        fun longPress(position: Int)
        fun supportsLongPress(position: Int): Boolean
    }

    /** Java callers still supply these app-specific presentation values. */
    class RowData(
        @JvmField val title: String?,
        @JvmField val subtitle: String?,
        @JvmField val enabled: Boolean,
        @JvmField val showArtwork: Boolean,
        @JvmField val albumFallback: Boolean,
        @JvmField val menuIconRes: Int,
        @JvmField val trackNumber: String?,
        @JvmField val durationLabel: String?,
        @JvmField val artworkKey: String?,
        @JvmField val artworkCandidates: List<Uri>?,
        @JvmField val showRemoveButton: Boolean,
        @JvmField val sectionHeader: Boolean
    )

    companion object {
        const val ROW_HEIGHT_DP = 68
        private const val MENU_ICON_ALPHA_THRESHOLD = 16
        private val DIVIDER_COLOR = 0xff303030.toInt()
    }

    private class RowViews(val root: MusicListRowComposeView)

    private val artworkLoader = MusicListArtworkLoader(context)
    private val menuIconCache = SparseArray<Bitmap>()
    private val rowAdapter: BaseAdapter

    init {
        // Exact original ListView interaction and geometry: don't replace
        // it with a naïve LazyColumn and break the physical Q25 trackpad.
        setPadding(0, 0, 0, 0)
        setBackgroundColor(Color.BLACK)
        isVerticalScrollBarEnabled = false
        isFastScrollEnabled = false
        itemsCanFocus = false
        isFocusable = true
        isFocusableInTouchMode = true
        setSelector(android.R.color.transparent)
        divider = ColorDrawable(DIVIDER_COLOR)
        dividerHeight = dp(1f)

        rowAdapter = object : BaseAdapter() {
            override fun getCount(): Int = data.size()
            override fun getItem(position: Int): Any = data.rowAt(position)
            override fun isEnabled(position: Int): Boolean = data.rowAt(position).enabled
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getViewTypeCount(): Int = 2
            override fun getItemViewType(position: Int): Int =
                if (data.rowAt(position).sectionHeader) 1 else 0

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val value = data.rowAt(position)
                if (value.sectionHeader) {
                    val header = convertView as? MusicSettingsHeaderView
                        ?: MusicSettingsHeaderView(context)
                    header.bind(value.title.orEmpty())
                    return header
                }
                val view = convertView ?: createRowView()
                val views = view.tag as RowViews
                bindRow(views, value, position)
                return view
            }
        }
        adapter = rowAdapter
        onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            data.activate(position)
        }
        // Keep Android's long-click detection and tap-on-release prevention.
        onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            if (position < 0 || position >= data.size() ||
                !data.rowAt(position).enabled ||
                !data.supportsLongPress(position)
            ) false
            else {
                data.longPress(position)
                true
            }
        }
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) = styleVisibleRows()
            override fun onNothingSelected(parent: AdapterView<*>?) = styleVisibleRows()
        }
        onFocusChangeListener = OnFocusChangeListener { _, _ -> styleVisibleRows() }
        setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit
            override fun onScroll(
                view: AbsListView?, firstVisibleItem: Int,
                visibleItemCount: Int, totalItemCount: Int
            ) = styleVisibleRows()
        })
        // Suppress platform's lighter focus overlay at its source while
        // retaining BBOS6 row-gradient selection and native DPAD events.
        Bbos6FocusPolicy.forManagedContainers(this)
    }

    fun canvasColor(): Int = Color.BLACK
    fun notifyRowsChanged() = rowAdapter.notifyDataSetChanged()

    /** Rebind visible rows in place without scroll/selection resets. */
    fun refreshVisibleRows() {
        val first = firstVisiblePosition
        for (child in 0 until childCount) {
            val view = getChildAt(child)
            val position = first + child
            val views = view.tag as? RowViews ?: continue
            if (position !in 0 until data.size()) continue
            bindRow(views, data.rowAt(position), position)
        }
    }

    fun refreshSelection() = styleVisibleRows()

    /** Shared artwork cache is also used by album-page Compose headers. */
    fun loadAlbumArtwork(
        key: String, albumTracks: List<Uri>, listener: MusicListArtworkLoader.Listener
    ) = artworkLoader.request(key, albumTracks, listener)

    fun clearArtworkCache() = artworkLoader.clear()
    fun dispose() = artworkLoader.close()

    /**
     * Preserve the Q25 scroll selection recovery after a touch scroll.
     * The ListView handles movement; this method only restores selection.
     */
    fun handleVerticalKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val selected = selectedItemPosition
            if (selected < 0) {
                val first = maxOf(0, firstVisiblePosition)
                val last = minOf(data.size() - 1, lastVisiblePosition)
                val target =
                    if (code == KeyEvent.KEYCODE_DPAD_UP) maxOf(first, last) else first
                val visibleIndex = target - firstVisiblePosition
                val visible = if (visibleIndex >= 0 && visibleIndex < childCount)
                    getChildAt(visibleIndex) else null
                val top = visible?.top ?: 0
                requestFocus()
                setSelectionFromTop(target, top)
            } else {
                if (!hasFocus()) requestFocus()
                dispatchKeyEvent(event)
            }
        }
        return true
    }

    private fun createRowView(): View {
        val row = MusicListRowComposeView(context)
        row.layoutParams = AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP.toFloat())
        )
        row.isFocusable = false
        row.tag = RowViews(row)
        return row
    }

    private fun bindRow(views: RowViews, row: RowData, position: Int) {
        views.root.bindText(
            row.title, row.subtitle, row.durationLabel, row.trackNumber,
            row.showRemoveButton,
            if (row.showRemoveButton) Runnable { data.activate(position) } else null
        )
        val spoken = if (row.trackNumber == null) row.title.orEmpty()
            else (if (row.trackNumber == "--") "Track number unavailable, "
                  else "Track ${row.trackNumber}, ") + row.title.orEmpty()
        views.root.contentDescription = (
            spoken + (row.subtitle?.let { ", $it" } ?: "") +
                (row.durationLabel?.let { ", $it" } ?: "") +
                (if (row.showRemoveButton) ", Remove available" else "")
        )
        bindArtwork(views, row)
        styleRow(views, position)
    }

    private fun bindArtwork(views: RowViews, row: RowData) {
        val target = views.root
        if (row.menuIconRes != 0) {
            target.setArtworkBindingKey(null)
            target.setMenuIconBitmap(menuIconBitmap(row.menuIconRes), row.title + " icon")
            return
        }
        if (!row.showArtwork) {
            target.setArtworkBindingKey(null)
            target.hideArtwork()
            return
        }
        val key = row.artworkKey
        val candidates = row.artworkCandidates
        if (key == null || candidates.isNullOrEmpty()) {
            target.setArtworkBindingKey(null)
            showAlbumFallback(views, row.albumFallback)
            return
        }
        val bindingKey = key + (if (row.albumFallback) "|album" else "|default")
        if (bindingKey != target.getArtworkBindingKey()) {
            target.setArtworkBindingKey(bindingKey)
            showAlbumFallback(views, row.albumFallback)
        }
        artworkLoader.request(key, candidates, MusicListArtworkLoader.Listener { bitmap ->
            if (bindingKey != target.getArtworkBindingKey()) return@Listener
            if (bitmap == null) showAlbumFallback(views, row.albumFallback)
            else target.setArtworkBitmap(bitmap)
        })
    }

    private fun showAlbumFallback(views: RowViews, originalAlbumIcon: Boolean) {
        if (originalAlbumIcon) views.root.showAlbumFallback()
        else views.root.showGenericFallback()
    }

    /**
     * The verified original-number PNG is decoded once and alpha-trimmed,
     * never recolored, stretched or written to disk.
     */
    private fun menuIconBitmap(resourceId: Int): Bitmap? {
        menuIconCache.get(resourceId)?.let { return it }
        val options = BitmapFactory.Options().apply { inScaled = false }
        val original = BitmapFactory.decodeResource(resources, resourceId, options)
            ?: return null
        var left = original.width
        var top = original.height
        var right = -1
        var bottom = -1
        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                if (Color.alpha(original.getPixel(x, y)) <= MENU_ICON_ALPHA_THRESHOLD) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        val cropped = if (right >= left && bottom >= top)
            Bitmap.createBitmap(original, left, top, right - left + 1, bottom - top + 1)
        else original
        if (cropped !== original) original.recycle()
        menuIconCache.put(resourceId, cropped)
        return cropped
    }

    private fun styleVisibleRows() {
        val first = firstVisiblePosition
        for (child in 0 until childCount) {
            val view = getChildAt(child)
            val views = view.tag as? RowViews ?: continue
            val position = first + child
            if (position !in 0 until data.size()) continue
            styleRow(views, position)
        }
    }

    private fun styleRow(views: RowViews, position: Int) {
        views.root.setHighlighted(hasFocus() && selectedItemPosition == position)
    }

    private fun dp(value: Float): Int =
        (resources.displayMetrics.density * value).roundToInt()
}
