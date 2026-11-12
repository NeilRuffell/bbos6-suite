package dev.q25.bbmusic

/** Display only provider-established ExternalStorage SAF paths. */
internal object MusicDocumentPath {
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    @JvmStatic fun readableLocation(
        authority: String?,
        documentId: String?,
        displayName: String?
    ): String? {
        if (authority != EXTERNAL_STORAGE_AUTHORITY ||
            documentId == null || displayName == null ||
            displayName.trim { it <= ' ' }.isEmpty()) return null
        val colon = documentId.indexOf(':')
        if (colon <= 0 || colon >= documentId.length - 1) return null
        val volume = documentId.substring(0, colon)
        val relative = documentId.substring(colon + 1)
        val primary = volume.equals("primary", ignoreCase = true)
        val removable = Regex("(?i)[0-9a-f]{4}-[0-9a-f]{4}").matches(volume)
        if (!primary && !removable) return null
        if (relative.startsWith("/") || relative.contains('\\') ||
            relative.contains("//") || relative.contains("../") ||
            relative == "..") return null
        val actualName = relative.substringAfterLast('/')
        if (!actualName.equals(displayName, ignoreCase = true)) return null
        return (if (primary) "Internal storage" else volume) + "/" + relative
    }
}
