package dev.q25.bbmusic

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Private app-owned catalog of authorized SAF media metadata and playlist rows.
 * The database filename, Room version, table/column names, indices, foreign keys,
 * SQL queries and non-destructive upgrade paths are unchanged from Java.
 */
@Database(
    entities = [
        MusicLibraryDatabase.TrackRow::class,
        MusicLibraryDatabase.ScanRow::class,
        MusicLibraryDatabase.PlaylistRow::class,
        MusicLibraryDatabase.PlaylistEntryRow::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusicLibraryDatabase : RoomDatabase() {
    @Entity(tableName = "library_tracks", indices = [Index(value = ["sourceTree"])])
    class TrackRow {
        @field:PrimaryKey
        @JvmField var uri: String = ""
        @JvmField var sourceTree: String = ""
        @JvmField var title: String = ""
        @JvmField var artist: String = ""
        @JvmField var album: String = ""
        @JvmField var genre: String = ""
        @JvmField var id: Long = 0L
        @JvmField var artistId: Long = 0L
        @JvmField var albumId: Long = 0L
        @JvmField var durationMs: Long = 0L
        @JvmField var trackNumber: Int = 0
        @field:ColumnInfo(defaultValue = "''")
        @JvmField var fileName: String = ""
        @field:ColumnInfo(defaultValue = "-1")
        @JvmField var fileSize: Long = -1L
        @field:ColumnInfo(defaultValue = "-1")
        @JvmField var lastModified: Long = -1L
    }

    /** A completed scan exists even for an empty selected folder set. */
    @Entity(tableName = "library_scan")
    class ScanRow {
        @field:PrimaryKey
        @JvmField var id: Int = 1
        @JvmField var selectedTreesKey: String = ""
    }

    @Entity(tableName = "music_playlists", indices = [Index(value = ["name"], unique = true)])
    class PlaylistRow {
        @field:PrimaryKey(autoGenerate = true)
        @JvmField var id: Long = 0L
        @JvmField var name: String = ""
    }

    @Entity(
        tableName = "music_playlist_entries",
        foreignKeys = [
            ForeignKey(
                entity = PlaylistRow::class,
                parentColumns = ["id"],
                childColumns = ["playlistId"],
                onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [Index(value = ["playlistId"])]
    )
    class PlaylistEntryRow {
        @field:PrimaryKey(autoGenerate = true)
        @JvmField var id: Long = 0L
        @JvmField var playlistId: Long = 0L
        @JvmField var uri: String = ""
        @JvmField var title: String = ""
        @JvmField var position: Int = 0
    }

    /** Field names are relied upon by Room's original projection SQL. */
    class PlaylistSummary {
        @JvmField var id: Long = 0L
        @JvmField var name: String = ""
        @JvmField var songCount: Int = 0
    }

    @Dao
    interface PlaylistDao {
        @Query(
            "SELECT p.id AS id, p.name AS name, COUNT(e.id) AS songCount " +
                "FROM music_playlists p LEFT JOIN music_playlist_entries e " +
                "ON p.id = e.playlistId GROUP BY p.id " +
                "ORDER BY p.name COLLATE NOCASE, p.id"
        )
        fun playlists(): List<PlaylistSummary>

        @Query("SELECT * FROM music_playlists WHERE id = :id LIMIT 1")
        fun playlist(id: Long): PlaylistRow?

        @Query(
            "SELECT COUNT(*) FROM music_playlists " +
                "WHERE name = :name COLLATE NOCASE AND id != :exceptId"
        )
        fun nameCount(name: String, exceptId: Long): Int

        @Insert
        fun create(playlist: PlaylistRow): Long

        @Query("UPDATE music_playlists SET name = :name WHERE id = :id")
        fun rename(id: Long, name: String): Int

        @Query("DELETE FROM music_playlists WHERE id = :id")
        fun delete(id: Long): Int

        @Query("SELECT * FROM music_playlist_entries WHERE playlistId = :playlistId ORDER BY position, id")
        fun entries(playlistId: Long): List<PlaylistEntryRow>

        @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_playlist_entries WHERE playlistId = :playlistId")
        fun nextPosition(playlistId: Long): Int

        @Insert
        fun add(entry: PlaylistEntryRow): Long

        @Query("DELETE FROM music_playlist_entries WHERE id = :entryId AND playlistId = :playlistId")
        fun remove(playlistId: Long, entryId: Long): Int

        @Query("UPDATE music_playlist_entries SET position = :position WHERE id = :entryId")
        fun setPosition(entryId: Long, position: Int)
    }

    abstract fun playlistDao(): PlaylistDao

    @Dao
    interface LibraryDao {
        @Query("SELECT * FROM library_tracks ORDER BY title COLLATE NOCASE, uri")
        fun tracks(): List<TrackRow>

        @Query("SELECT * FROM library_scan WHERE id = 1 LIMIT 1")
        fun scanState(): ScanRow?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insertTracks(tracks: List<TrackRow>)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun setScanState(row: ScanRow)

        @Query("DELETE FROM library_tracks")
        fun deleteAllTracks()

        @Query("DELETE FROM library_tracks WHERE uri IN (:uris)")
        fun deleteTracks(uris: List<String>)

        @Query("DELETE FROM library_tracks WHERE sourceTree = :sourceTree")
        fun deleteTracksFromTree(sourceTree: String)

        @Query("DELETE FROM library_scan")
        fun deleteScanState()
    }

    abstract fun libraryDao(): LibraryDao

    companion object {
        private const val DATABASE_NAME = "bbmusic_library.db"

        /** Non-destructive v1 -> v2 migration retains tags and folder grants. */
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN fileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN fileSize INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN lastModified INTEGER NOT NULL DEFAULT -1")
            }
        }

        /** Adding playlist tables must never erase the existing music catalog. */
        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS music_playlists (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_music_playlists_name ON music_playlists(name)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS music_playlist_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "playlistId INTEGER NOT NULL, uri TEXT NOT NULL, " +
                        "title TEXT NOT NULL, position INTEGER NOT NULL, " +
                        "FOREIGN KEY(playlistId) REFERENCES music_playlists(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_music_playlist_entries_playlistId " +
                        "ON music_playlist_entries(playlistId)"
                )
            }
        }

        @JvmStatic
        fun open(context: Context): MusicLibraryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MusicLibraryDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
