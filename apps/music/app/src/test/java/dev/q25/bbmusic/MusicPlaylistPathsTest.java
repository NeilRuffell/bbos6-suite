package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class MusicPlaylistPathsTest {
    private static final String ROOT = "/storage/emulated/0";

    @Test public void externalStorageDocumentMapsToNativePrimaryPath() {
        assertEquals("/storage/emulated/0/Music/One.mp3",
                MusicPlaylistPaths.absolute(ROOT, "Music/One.mp3"));
    }

    @Test public void rejectsEscapesAndUnanchoredRelativeEntries() {
        assertNull(MusicPlaylistPaths.absolute(ROOT, "../../system/file.mp3"));
        assertNull(MusicPlaylistPaths.absolute(ROOT, "/tmp/file.mp3"));
        assertNull(MusicPlaylistPaths.resolve(ROOT, null, "One.mp3"));
        assertNull(MusicPlaylistPaths.resolve(ROOT, "/storage/emulated/0/Music",
                "../../../outside.mp3"));
    }

    @Test public void relativeM3u8PathsResolveFromPlaylistDocumentFolder() {
        assertEquals("/storage/emulated/0/Music/One.mp3",
                MusicPlaylistPaths.resolve(ROOT,
                        "/storage/emulated/0/Playlists", "../Music/One.mp3"));
    }

    @Test public void acceptsFileUrlAndRejectsNetworkOrContentUris() {
        assertEquals("/storage/emulated/0/Music/A B.mp3",
                MusicPlaylistPaths.resolve(ROOT, null,
                        "file:///storage/emulated/0/Music/A%20B.mp3"));
        assertNull(MusicPlaylistPaths.resolve(ROOT, null,
                "https://example.com/song.mp3"));
        assertNull(MusicPlaylistPaths.resolve(ROOT, null,
                "content://com.android.externalstorage.documents/foo"));
        assertNull(MusicPlaylistPaths.resolve(ROOT, null,
                "C:\\Music\\One.mp3"));
    }

    @Test public void similarlyNamedButDifferentDirectoriesNeverMatch() {
        assertEquals("/storage/emulated/0/Music/B/One.mp3",
                MusicPlaylistPaths.resolve(ROOT, null,
                        "/storage/emulated/0/Music/B/One.mp3"));
        assertNull(MusicPlaylistPaths.resolve(ROOT, null,
                "/storage/emulated/01/Music/One.mp3"));
    }
}
