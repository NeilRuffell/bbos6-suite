package dev.q25.bbmusic;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only, one-directory-at-a-time SAF browser for explicitly saved music
 * folders. Listing children never scans unrelated storage or mutates files.
 * All provider I/O is off the UI thread; the existing MusicListView owns UI.
 */
final class MusicFolderBrowser {
    private static final String TAG = "BBMusic-Browser";

    interface Listener {
        void onLoaded(List<Entry> entries);
        void onError(String message);
    }

    static final class Entry {
        final String name;
        final String documentId;
        final Uri uri;
        final boolean directory;

        Entry(String name, String documentId, Uri uri, boolean directory) {
            this.name = name;
            this.documentId = documentId;
            this.uri = uri;
            this.directory = directory;
        }
    }

    private final Context context;
    private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    MusicFolderBrowser(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
    }

    void listChildren(Uri tree, String directoryId, Listener listener) {
        if (closed) return;
        worker.execute(() -> {
            try {
                // Never trust a retained Page as authorization: Settings may
                // have removed the tree or Android may have revoked its grant.
                if (!MusicFolders.selectedTrees(context).contains(tree)) {
                    throw new SecurityException("Selected folder access unavailable");
                }
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree, directoryId);
                String[] projection = {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                };
                ArrayList<Entry> entries = new ArrayList<>();
                try (Cursor cursor = resolver.query(children, projection,
                        null, null, null)) {
                    if (cursor == null) {
                        throw new IllegalStateException("Folder has no readable child list");
                    }
                    int idIndex = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                    int nameIndex = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                    int mimeIndex = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_MIME_TYPE);
                    while (!closed && cursor.moveToNext()) {
                        String id = cursor.getString(idIndex);
                        if (id == null || id.isEmpty()) continue;
                        String name = cursor.getString(nameIndex);
                        if (name == null || name.trim().isEmpty()) {
                            // Recover the final component to correctly detect
                            // hidden directories even if the provider omits its name.
                            int separator = Math.max(id.lastIndexOf('/'),
                                    id.lastIndexOf(':'));
                            name = separator >= 0 ? id.substring(separator + 1) : id;
                        }
                        String mime = cursor.getString(mimeIndex);
                        boolean directory =
                                DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                        if (directory) {
                            if (!MusicFilePolicy.isVisibleDirectory(name)) continue;
                        } else if (!MusicFilePolicy.isAudio(name, mime)) {
                            continue;
                        }
                        entries.add(new Entry(name, id,
                                DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                directory));
                    }
                }
                entries.sort((a, b) -> {
                    if (a.directory != b.directory) return a.directory ? -1 : 1;
                    int order = String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
                    if (order != 0) return order;
                    return a.documentId.compareTo(b.documentId);
                });
                List<Entry> result = Collections.unmodifiableList(entries);
                main.post(() -> {
                    if (closed) return;
                    if (!MusicFolders.selectedTrees(context).contains(tree)) {
                        listener.onError("Folder access unavailable");
                    } else {
                        listener.onLoaded(result);
                    }
                });
            } catch (SecurityException exception) {
                Log.w(TAG, "Selected music folder permission unavailable", exception);
                main.post(() -> {
                    if (!closed) listener.onError("Folder access unavailable");
                });
            } catch (RuntimeException exception) {
                Log.w(TAG, "Cannot browse selected music folder", exception);
                main.post(() -> {
                    if (!closed) listener.onError("Cannot open music folder");
                });
            }
        });
    }

    void close() {
        closed = true;
        worker.shutdownNow();
    }
}
