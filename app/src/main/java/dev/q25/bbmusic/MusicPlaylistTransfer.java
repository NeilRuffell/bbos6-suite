package dev.q25.bbmusic;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android document-picker M3U8 I/O. Imported entries are resolved ONLY to
 * already-indexed authorized songs, never arbitrary paths or network URLs.
 * External paths are available only for verified primary-storage SAF IDs.
 */
final class MusicPlaylistTransfer {
    private static final String TAG = "BBMusic-M3U8";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final ContentResolver resolver;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String primaryRoot;
    private volatile boolean closed;

    interface Callback<T> {
        void onComplete(T value, String error);
    }

    static final class Imported {
        final List<LocalMusicSource.Song> songs;
        final int unmatched;
        final int total;

        Imported(List<LocalMusicSource.Song> songs, int unmatched, int total) {
            this.songs = songs;
            this.unmatched = unmatched;
            this.total = total;
        }
    }

    MusicPlaylistTransfer(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
        // Resolve the primary-storage path through Android, not a hard-coded
        // /storage/emulated/0 assumption. We never open it as a java.io.File.
        primaryRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    /** A portable path is unavailable for arbitrary/opaque SAF providers. */
    private String pathFor(Uri uri) {
        if (uri == null || !"com.android.externalstorage.documents".equals(
                uri.getAuthority())) return null;
        try {
            String id = DocumentsContract.getDocumentId(uri);
            if (id == null || !id.startsWith("primary:")) return null;
            String tail = id.substring("primary:".length());
            if (tail.isEmpty()) return null;
            return MusicPlaylistPaths.absolute(primaryRoot, tail);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String baseFor(Uri document) {
        String full = pathFor(document);
        if (full == null) return null;
        Path parent = Paths.get(full).getParent();
        return parent == null ? null : parent.toString();
    }

    void read(Uri document, List<LocalMusicSource.Song> catalog,
            Callback<Imported> callback) {
        worker.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = resolver.openInputStream(document)) {
                    if (in == null) throw new IllegalStateException("Cannot open playlist");
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] part = new byte[8192];
                    int count;
                    while ((count = in.read(part)) != -1) {
                        if (buffer.size() + count > MAX_BYTES) {
                            throw new IllegalArgumentException("Playlist exceeds 2 MB");
                        }
                        buffer.write(part, 0, count);
                    }
                    bytes = buffer.toByteArray();
                }
                String data = new String(bytes, StandardCharsets.UTF_8);
                if (data.startsWith("\uFEFF")) data = data.substring(1);
                Map<String, LocalMusicSource.Song> lookup = new HashMap<>();
                for (LocalMusicSource.Song song : catalog) {
                    String key = pathFor(song.uri);
                    if (key != null) lookup.put(key, song);
                }
                String base = baseFor(document);
                ArrayList<LocalMusicSource.Song> resolved = new ArrayList<>();
                int skipped = 0;
                int total = 0;
                for (String raw : data.split("\\r?\\n")) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (++total > 20000) {
                        throw new IllegalArgumentException("Playlist exceeds 20,000 entries");
                    }
                    String path = MusicPlaylistPaths.resolve(primaryRoot, base, line);
                    LocalMusicSource.Song song = path == null ? null : lookup.get(path);
                    if (song == null) skipped++;
                    else resolved.add(song);
                }
                Imported result = new Imported(resolved, skipped, total);
                main.post(() -> { if (!closed) callback.onComplete(result, null); });
            } catch (Exception exception) {
                Log.w(TAG, "Cannot import M3U8", exception);
                main.post(() -> {
                    if (!closed) callback.onComplete(null,
                            "Unable to import M3U8: " + exception.getMessage());
                });
            }
        });
    }

    /** Validate portably representable entries before creating a file. */
    void checkExport(List<MusicLibraryDatabase.PlaylistEntryRow> entries,
            List<LocalMusicSource.Song> catalog, Callback<Boolean> callback) {
        worker.execute(() -> {
            Map<String, LocalMusicSource.Song> indexed = new HashMap<>();
            for (LocalMusicSource.Song song : catalog) {
                indexed.put(song.uri.toString(), song);
            }
            String error = null;
            for (MusicLibraryDatabase.PlaylistEntryRow entry : entries) {
                LocalMusicSource.Song song = indexed.get(entry.uri);
                if (song == null || pathFor(song.uri) == null) {
                    error = "Cannot export: playlist contains unavailable "
                            + "or non-primary-storage tracks";
                    break;
                }
            }
            final String result = error;
            main.post(() -> {
                if (!closed) callback.onComplete(result == null, result);
            });
        });
    }

    /** Do not silently write a partial playlist on unsupported document trees. */
    void write(Uri document, List<MusicLibraryDatabase.PlaylistEntryRow> entries,
            List<LocalMusicSource.Song> catalog, Callback<Integer> callback) {
        worker.execute(() -> {
            try {
                Map<String, LocalMusicSource.Song> indexed = new HashMap<>();
                for (LocalMusicSource.Song song : catalog) {
                    indexed.put(song.uri.toString(), song);
                }
                StringBuilder text = new StringBuilder("#EXTM3U\n");
                int count = 0;
                for (MusicLibraryDatabase.PlaylistEntryRow entry : entries) {
                    LocalMusicSource.Song song = indexed.get(entry.uri);
                    String path = song == null ? null : pathFor(song.uri);
                    if (path == null) {
                        throw new IllegalArgumentException(
                                "Playlist contains missing or non-primary-storage tracks");
                    }
                    String artist = song.artist.replace('\n', ' ').replace('\r', ' ');
                    String title = song.title.replace('\n', ' ').replace('\r', ' ');
                    text.append("#EXTINF:").append(song.durationMs > 0
                                    ? song.durationMs / 1000 : -1)
                            .append(',').append(artist).append(" - ")
                            .append(title).append('\n')
                            .append(path).append('\n');
                    count++;
                }
                try (OutputStream out = resolver.openOutputStream(document, "wt")) {
                    if (out == null) throw new IllegalStateException("Cannot write playlist");
                    out.write(text.toString().getBytes(StandardCharsets.UTF_8));
                }
                final int written = count;
                main.post(() -> { if (!closed) callback.onComplete(written, null); });
            } catch (Exception exception) {
                Log.w(TAG, "Cannot export M3U8", exception);
                main.post(() -> {
                    if (!closed) callback.onComplete(null,
                            "Unable to export M3U8: " + exception.getMessage());
                });
            }
        });
    }

    void close() {
        closed = true;
        worker.shutdown();
    }
}
