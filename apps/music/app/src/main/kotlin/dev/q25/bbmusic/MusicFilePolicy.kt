package dev.q25.bbmusic

import java.util.Locale

/** Candidate MIME and extension policy; scanner still validates actual tracks. */
internal object MusicFilePolicy {
    private const val SAF_DIRECTORY_MIME = "vnd.android.document/directory"
    private val audioExtensions = arrayOf(
        ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav",
        ".wma", ".aiff", ".aif", ".alac", ".ape", ".amr"
    )
    private val playlistExtensions = arrayOf(".m3u", ".m3u8", ".pls", ".xspf")

    @JvmStatic fun isVisibleDirectory(name: String?): Boolean =
        name != null && !name.startsWith(".")

    @JvmStatic fun isAudio(filename: String?, mime: String?): Boolean {
        if (mime == SAF_DIRECTORY_MIME) return false
        val lower = filename?.lowercase(Locale.ROOT)
        if (lower != null && playlistExtensions.any(lower::endsWith)) return false
        val type = mime?.lowercase(Locale.ROOT)
        if (type?.startsWith("audio/") == true) return true
        if (type?.startsWith("video/") == true) return false
        return lower != null && audioExtensions.any(lower::endsWith)
    }
}
