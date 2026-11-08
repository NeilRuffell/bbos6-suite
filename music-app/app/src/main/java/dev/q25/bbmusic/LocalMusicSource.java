package dev.q25.bbmusic;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Folder-scoped local music index using the Storage Access Framework.
 *
 * Important privacy invariant: never query MediaStore's device-wide tables,
 * enumerate storage roots, or walk directories outside the persisted trees
 * explicitly selected in Settings > Music Folders > Add folders.
 *
 * Queries and audio metadata reads run on one worker thread. The UI owns
 * how the resulting list is presented; no media playback dependency exists.
 */
final class LocalMusicSource {
    private static final String TAG = "BBMusic-Local";
    // Rebuild previously indexed libraries once after introducing actual
    // audio-track validation. No database schema/data reset is required.
    private static final String INDEX_RULES_VERSION = "verified-audio-v2:";
    private final Context context;
    private final ContentResolver resolver;
    private final MusicLibraryDatabase libraryDatabase;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile int latestRequest;

    interface Listener {
        void onLoaded(List<Song> songs, ScanSummary summary);
        void onError(String reason);
    }

    static final class ScanSummary {
        final boolean scanned;
        final boolean full;
        final int added;
        final int rechecked;
        final int removed;
        final int reused;
        final int total;

        ScanSummary(boolean scanned, boolean full, int added, int rechecked,
                int removed, int reused, int total) {
            this.scanned = scanned;
            this.full = full;
            this.added = added;
            this.rechecked = rechecked;
            this.removed = removed;
            this.reused = reused;
            this.total = total;
        }

        boolean artworkMayHaveChanged() {
            return full || added > 0 || rechecked > 0 || removed > 0;
        }

        String message() {
            if (full) return "Full rescan complete: " + total + " tracks";
            if (added == 0 && rechecked == 0 && removed == 0) {
                return "Library up to date: " + total + " tracks";
            }
            return "Library refreshed: " + added + " added, "
                    + rechecked + " rechecked, " + removed + " removed";
        }
    }

    private static final class ScanResult {
        final ArrayList<Song> songs = new ArrayList<>();
        final ArrayList<MusicLibraryDatabase.TrackRow> changes = new ArrayList<>();
        final ArrayList<String> removed = new ArrayList<>();
        final Set<String> retainedUris = new HashSet<>();
        final Set<String> visitedDocuments = new HashSet<>();
        int added;
        int rechecked;
        int reused;

        ScanSummary summary(boolean full) {
            return new ScanSummary(true, full, added, rechecked, removed.size(),
                    reused, songs.size());
        }
    }

    static final class Song {
        final long id;
        final Uri uri;
        final Uri sourceTree;
        final String title;
        final String artist;
        final String album;
        final String genre;
        final long artistId;
        final long albumId;
        final long durationMs;
        final int trackNumber;

        Song(long id, Uri uri, Uri sourceTree, String title, String artist, String album,
                String genre, long artistId, long albumId, long durationMs,
                int trackNumber) {
            this.id = id;
            this.uri = uri;
            this.sourceTree = sourceTree;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.genre = genre;
            this.artistId = artistId;
            this.albumId = albumId;
            this.durationMs = durationMs;
            this.trackNumber = trackNumber;
        }
    }

    LocalMusicSource(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
        libraryDatabase = MusicLibraryDatabase.open(this.context);
    }

    /** Load the saved catalog without directory enumeration when possible. */
    void load(Listener listener) {
        request(false, false, listener);
    }

    /** Enumerate folders while reusing unchanged, verified song metadata. */
    void refresh(Listener listener) {
        request(true, false, listener);
    }

    /** Force metadata rechecking when document providers report stale metadata. */
    void fullRescan(Listener listener) {
        request(true, true, listener);
    }

    private void request(boolean scanRequested, boolean fullRescan,
            Listener listener) {
        if (closed) return;
        final int request = ++latestRequest;
        worker.execute(() -> {
            try {
                List<Uri> trees = MusicFolders.selectedTrees(context);
                String key = selectionKey(trees);
                MusicLibraryDatabase.LibraryDao dao = libraryDatabase.libraryDao();
                MusicLibraryDatabase.ScanRow state = dao.scanState();
                List<MusicLibraryDatabase.TrackRow> oldRows = dao.tracks();
                List<Song> songs;
                ScanSummary summary;

                if (!scanRequested && state != null
                        && key.equals(state.selectedTreesKey)) {
                    // Never load metadata for trees whose SAF grants expired.
                    Set<String> authorized = new HashSet<>();
                    for (Uri tree : trees) authorized.add(tree.toString());
                    ArrayList<Song> cached = new ArrayList<>();
                    for (MusicLibraryDatabase.TrackRow row : oldRows) {
                        if (!authorized.contains(row.sourceTree)
                                || !row.uri.startsWith("content:")) continue;
                        cached.add(songFromRow(row));
                    }
                    cached.sort(Comparator.comparing((Song song) -> song.title,
                            String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(song -> song.uri.toString()));
                    songs = Collections.unmodifiableList(cached);
                    summary = new ScanSummary(false, false, 0, 0, 0,
                            cached.size(), cached.size());
                    Log.i(TAG, "Loaded persistent music index: " + songs.size());
                } else {
                    Map<String, MusicLibraryDatabase.TrackRow> previous =
                            new HashMap<>();
                    for (MusicLibraryDatabase.TrackRow row : oldRows) {
                        previous.put(row.uri, row);
                    }
                    // SAF exposes no universal directory-change journal; walk
                    // every selected directory, then check candidate signatures.
                    ScanResult result = scanSelectedFolders(
                            request, trees, previous, fullRescan);
                    if (closed || request != latestRequest) return;
                    MusicLibraryDatabase.ScanRow next =
                            new MusicLibraryDatabase.ScanRow();
                    next.selectedTreesKey = key;
                    // Atomic delta commit: failed scans retain the old index.
                    libraryDatabase.runInTransaction(() -> {
                        if (closed || request != latestRequest) return;
                        for (int i = 0; i < result.removed.size(); i += 400) {
                            dao.deleteTracks(result.removed.subList(i,
                                    Math.min(i + 400, result.removed.size())));
                        }
                        if (!result.changes.isEmpty()) dao.insertTracks(result.changes);
                        dao.setScanState(next);
                    });
                    songs = Collections.unmodifiableList(result.songs);
                    summary = result.summary(fullRescan);
                    Log.i(TAG, "Library scan: added=" + summary.added
                            + " rechecked=" + summary.rechecked
                            + " removed=" + summary.removed
                            + " reused=" + summary.reused
                            + " total=" + summary.total
                            + " full=" + fullRescan);
                }
                main.post(() -> {
                    if (!closed && request == latestRequest) {
                        listener.onLoaded(songs, summary);
                    }
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Music library load failed", exception);
                main.post(() -> {
                    if (!closed && request == latestRequest) {
                        listener.onError("Cannot read selected music folders");
                    }
                });
            }
        });
    }

    private static Song songFromRow(MusicLibraryDatabase.TrackRow row) {
        return new Song(row.id, Uri.parse(row.uri), Uri.parse(row.sourceTree),
                row.title, row.artist, row.album, row.genre, row.artistId,
                row.albumId, row.durationMs, row.trackNumber);
    }

    private static MusicLibraryDatabase.TrackRow trackRow(Song song,
            String filename, long fileSize, long lastModified) {
        MusicLibraryDatabase.TrackRow row = new MusicLibraryDatabase.TrackRow();
        row.id = song.id;
        row.uri = song.uri.toString();
        row.sourceTree = song.sourceTree.toString();
        row.title = song.title;
        row.artist = song.artist;
        row.album = song.album;
        row.genre = song.genre;
        row.artistId = song.artistId;
        row.albumId = song.albumId;
        row.durationMs = song.durationMs;
        row.trackNumber = song.trackNumber;
        row.fileName = filename == null ? "" : filename;
        row.fileSize = fileSize;
        row.lastModified = lastModified;
        return row;
    }

    /** Length-prefixed sorted URI list; an empty scanned library is valid. */
    private static String selectionKey(List<Uri> trees) {
        ArrayList<String> sorted = new ArrayList<>(trees.size());
        for (Uri tree : trees) sorted.add(tree.toString());
        Collections.sort(sorted);
        StringBuilder key = new StringBuilder();
        for (String tree : sorted) {
            key.append(tree.length()).append(':').append(tree);
        }
        return INDEX_RULES_VERSION + key;
    }

    void invalidate() {
        // Discard in-flight scans after a selection changes.
        latestRequest++;
    }

    /**
     * Erase metadata for a removed folder even if the next scan fails or the
     * user immediately closes the Activity. Queue it ahead of the rescan.
     */
    void forgetRemovedTree(Uri tree, boolean noAuthorizedTreesRemain) {
        if (closed) return;
        worker.execute(() -> {
            try {
                MusicLibraryDatabase.LibraryDao dao = libraryDatabase.libraryDao();
                libraryDatabase.runInTransaction(() -> {
                    if (noAuthorizedTreesRemain) {
                        dao.deleteAllTracks();
                    } else {
                        dao.deleteTracksFromTree(tree.toString());
                    }
                    // A failed subsequent rescan cannot validate old rows.
                    dao.deleteScanState();
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Cannot remove deleted folder's cached metadata",
                        exception);
            }
        });
    }

    private ScanResult scanSelectedFolders(int request, List<Uri> trees,
            Map<String, MusicLibraryDatabase.TrackRow> previous,
            boolean fullRescan) {
        ScanResult result = new ScanResult();
        Set<String> visited = new HashSet<>();
        int readableTrees = 0;
        for (Uri tree : trees) {
            if (closed || request != latestRequest) break;
            try {
                scanTree(tree, request, visited, previous, fullRescan, result);
                readableTrees++;
            } catch (SecurityException exception) {
                Log.w(TAG, "Lost access to selected folder " + tree, exception);
                throw exception;
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to scan selected folder " + tree, exception);
                throw exception;
            }
        }
        if (!closed && request == latestRequest
                && !trees.isEmpty() && readableTrees == 0) {
            throw new IllegalStateException("All selected music folders were inaccessible");
        }

        // Entries missing from a completed enumeration are no longer indexed.
        for (String uri : previous.keySet()) {
            if (!result.retainedUris.contains(uri)) result.removed.add(uri);
        }
        result.songs.sort(Comparator.comparing((Song song) -> song.title,
                String.CASE_INSENSITIVE_ORDER)
                .thenComparing(song -> song.uri.toString()));
        return result;
    }

    private void scanTree(Uri tree, int request, Set<String> visited,
            Map<String, MusicLibraryDatabase.TrackRow> previous,
            boolean fullRescan, ScanResult result) {
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        ArrayDeque<String> directories = new ArrayDeque<>();
        directories.add(rootId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };

        while (!directories.isEmpty() && !closed && request == latestRequest) {
            String directoryId = directories.removeFirst();
            String visitKey = tree.getAuthority() + "|" + directoryId;
            if (!visited.add(visitKey)) continue;
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree, directoryId);
            try (Cursor cursor = resolver.query(childrenUri, projection,
                    null, null, null)) {
                if (cursor == null) {
                    throw new IllegalStateException(
                            "No child list for authorized directory " + childrenUri);
                }
                int idColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE);
                int sizeColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_SIZE);
                int modifiedColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED);

                while (cursor.moveToNext() && !closed && request == latestRequest) {
                    String docId = cursor.getString(idColumn);
                    String name = cursor.getString(nameColumn);
                    String mime = cursor.getString(mimeColumn);
                    if (docId == null || docId.isEmpty()) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        directories.addLast(docId);
                    } else if (isAudio(name, mime)) {
                        Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(
                                tree, docId);
                        String uri = fileUri.toString();
                        if (!result.visitedDocuments.add(uri)) continue;
                        long size = cursorLong(cursor, sizeColumn);
                        long modified = cursorLong(cursor, modifiedColumn);
                        MusicLibraryDatabase.TrackRow cached = previous.get(uri);
                        boolean reuse = !fullRescan && cached != null
                                && tree.toString().equals(cached.sourceTree)
                                && MusicFileChangeDetector.unchanged(
                                        cached.fileSize, cached.lastModified,
                                        cached.fileName, size, modified, name);
                        Song song;
                        if (reuse) {
                            song = songFromRow(cached);
                            result.reused++;
                        } else {
                            song = inspectAudio(tree, fileUri, name);
                            if (song != null) {
                                result.changes.add(trackRow(
                                        song, name, size, modified));
                                if (cached == null) result.added++;
                                else result.rechecked++;
                            }
                        }
                        if (song != null) {
                            result.retainedUris.add(uri);
                            result.songs.add(song);
                        }
                    }
                }
            } catch (SecurityException exception) {
                Log.w(TAG, "No access to directory within selected tree", exception);
                throw exception;
            } catch (RuntimeException exception) {
                Log.w(TAG, "Cannot enumerate directory within selected tree", exception);
                throw exception;
            }
        }
    }

    /** A missing provider size/mtime is unknown, not an unchanged file. */
    private static long cursorLong(Cursor cursor, int column) {
        return column >= 0 && !cursor.isNull(column) ? cursor.getLong(column) : -1L;
    }

    private static boolean isAudio(String filename, String mime) {
        return MusicFilePolicy.isAudio(filename, mime);
    }

    /**
     * Confirm that an authorized SAF document contains an actual audio track
     * before creating a playable row. A provider's audio/* MIME type or a
     * filename extension alone is not evidence: some providers label
     * directories or non-audio documents as audio.
     *
     * Return -1 when the document is not recognized as audio. A verified
     * audio document may legitimately have an unknown duration (zero).
     * MediaExtractor is short-lived and runs only on the scan worker.
     */
    private long probeAudioTrack(Uri fileUri) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, fileUri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                        ? format.getLong(MediaFormat.KEY_DURATION) : 0L;
                return Math.max(0L, durationUs / 1000L);
            }
        } catch (java.io.IOException | RuntimeException exception) {
            Log.d(TAG, "Cannot recognize or extract audio from: " + fileUri,
                    exception);
        } finally {
            extractor.release();
        }
        return -1L;
    }

    private Song inspectAudio(Uri sourceTree, Uri fileUri, String filename) {
        long verifiedDurationMs = probeAudioTrack(fileUri);
        if (verifiedDurationMs < 0L) {
            Log.i(TAG, "Skipping unrecognized or unsupported audio candidate: " + fileUri);
            return null;
        }

        String fallbackTitle = filename == null ? "Unknown Title" : filename;
        int dot = fallbackTitle.lastIndexOf('.');
        if (dot > 0) fallbackTitle = fallbackTitle.substring(0, dot);

        String title = fallbackTitle;
        String artist = "Unknown Artist";
        String album = "Unknown Album";
        String genre = "Unknown Genre";
        long duration = verifiedDurationMs;
        int track = 0;

        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            // Only a URI returned from a selected DocumentsProvider tree.
            metadata.setDataSource(context, fileUri);
            title = name(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE), fallbackTitle);
            artist = name(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST), artist);
            album = name(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM), album);
            genre = name(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_GENRE), genre);
            long taggedDuration = parseLong(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (taggedDuration > 0L) duration = taggedDuration;
            track = parseTrack(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
        } catch (RuntimeException exception) {
            // A positively verified audio stream remains browseable by
            // filename even when it lacks readable tags.
            Log.d(TAG, "Metadata unavailable for " + fileUri, exception);
        } finally {
            try {
                metadata.release();
            } catch (java.io.IOException | RuntimeException exception) {
                // A cleanup error must not discard a readable track or abort
                // the remaining scan of user-selected music folders.
                Log.w(TAG, "Unable to release metadata retriever for " + fileUri,
                        exception);
            }
        }
        long songId = fileUri.toString().hashCode() & 0xffffffffL;
        return new Song(songId, fileUri, sourceTree, title, artist, album, genre,
                -1L, -1L, duration, track);
    }

    private static String name(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static long parseLong(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static int parseTrack(String value) {
        if (value == null) return 0;
        int separator = value.indexOf('/');
        return (int) Math.min(Integer.MAX_VALUE,
                parseLong(separator >= 0 ? value.substring(0, separator) : value));
    }

    void close() {
        closed = true;
        invalidate();
        // Close Room after the queued worker operation has finished or exited.
        worker.execute(libraryDatabase::close);
        worker.shutdown();
    }
}
