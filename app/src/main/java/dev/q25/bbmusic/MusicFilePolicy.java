package dev.q25.bbmusic;

import java.util.Locale;

/**
 * Shared file-type policy for library scanning and SAF folder browsing.
 * File candidates still require actual audio-track validation by the scanner
 * before they become playable library songs.
 */
final class MusicFilePolicy {
    // DocumentsContract.Document.MIME_TYPE_DIR (kept literal for JVM tests).
    private static final String SAF_DIRECTORY_MIME = "vnd.android.document/directory";

    private MusicFilePolicy() {}

    /** Hidden directories are not shown in Music > Folders. */
    static boolean isVisibleDirectory(String name) {
        return name != null && !name.startsWith(".");
    }

    /** Same audio candidate criteria as the library scanner. */
    static boolean isAudio(String filename, String mime) {
        if (SAF_DIRECTORY_MIME.equals(mime)) return false;
        // Playlists are not audio tracks, even if marked audio/* by a provider.
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".m3u") || lower.endsWith(".m3u8")
                    || lower.endsWith(".pls") || lower.endsWith(".xspf")) {
                return false;
            }
        }
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return true;
        }
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return false;
        }
        return hasAudioExtension(filename);
    }

    private static boolean hasAudioExtension(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a")
                || lower.endsWith(".aac") || lower.endsWith(".flac")
                || lower.endsWith(".ogg") || lower.endsWith(".opus")
                || lower.endsWith(".wav") || lower.endsWith(".wma")
                || lower.endsWith(".aiff") || lower.endsWith(".aif")
                || lower.endsWith(".alac") || lower.endsWith(".ape")
                || lower.endsWith(".amr");
    }
}
