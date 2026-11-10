package dev.q25.bbmusic;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.Log;

import dev.q25.bbos6.ui.Bbos6PropertiesItem;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Music-only adapter between verified SAF songs and the generic Properties UI.
 * Reads only the selected document; all provider I/O is off the UI thread.
 *
 * There is no portable SAF creation date or absolute filesystem path. Neither
 * is fabricated. The provider may also omit size, type or modification time.
 */
final class MusicTrackPropertiesReader {
    private static final String TAG = "BBMusic-Properties";

    interface Listener {
        void onLoaded(List<Bbos6PropertiesItem> items);
    }

    private final Context context;
    private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    MusicTrackPropertiesReader(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    void load(LocalMusicSource.Song song, Listener listener) {
        if (closed) return;
        worker.execute(() -> {
            if (closed) return;
            // Revoked/removed folders never grant a Properties lookup.
            if (!MusicFolders.selectedTrees(context).contains(song.sourceTree)) {
                main.post(() -> {
                    if (!closed) listener.onLoaded(new ArrayList<>());
                });
                return;
            }

            String fileName = null;
            String mime = null;
            Long fileSize = null;
            Long modifiedAt = null;
            try {
                String[] projection = {
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                };
                try (Cursor cursor = resolver.query(song.uri, projection,
                        null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        fileName = readString(cursor,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                        mime = readString(cursor,
                                DocumentsContract.Document.COLUMN_MIME_TYPE);
                        fileSize = readPositiveOrZero(cursor,
                                DocumentsContract.Document.COLUMN_SIZE);
                        modifiedAt = readPositiveOrZero(cursor,
                                DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                    }
                }
            } catch (RuntimeException exception) {
                // The song's indexed tags remain usable if a document provider
                // does not support a metadata projection or has gone offline.
                Log.w(TAG, "Optional document metadata unavailable ("
                        + exception.getClass().getSimpleName() + ")");
            }

            ArrayList<Bbos6PropertiesItem> items = new ArrayList<>();
            if (fileName != null && !fileName.trim().isEmpty()) {
                try {
                    String docId = DocumentsContract.getDocumentId(song.uri);
                    String readablePath = MusicDocumentPath.readableLocation(
                            song.uri.getAuthority(), docId, fileName);
                    if (readablePath != null) {
                        items.add(Bbos6PropertiesItem.text("Path: " + readablePath));
                    }
                } catch (RuntimeException ignored) {
                    // Do not treat an opaque/invalid SAF ID as a filesystem path.
                }
            }
            if (fileSize != null) {
                items.add(Bbos6PropertiesItem.trailing("Size:",
                        Formatter.formatShortFileSize(context, fileSize)));
            }

            // All indexed Song objects passed here were verified by
            // MediaExtractor as containing an actual audio track.
            if (mime != null && mime.startsWith("audio/")) {
                items.add(Bbos6PropertiesItem.inline("Type:", mime));
            } else {
                items.add(Bbos6PropertiesItem.inline("Type:", "Audio File"));
            }

            // Android DocumentsContract has a last-modified timestamp but no
            // standard creation timestamp. Do not repurpose recording/tag dates.
            if (modifiedAt != null && modifiedAt > 0L) {
                Date date = new Date(modifiedAt);
                String time = DateFormat.getMediumDateFormat(context).format(date)
                        + " " + DateFormat.getTimeFormat(context).format(date);
                items.add(Bbos6PropertiesItem.trailing("Last Modified:", time));
            }

            if (known(song.title)) {
                items.add(Bbos6PropertiesItem.inline("Title:", song.title));
            }
            if (MusicAlbumTag.present(song.album)) {
                items.add(Bbos6PropertiesItem.inline("Album:", song.album));
            }
            // LocalMusicSource substitutes Unknown Artist when the file has no
            // artist tag. Do not present that synthetic fallback as file data.
            if (known(song.artist) && !"Unknown Artist".equals(song.artist)) {
                items.add(Bbos6PropertiesItem.inline("Artist:", song.artist));
            }

            main.post(() -> {
                if (closed) return;
                // Permission may have been revoked while the provider was busy.
                if (!MusicFolders.selectedTrees(context).contains(song.sourceTree)) {
                    listener.onLoaded(new ArrayList<>());
                } else {
                    listener.onLoaded(items);
                }
            });
        });
    }

    private static boolean known(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String readString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static Long readPositiveOrZero(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return null;
        long value = cursor.getLong(index);
        return value < 0 ? null : value;
    }

    void close() {
        closed = true;
        worker.shutdownNow();
    }
}
