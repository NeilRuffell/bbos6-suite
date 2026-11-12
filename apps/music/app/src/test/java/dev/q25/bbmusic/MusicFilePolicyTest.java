package dev.q25.bbmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class MusicFilePolicyTest {
    @Test public void hiddenDirectoriesAreExcluded() {
        assertFalse(MusicFilePolicy.isVisibleDirectory(".thumbnails"));
        assertFalse(MusicFilePolicy.isVisibleDirectory(".nomedia"));
        assertFalse(MusicFilePolicy.isVisibleDirectory("."));
        assertFalse(MusicFilePolicy.isVisibleDirectory(null));
        assertTrue(MusicFilePolicy.isVisibleDirectory("Albums"));
        assertTrue(MusicFilePolicy.isVisibleDirectory("My.Music"));
    }

    @Test public void audioFilesAreIncludedByMimeOrExtension() {
        assertTrue(MusicFilePolicy.isAudio("song.mp3", "application/octet-stream"));
        assertTrue(MusicFilePolicy.isAudio("song.FLAC", null));
        assertTrue(MusicFilePolicy.isAudio("no-extension", "audio/mpeg"));
        assertTrue(MusicFilePolicy.isAudio("song.wav", "audio/wav"));
    }

    @Test public void nonAudioFilesAreExcluded() {
        assertFalse(MusicFilePolicy.isAudio("cover.jpg", "image/jpeg"));
        assertFalse(MusicFilePolicy.isAudio("notes.txt", "text/plain"));
        assertFalse(MusicFilePolicy.isAudio("movie.mp4", "video/mp4"));
        assertFalse(MusicFilePolicy.isAudio("folder.mp3",
                "vnd.android.document/directory"));
        assertFalse(MusicFilePolicy.isAudio(null, null));
    }

    @Test public void playlistsAreAlwaysExcluded() {
        assertFalse(MusicFilePolicy.isAudio("mix.m3u", "audio/mpegurl"));
        assertFalse(MusicFilePolicy.isAudio("mix.M3U8", "audio/x-mpegurl"));
        assertFalse(MusicFilePolicy.isAudio("mix.pls", "audio/mpeg"));
        assertFalse(MusicFilePolicy.isAudio("mix.xspf", "application/xml"));
    }

    @Test public void videoMimeDoesNotPassThroughAudioExtension() {
        assertFalse(MusicFilePolicy.isAudio("mislabelled.mp3", "video/mp4"));
    }
}
