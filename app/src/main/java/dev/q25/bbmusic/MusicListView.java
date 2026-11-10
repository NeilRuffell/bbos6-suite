package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.List;

/**
 * One reusable, standalone BBOS6 library ListView for every Music page.
 *
 * Owns exactly one native ListView, BaseAdapter, recycled 64dp rows, album
 * thumbnails, selection gradient, touch scrolling and hardware Up/Down
 * navigation. Page hierarchy, count calculations, album selection and
 * actions remain outside this visual component.
 *
 * Uses no BB UI, SystemUI, Vector, root or third-party media application.
 */
final class MusicListView extends ListView {
    interface DataSource {
        int size();
        RowData rowAt(int position);
        void activate(int position);
        void longPress(int position);
        boolean supportsLongPress(int position);
    }

    /** Presentation values; deliberately independent of music categories. */
    static final class RowData {
        final String title;
        final String subtitle;
        final boolean enabled;
        final boolean showArtwork;
        // Album browse rows show original 543.png when embedded art is absent.
        final boolean albumFallback;
        // Non-zero only for verified original BBOS6 home-menu PNG resources.
        final int menuIconRes;
        // Non-null only for track rows in an album detail page.
        final String trackNumber;
        // Song rows only: right-aligned duration next to the first-line title.
        final String durationLabel;
        final String artworkKey;
        final List<Uri> artworkCandidates;
        final boolean showRemoveButton;
        final boolean sectionHeader;

        RowData(String title, String subtitle, boolean enabled,
                boolean showArtwork, boolean albumFallback, int menuIconRes,
                String trackNumber, String durationLabel, String artworkKey,
                List<Uri> artworkCandidates, boolean showRemoveButton,
                boolean sectionHeader) {
            this.title = title;
            this.subtitle = subtitle;
            this.enabled = enabled;
            this.showArtwork = showArtwork;
            this.albumFallback = albumFallback;
            this.menuIconRes = menuIconRes;
            this.trackNumber = trackNumber;
            this.durationLabel = durationLabel;
            this.artworkKey = artworkKey;
            this.artworkCandidates = artworkCandidates;
            this.showRemoveButton = showRemoveButton;
            this.sectionHeader = sectionHeader;
        }
    }

    // Slightly taller shared rows add 2dp of breathing room above and below
    // centered text; square artwork still fills each row edge-to-edge.
    static final int ROW_HEIGHT_DP = 68;
    // Menu PNGs have different pixel dimensions and transparent borders.
    // Normalize the alpha-bounded artwork into this shared visible square.
    private static final int MENU_ICON_VISIBLE_DP = 52;
    private static final int MENU_ICON_ALPHA_THRESHOLD = 16;
    private static final class RowViews {
        final MusicListRowComposeView root;

        RowViews(MusicListRowComposeView root) {
            this.root = root;
        }
    }

    private final DataSource data;
    private final BaseAdapter adapter;
    private final MusicListArtworkLoader artworkLoader;
    // Original BBOS6 menu PNGs are decoded and trimmed, once each.
    private final SparseArray<Bitmap> menuIconCache = new SparseArray<>();
    private final int canvasColor;

    MusicListView(Context context, DataSource data) {
        super(context);
        this.data = data;
        artworkLoader = new MusicListArtworkLoader(context);

        // BBOS6 Music always uses a black canvas and black unfocused rows,
        // regardless of the host's system light/dark setting.
        canvasColor = Color.BLACK;
        int dividerColor = 0xff303030;

        // Focus selection colors are provided by the same shared gradient
        // used for the Now Playing Shuffle control.

        // Preserve the proven BB UI QS-style ListView configuration.
        setPadding(0, 0, 0, 0);
        setBackgroundColor(canvasColor);
        setVerticalScrollBarEnabled(false);
        setFastScrollEnabled(false);
        setItemsCanFocus(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSelector(android.R.color.transparent);
        setDivider(new ColorDrawable(dividerColor));
        setDividerHeight(dp(1));

        adapter = new BaseAdapter() {
            @Override public int getCount() {
                return MusicListView.this.data.size();
            }

            @Override public Object getItem(int position) {
                return MusicListView.this.data.rowAt(position);
            }

            @Override public boolean isEnabled(int position) {
                return MusicListView.this.data.rowAt(position).enabled;
            }

            @Override public long getItemId(int position) {
                return position;
            }

            @Override public int getViewTypeCount() {
                return 2;
            }

            @Override public int getItemViewType(int position) {
                return MusicListView.this.data.rowAt(position).sectionHeader ? 1 : 0;
            }

            @Override public View getView(int position, View convertView,
                    ViewGroup parent) {
                RowData dataRow = MusicListView.this.data.rowAt(position);
                if (dataRow.sectionHeader) {
                    MusicSettingsHeaderView header = convertView == null
                            ? new MusicSettingsHeaderView(getContext())
                            : (MusicSettingsHeaderView) convertView;
                    header.bind(dataRow.title);
                    return header;
                }
                View view = convertView == null ? createRowView() : convertView;
                RowViews views = (RowViews) view.getTag();
                bindRow(views, dataRow, position);
                return view;
            }
        };
        setAdapter(adapter);

        setOnItemClickListener((parent, row, position, id) -> data.activate(position));
        // Platform long-click detection shares the system touch timeout with
        // the Q25 confirm-key handler. Consuming it prevents a tap on release.
        setOnItemLongClickListener((parent, row, position, id) -> {
            if (position < 0 || position >= data.size()
                    || !data.rowAt(position).enabled
                    || !data.supportsLongPress(position)) return false;
            data.longPress(position);
            return true;
        });
        setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,
                    View row, int position, long id) {
                styleVisibleRows();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
                styleVisibleRows();
            }
        });
        setOnFocusChangeListener((view, hasFocus) -> styleVisibleRows());
        setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { }

            @Override public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
                styleVisibleRows();
            }
        });
    }

    int canvasColor() {
        return canvasColor;
    }

    void notifyRowsChanged() {
        adapter.notifyDataSetChanged();
    }

    /** Rebind counts and covers in place without resetting selection or scroll. */
    void refreshVisibleRows() {
        int first = getFirstVisiblePosition();
        for (int child = 0; child < getChildCount(); child++) {
            View row = getChildAt(child);
            int position = first + child;
            if (!(row.getTag() instanceof RowViews)
                    || position < 0 || position >= data.size()) continue;
            bindRow((RowViews) row.getTag(), data.rowAt(position), position);
        }
    }

    void refreshSelection() {
        styleVisibleRows();
    }

    /** Share the row thumbnail cache with album-detail page headers. */
    void loadAlbumArtwork(String key, List<Uri> albumTracks,
            MusicListArtworkLoader.Listener listener) {
        artworkLoader.request(key, albumTracks, listener);
    }

    void clearArtworkCache() {
        artworkLoader.clear();
    }

    void dispose() {
        artworkLoader.close();
    }

    /**
     * Exactly the prior MusicActivity QS-style trackpad routing.
     * ListView handles the actual one-row movement; this only restores
     * a visible selection after touch scrolling clears it.
     */
    boolean handleVerticalKey(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int selected = getSelectedItemPosition();
            if (selected < 0) {
                int first = Math.max(0, getFirstVisiblePosition());
                int last = Math.min(data.size() - 1, getLastVisiblePosition());
                int target = code == KeyEvent.KEYCODE_DPAD_UP
                        ? Math.max(first, last) : first;
                int visibleIndex = target - getFirstVisiblePosition();
                View visible = visibleIndex >= 0 && visibleIndex < getChildCount()
                        ? getChildAt(visibleIndex) : null;
                int top = visible == null ? 0 : visible.getTop();
                requestFocus();
                setSelectionFromTop(target, top);
            } else {
                if (!hasFocus()) requestFocus();
                dispatchKeyEvent(event);
            }
        }
        return true;
    }

    /**
     * Kotlin Compose owns only row drawing; this native ListView remains the
     * adapter, scroll/selection, DPAD, touch, and long-press controller.
     */
    private View createRowView() {
        MusicListRowComposeView row = new MusicListRowComposeView(getContext());
        row.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP)));
        row.setFocusable(false);
        row.setTag(new RowViews(row));
        return row;
    }

    private void bindRow(RowViews views, RowData row, int position) {
        views.root.bindText(row.title, row.subtitle,
                row.durationLabel, row.trackNumber,
                row.showRemoveButton,
                row.showRemoveButton ? () -> data.activate(position) : null);

        String spoken = row.trackNumber == null ? row.title
                : ("--".equals(row.trackNumber) ? "Track number unavailable, "
                : "Track " + row.trackNumber + ", ") + row.title;
        views.root.setContentDescription((row.subtitle == null ? spoken
                : spoken + ", " + row.subtitle)
                + (row.durationLabel == null ? "" : ", " + row.durationLabel)
                + (row.showRemoveButton ? ", Remove available" : ""));

        bindArtwork(views, row);
        styleRow(views, position);
    }

    private void bindArtwork(RowViews views, RowData row) {
        MusicListRowComposeView target = views.root;
        if (row.menuIconRes != 0) {
            // The original alpha-based trim and resource cache remain in this
            // Java ListView; Compose only paints the resulting Bitmap.
            target.setArtworkBindingKey(null);
            target.setMenuIconBitmap(menuIconBitmap(row.menuIconRes),
                    row.title + " icon");
            return;
        }
        if (!row.showArtwork) {
            target.setArtworkBindingKey(null);
            target.hideArtwork();
            return;
        }
        if (row.artworkKey == null
                || row.artworkCandidates == null || row.artworkCandidates.isEmpty()) {
            target.setArtworkBindingKey(null);
            showAlbumFallback(views, row.albumFallback);
            return;
        }
        String requestKey = row.artworkKey;
        String bindingKey = requestKey + (row.albumFallback ? "|album" : "|default");
        if (!bindingKey.equals(target.getArtworkBindingKey())) {
            target.setArtworkBindingKey(bindingKey);
            showAlbumFallback(views, row.albumFallback);
        }
        artworkLoader.request(requestKey, row.artworkCandidates, image -> {
            // Prevent a pending callback from painting a recycled list row.
            if (!bindingKey.equals(target.getArtworkBindingKey())) return;
            if (image == null) showAlbumFallback(views, row.albumFallback);
            else target.setArtworkBitmap(image);
        });
    }

    private void showAlbumFallback(RowViews views, boolean originalAlbumIcon) {
        if (originalAlbumIcon) views.root.showAlbumFallback();
        else views.root.showGenericFallback();
    }

    /**
     * Decode each original (drawable-nodpi) icon once and trim only pixels
     * that are essentially transparent. The bitmap is never stretched,
     * recolored, written back to the repository, or cropped at display time.
     *
     * This handles both 65px and 75px original files with the same
     * visible-art scaling while preserving their individual aspect ratios.
     */
    private Bitmap menuIconBitmap(int resourceId) {
        Bitmap cached = menuIconCache.get(resourceId);
        if (cached != null) return cached;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false; // Preserve the exact source pixel geometry.
        Bitmap original = BitmapFactory.decodeResource(
                getResources(), resourceId, options);
        if (original == null) return null;

        int left = original.getWidth();
        int top = original.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                if (Color.alpha(original.getPixel(x, y))
                        <= MENU_ICON_ALPHA_THRESHOLD) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }

        Bitmap cropped = right >= left && bottom >= top
                ? Bitmap.createBitmap(original, left, top,
                        right - left + 1, bottom - top + 1)
                : original;
        if (cropped != original) original.recycle();
        menuIconCache.put(resourceId, cropped);
        return cropped;
    }

    private void styleVisibleRows() {
        int first = getFirstVisiblePosition();
        for (int child = 0; child < getChildCount(); child++) {
            View row = getChildAt(child);
            if (!(row.getTag() instanceof RowViews)) continue;
            int position = first + child;
            if (position < 0 || position >= data.size()) continue;
            styleRow((RowViews) row.getTag(), position);
        }
    }

    private void styleRow(RowViews views, int position) {
        views.root.setHighlighted(hasFocus() && getSelectedItemPosition() == position);
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
