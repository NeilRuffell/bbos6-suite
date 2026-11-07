package dev.q25.bbmusic;

import java.util.Objects;

/** Conservative fingerprint comparison for authorized SAF music documents. */
final class MusicFileChangeDetector {
    private MusicFileChangeDetector() { }

    static boolean unchanged(long previousSize, long previousModified,
            String previousName, long currentSize, long currentModified,
            String currentName) {
        return previousSize >= 0 && currentSize >= 0
                && previousModified > 0 && currentModified > 0
                && previousSize == currentSize
                && previousModified == currentModified
                && Objects.equals(previousName, currentName);
    }
}
