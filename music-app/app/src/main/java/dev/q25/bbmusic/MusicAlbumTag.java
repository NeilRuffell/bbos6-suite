package dev.q25.bbmusic;

/**
 * Album tags are optional. An absent/blank tag is stored as an empty string,
 * not a synthetic "Unknown Album" label. A literal tagged value of
 * "Unknown Album" is ordinary album metadata and must remain browseable.
 */
final class MusicAlbumTag {
    private MusicAlbumTag() {}

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean present(String album) {
        return album != null && !album.trim().isEmpty();
    }

    /**
     * Only legacy indexes synthesized this exact text for missing tags.
     * Re-read these ambiguous entries once when migrating the catalog.
     */
    static boolean requiresLegacyVerification(String cachedAlbum) {
        return "Unknown Album".equals(cachedAlbum);
    }
}
