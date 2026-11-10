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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Performs a confirmed, read/write-authorized deletion of *audio documents*
 * through Android's native Storage Access Framework on a worker thread.
 * Never deletes directory documents or uses filesystem / MediaStore paths.
 *
 * The Room scan marker is invalidated before the first deletion, so a process
 * killed part-way through a batch must rescan instead of trusting stale rows.
 */
final class MusicTrackDeletion {
    private static final String TAG = "BBMusic-Delete";

    interface Listener {
        void onFinished(Result result);
    }

    static final class Result {
        final Set<String> deletedIds;
        final int failed;

        Result(Set<String> deletedIds, int failed) {
            this.deletedIds = Collections.unmodifiableSet(new HashSet<>(deletedIds));
            this.failed = failed;
        }

        int deletedCount() { return deletedIds.size(); }
    }

    private final Context context;
    private final ContentResolver resolver;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    MusicTrackDeletion(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
    }

    /** The caller has already shown a destructive-action confirmation. */
    void delete(List<LocalMusicSource.Song> requested, Listener listener) {
        if (closed || requested.isEmpty()) return;
        // Deduplicate identical documents even if a group presents aliases.
        LinkedHashMap<String, LocalMusicSource.Song> distinct = new LinkedHashMap<>();
        for (LocalMusicSource.Song song : requested) {
            if (song != null && song.uri != null) {
                distinct.put(song.uri.toString(), song);
            }
        }
        if (distinct.isEmpty()) return;
        executor.execute(() -> {
            Set<String> deleted = new HashSet<>();
            int failed = 0;
            MusicLibraryDatabase database = null;
            try {
                database = MusicLibraryDatabase.open(context);
                MusicLibraryDatabase.LibraryDao dao = database.libraryDao();
                // This occurs before changing files: a power loss or Activity
                // teardown cannot leave a valid marker for deleted content.
                database.runInTransaction(dao::deleteScanState);
                for (LocalMusicSource.Song song : distinct.values()) {
                    if (!authorized(song) || !supportsDelete(song.uri)) {
                        failed++;
                        continue;
                    }
                    try {
                        if (DocumentsContract.deleteDocument(resolver, song.uri)) {
                            deleted.add(song.uri.toString());
                        } else {
                            failed++;
                        }
                    } catch (Exception exception) {
                        // One read-only or unavailable file must not prevent
                        // deletion of other valid files in the confirmed group.
                        failed++;
                        Log.w(TAG, "Provider rejected track deletion: "
                                + exception.getClass().getSimpleName());
                    }
                }
                if (!deleted.isEmpty()) {
                    ArrayList<String> uris = new ArrayList<>(deleted);
                    for (int i = 0; i < uris.size(); i += 400) {
                        List<String> batch = uris.subList(i,
                                Math.min(i + 400, uris.size()));
                        database.runInTransaction(() -> dao.deleteTracks(batch));
                    }
                }
            } catch (RuntimeException exception) {
                Log.e(TAG, "Track deletion database operation failed", exception);
                failed += distinct.size() - deleted.size() - failed;
            } finally {
                if (database != null) database.close();
            }

            Result result = new Result(deleted, failed);
            main.post(() -> {
                if (!closed) listener.onFinished(result);
            });
        });
    }

    private boolean authorized(LocalMusicSource.Song song) {
        if (song.sourceTree == null
                || !MusicFolders.selectedTrees(context).contains(song.sourceTree)
                || !MusicFolders.hasWriteGrant(context, song.sourceTree)) return false;
        try {
            if (!"content".equals(song.uri.getScheme())
                    || !DocumentsContract.isTreeUri(song.uri)
                    || !song.sourceTree.getAuthority().equals(song.uri.getAuthority())) {
                return false;
            }
            String selectedRoot = DocumentsContract.getTreeDocumentId(song.sourceTree);
            if (!selectedRoot.equals(DocumentsContract.getTreeDocumentId(song.uri))) {
                return false;
            }
            String id = DocumentsContract.getDocumentId(song.uri);
            return id != null && song.uri.equals(
                    DocumentsContract.buildDocumentUriUsingTree(song.sourceTree, id));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Only documents whose provider explicitly advertises DELETE support. */
    private boolean supportsDelete(Uri uri) {
        String[] projection = {
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            int flagsColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_FLAGS);
            int typeColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);
            if (flagsColumn < 0 || cursor.isNull(flagsColumn)) return false;
            if (typeColumn >= 0 && !cursor.isNull(typeColumn)
                    && DocumentsContract.Document.MIME_TYPE_DIR.equals(
                            cursor.getString(typeColumn))) return false;
            return (cursor.getInt(flagsColumn)
                    & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Stop delivering callbacks to a destroyed Activity, but let an already
     * confirmed batch finish and persist its Room changes in the worker.
     */
    void close() {
        closed = true;
        executor.shutdown();
    }
}
