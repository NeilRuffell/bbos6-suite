package dev.q25.bbmusic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app-private, ordered playlist repository. No audio is copied or moved:
 * entries keep exact SAF URIs so selected-folder access controls still apply.
 * Room work is serialized off the main thread; results return on main.
 */
final class MusicPlaylists {
    private static final String TAG = "BBMusic-Playlists";
    private final Context context;
    private final MusicLibraryDatabase database;
    private final MusicLibraryDatabase.PlaylistDao dao;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    interface Callback<T> {
        void onComplete(T value, String error);
    }

    MusicPlaylists(Context context) {
        this.context = context.getApplicationContext();
        database = MusicLibraryDatabase.open(this.context);
        dao = database.playlistDao();
    }

    private <T> void submit(Callable<T> operation, Callback<T> callback) {
        if (closed) return;
        worker.execute(() -> {
            T value = null;
            String error = null;
            try {
                value = operation.call();
            } catch (Exception exception) {
                Log.e(TAG, "Playlist operation failed", exception);
                error = exception.getMessage() == null
                        ? "Playlist operation failed" : exception.getMessage();
            }
            final T result = value;
            final String failure = error;
            if (callback != null) {
                main.post(() -> {
                    if (!closed) callback.onComplete(result, failure);
                });
            }
        });
    }

    void list(Callback<List<MusicLibraryDatabase.PlaylistSummary>> callback) {
        submit(dao::playlists, callback);
    }

    void entries(long playlistId,
            Callback<List<MusicLibraryDatabase.PlaylistEntryRow>> callback) {
        submit(() -> dao.entries(playlistId), callback);
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Playlist name required");
        if (name.length() > 128) {
            throw new IllegalArgumentException("Playlist name is too long");
        }
        return name;
    }

    private String availableName(String requested) {
        String base = validName(requested);
        String candidate = base;
        int suffix = 2;
        while (dao.nameCount(candidate, -1L) > 0) {
            String ending = " (" + suffix++ + ")";
            candidate = base.substring(0, Math.min(base.length(),
                    128 - ending.length())) + ending;
        }
        return candidate;
    }

    void create(String name, Callback<Long> callback) {
        submit(() -> {
            String verified = validName(name);
            if (dao.nameCount(verified, -1L) > 0) {
                throw new IllegalArgumentException("A playlist with that name exists");
            }
            MusicLibraryDatabase.PlaylistRow row = new MusicLibraryDatabase.PlaylistRow();
            row.name = verified;
            return dao.create(row);
        }, callback);
    }

    void rename(long id, String name, Callback<Boolean> callback) {
        submit(() -> {
            String verified = validName(name);
            if (dao.nameCount(verified, id) > 0) {
                throw new IllegalArgumentException("A playlist with that name exists");
            }
            return dao.rename(id, verified) != 0;
        }, callback);
    }

    void delete(long id, Callback<Boolean> callback) {
        submit(() -> dao.delete(id) != 0, callback);
    }

    private List<LocalMusicSource.Song> authorized(List<LocalMusicSource.Song> songs) {
        Set<android.net.Uri> trees = new HashSet<>(MusicFolders.selectedTrees(context));
        ArrayList<LocalMusicSource.Song> accepted = new ArrayList<>();
        if (songs == null) return accepted;
        for (LocalMusicSource.Song song : songs) {
            if (song != null && song.uri != null && trees.contains(song.sourceTree)
                    && "content".equals(song.uri.getScheme())) {
                accepted.add(song);
            }
        }
        return accepted;
    }

    private void insertSongs(long playlistId, List<LocalMusicSource.Song> songs) {
        if (dao.playlist(playlistId) == null) {
            throw new IllegalArgumentException("Playlist no longer exists");
        }
        List<LocalMusicSource.Song> verified = authorized(songs);
        if (songs == null || verified.size() != songs.size()) {
            throw new SecurityException(
                    "Music folder access changed; refresh your library");
        }
        int position = dao.nextPosition(playlistId);
        for (LocalMusicSource.Song song : verified) {
            MusicLibraryDatabase.PlaylistEntryRow row =
                    new MusicLibraryDatabase.PlaylistEntryRow();
            row.playlistId = playlistId;
            row.uri = song.uri.toString();
            row.title = song.title;
            row.position = position++;
            dao.add(row);
        }
    }

    void add(long playlistId, List<LocalMusicSource.Song> songs,
            Callback<Boolean> callback) {
        submit(() -> {
            database.runInTransaction(() -> insertSongs(playlistId, songs));
            return true;
        }, callback);
    }

    /** Import creates a uniquely named playlist atomically with its entries. */
    void importSongs(String suggestedName, List<LocalMusicSource.Song> songs,
            Callback<Long> callback) {
        submit(() -> {
            final long[] created = {-1L};
            database.runInTransaction(() -> {
                MusicLibraryDatabase.PlaylistRow playlist =
                        new MusicLibraryDatabase.PlaylistRow();
                playlist.name = availableName(suggestedName);
                created[0] = dao.create(playlist);
                insertSongs(created[0], songs);
            });
            return created[0];
        }, callback);
    }

    void remove(long playlistId, long entryId, Callback<Boolean> callback) {
        submit(() -> dao.remove(playlistId, entryId) != 0, callback);
    }

    /** Swap saved position values, preserving duplicates and stable row IDs. */
    void move(long playlistId, long entryId, int direction,
            Callback<Boolean> callback) {
        submit(() -> {
            if (direction != -1 && direction != 1) return false;
            List<MusicLibraryDatabase.PlaylistEntryRow> entries =
                    dao.entries(playlistId);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id != entryId) continue;
                int target = i + direction;
                if (target < 0 || target >= entries.size()) return false;
                MusicLibraryDatabase.PlaylistEntryRow first = entries.get(i);
                MusicLibraryDatabase.PlaylistEntryRow second = entries.get(target);
                database.runInTransaction(() -> {
                    dao.setPosition(first.id, second.position);
                    dao.setPosition(second.id, first.position);
                });
                return true;
            }
            return false;
        }, callback);
    }

    void close() {
        closed = true;
        worker.execute(database::close);
        worker.shutdown();
    }
}
