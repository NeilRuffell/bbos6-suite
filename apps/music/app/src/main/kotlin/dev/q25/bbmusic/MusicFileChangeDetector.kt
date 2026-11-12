package dev.q25.bbmusic

/** Conservative SAF file fingerprint comparison, formerly pure Java. */
internal object MusicFileChangeDetector {
    @JvmStatic fun unchanged(
        previousSize: Long,
        previousModified: Long,
        previousName: String?,
        currentSize: Long,
        currentModified: Long,
        currentName: String?
    ): Boolean =
        previousSize >= 0 && currentSize >= 0 &&
            previousModified > 0 && currentModified > 0 &&
            previousSize == currentSize &&
            previousModified == currentModified &&
            previousName == currentName
}
