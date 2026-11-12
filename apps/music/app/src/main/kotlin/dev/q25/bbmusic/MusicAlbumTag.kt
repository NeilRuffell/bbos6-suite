package dev.q25.bbmusic

/** No synthetic album tag: a literal "Unknown Album" is still metadata. */
internal object MusicAlbumTag {
    @JvmStatic fun normalize(value: String?): String =
        value?.trim { it <= ' ' } ?: ""
    @JvmStatic fun present(album: String?): Boolean =
        album != null && album.trim { it <= ' ' }.isNotEmpty()
    @JvmStatic fun requiresLegacyVerification(cachedAlbum: String?): Boolean =
        cachedAlbum == "Unknown Album"
}
