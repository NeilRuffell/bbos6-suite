package dev.q25.bbmusic;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Pure path conversion for interoperable primary-storage M3U8 references. */
final class MusicPlaylistPaths {
    private MusicPlaylistPaths() {}

    static String absolute(String primaryRoot, String documentSuffix) {
        try {
            if (documentSuffix == null || documentSuffix.startsWith("/")
                    || documentSuffix.startsWith("\\")
                    || documentSuffix.indexOf('\u0000') >= 0) return null;
            Path root = Paths.get(primaryRoot).toAbsolutePath().normalize();
            Path candidate = root.resolve(documentSuffix).normalize();
            return !candidate.equals(root) && candidate.startsWith(root)
                    ? candidate.toString() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Never guess an unanchored relative path or import a network item. */
    static String resolve(String primaryRoot, String playlistFolder, String raw) {
        try {
            if (raw == null || raw.isEmpty() || raw.indexOf('\u0000') >= 0) {
                return null;
            }
            String value = raw.trim().replace('\\', '/');
            if (value.matches("(?i)^[a-z]:/.*")) return null;
            if (value.startsWith("file:")) {
                URI uri = URI.create(value);
                if (!"file".equalsIgnoreCase(uri.getScheme())
                        || (uri.getHost() != null && !uri.getHost().isEmpty())) {
                    return null;
                }
                value = Paths.get(uri).toString();
            } else if (value.contains("://") || value.startsWith("//")) {
                return null;
            }
            Path root = Paths.get(primaryRoot).toAbsolutePath().normalize();
            Path path = Paths.get(value);
            if (!path.isAbsolute()) {
                if (playlistFolder == null) return null;
                path = Paths.get(playlistFolder).resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            return !path.equals(root) && path.startsWith(root)
                    ? path.toString() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
