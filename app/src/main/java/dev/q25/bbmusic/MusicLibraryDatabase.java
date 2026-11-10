package dev.q25.bbmusic;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;

/**
 * Private, app-owned music catalog. The original files remain in the user's
 * Storage Access Framework folders; only their metadata and authorized URIs
 * are cached here. Catalog and playlist queries use dedicated background workers.
 */
@Database(entities = {MusicLibraryDatabase.TrackRow.class,
        MusicLibraryDatabase.ScanRow.class,
        MusicLibraryDatabase.PlaylistRow.class,
        MusicLibraryDatabase.PlaylistEntryRow.class}, version = 3, exportSchema = false)
abstract class MusicLibraryDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "bbmusic_library.db";

    @Entity(tableName = "library_tracks", indices = {@Index("sourceTree")})
    static class TrackRow {
        @PrimaryKey @NonNull public String uri = "";
        @NonNull public String sourceTree = "";
        @NonNull public String title = "";
        @NonNull public String artist = "";
        @NonNull public String album = "";
        @NonNull public String genre = "";
        public long id;
        public long artistId;
        public long albumId;
        public long durationMs;
        public int trackNumber;
        @NonNull @ColumnInfo(defaultValue = "''") public String fileName = "";
        @ColumnInfo(defaultValue = "-1") public long fileSize = -1L;
        @ColumnInfo(defaultValue = "-1") public long lastModified = -1L;
    }

    /**
     * A completed scan is recorded even for an empty collection. A different
     * authorized folder set makes the cached snapshot invalid.
     */
    @Entity(tableName = "library_scan")
    static class ScanRow {
        @PrimaryKey public int id = 1;
        @NonNull public String selectedTreesKey = "";
    }

    /** Only playlist names and ordered, stable SAF track references live here. */
    @Entity(tableName = "music_playlists", indices = {
            @Index(value = {"name"}, unique = true)})
    static class PlaylistRow {
        @PrimaryKey(autoGenerate = true) public long id;
        @NonNull public String name = "";
    }

    @Entity(tableName = "music_playlist_entries",
            foreignKeys = @ForeignKey(entity = PlaylistRow.class,
                    parentColumns = "id", childColumns = "playlistId",
                    onDelete = ForeignKey.CASCADE),
            indices = {@Index("playlistId")})
    static class PlaylistEntryRow {
        @PrimaryKey(autoGenerate = true) public long id;
        public long playlistId;
        @NonNull public String uri = "";
        @NonNull public String title = "";
        public int position;
    }

    static class PlaylistSummary {
        public long id;
        @NonNull public String name = "";
        public int songCount;
    }

    @Dao
    interface PlaylistDao {
        @Query("SELECT p.id AS id, p.name AS name, COUNT(e.id) AS songCount "
                + "FROM music_playlists p LEFT JOIN music_playlist_entries e "
                + "ON p.id = e.playlistId GROUP BY p.id "
                + "ORDER BY p.name COLLATE NOCASE, p.id")
        List<PlaylistSummary> playlists();

        @Query("SELECT * FROM music_playlists WHERE id = :id LIMIT 1")
        PlaylistRow playlist(long id);

        @Query("SELECT COUNT(*) FROM music_playlists "
                + "WHERE name = :name COLLATE NOCASE AND id != :exceptId")
        int nameCount(String name, long exceptId);

        @Insert
        long create(PlaylistRow playlist);

        @Query("UPDATE music_playlists SET name = :name WHERE id = :id")
        int rename(long id, String name);

        @Query("DELETE FROM music_playlists WHERE id = :id")
        int delete(long id);

        @Query("SELECT * FROM music_playlist_entries "
                + "WHERE playlistId = :playlistId ORDER BY position, id")
        List<PlaylistEntryRow> entries(long playlistId);

        @Query("SELECT COALESCE(MAX(position), -1) + 1 "
                + "FROM music_playlist_entries WHERE playlistId = :playlistId")
        int nextPosition(long playlistId);

        @Insert
        long add(PlaylistEntryRow entry);

        @Query("DELETE FROM music_playlist_entries "
                + "WHERE id = :entryId AND playlistId = :playlistId")
        int remove(long playlistId, long entryId);

        @Query("UPDATE music_playlist_entries SET position = :position WHERE id = :entryId")
        void setPosition(long entryId, int position);
    }

    abstract PlaylistDao playlistDao();

    @Dao
    interface LibraryDao {
        @Query("SELECT * FROM library_tracks ORDER BY title COLLATE NOCASE, uri")
        List<TrackRow> tracks();

        @Query("SELECT * FROM library_scan WHERE id = 1 LIMIT 1")
        ScanRow scanState();

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertTracks(List<TrackRow> tracks);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void setScanState(ScanRow row);

        @Query("DELETE FROM library_tracks")
        void deleteAllTracks();

        @Query("DELETE FROM library_tracks WHERE uri IN (:uris)")
        void deleteTracks(List<String> uris);

        @Query("DELETE FROM library_tracks WHERE sourceTree = :sourceTree")
        void deleteTracksFromTree(String sourceTree);

        @Query("DELETE FROM library_scan")
        void deleteScanState();
    }

    abstract LibraryDao libraryDao();

    /** Non-destructive v1 -> v2 migration retains tags and folder grants. */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE library_tracks "
                    + "ADD COLUMN fileName TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE library_tracks "
                    + "ADD COLUMN fileSize INTEGER NOT NULL DEFAULT -1");
            database.execSQL("ALTER TABLE library_tracks "
                    + "ADD COLUMN lastModified INTEGER NOT NULL DEFAULT -1");
        }
    };

    /** Adding playlist tables must never erase the existing music catalog. */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS music_playlists ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "name TEXT NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_music_playlists_name ON music_playlists(name)");
            database.execSQL("CREATE TABLE IF NOT EXISTS music_playlist_entries ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "playlistId INTEGER NOT NULL, uri TEXT NOT NULL, "
                    + "title TEXT NOT NULL, position INTEGER NOT NULL, "
                    + "FOREIGN KEY(playlistId) REFERENCES music_playlists(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "index_music_playlist_entries_playlistId "
                    + "ON music_playlist_entries(playlistId)");
        }
    };

    static MusicLibraryDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                MusicLibraryDatabase.class, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build();
    }
}
