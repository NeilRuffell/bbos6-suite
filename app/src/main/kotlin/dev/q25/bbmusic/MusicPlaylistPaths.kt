package dev.q25.bbmusic

import java.net.URI
import java.nio.file.Paths

/** Keep playlist file references confined to the verified primary root. */
internal object MusicPlaylistPaths {
    @JvmStatic fun absolute(primaryRoot: String?, documentSuffix: String?): String? {
        try {
            if (documentSuffix == null || documentSuffix.startsWith("/") ||
                documentSuffix.startsWith("\\") ||
                documentSuffix.indexOf('\u0000') >= 0) return null
            val root = Paths.get(primaryRoot ?: return null).toAbsolutePath().normalize()
            val candidate = root.resolve(documentSuffix).normalize()
            return if (candidate != root && candidate.startsWith(root))
                candidate.toString() else null
        } catch (_: RuntimeException) {
            return null
        }
    }

    /** Reject unanchored and network references; never invent a file path. */
    @JvmStatic fun resolve(
        primaryRoot: String?,
        playlistFolder: String?,
        raw: String?
    ): String? {
        try {
            if (raw == null || raw.isEmpty() || raw.indexOf('\u0000') >= 0) return null
            var value = raw.trim().replace('\\', '/')
            if (value.matches(Regex("(?i)^[a-z]:/.*"))) return null
            if (value.startsWith("file:")) {
                val uri = URI.create(value)
                if (uri.scheme?.equals("file", ignoreCase = true) != true ||
                    !uri.host.isNullOrEmpty()) return null
                value = Paths.get(uri).toString()
            } else if (value.contains("://") || value.startsWith("//")) {
                return null
            }
            val root = Paths.get(primaryRoot ?: return null).toAbsolutePath().normalize()
            var path = Paths.get(value)
            if (!path.isAbsolute) {
                if (playlistFolder == null) return null
                path = Paths.get(playlistFolder).resolve(path)
            }
            path = path.toAbsolutePath().normalize()
            return if (path != root && path.startsWith(root))
                path.toString() else null
        } catch (_: RuntimeException) {
            return null
        }
    }
}
