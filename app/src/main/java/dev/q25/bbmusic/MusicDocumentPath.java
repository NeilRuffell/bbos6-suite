package dev.q25.bbmusic;

/**
 * Formats only human-readable paths published by Android's External Storage
 * DocumentsProvider. Most SAF document IDs are opaque and MUST NOT be shown
 * as filesystem paths or transformed into invented /storage/... locations.
 */
final class MusicDocumentPath {
    static final String EXTERNAL_STORAGE_AUTHORITY =
            "com.android.externalstorage.documents";

    private MusicDocumentPath() {}

    /**
     * @return a logical storage location (not a POSIX absolute file path),
     *         or null when the provider's document ID cannot establish one.
     */
    static String readableLocation(String authority, String documentId,
            String displayName) {
        if (!EXTERNAL_STORAGE_AUTHORITY.equals(authority)
                || documentId == null || displayName == null
                || displayName.trim().isEmpty()) return null;
        int colon = documentId.indexOf(':');
        if (colon <= 0 || colon >= documentId.length() - 1) return null;
        String volume = documentId.substring(0, colon);
        String relativePath = documentId.substring(colon + 1);
        boolean primary = "primary".equalsIgnoreCase(volume);
        boolean removableVolume = volume.matches("(?i)[0-9a-f]{4}-[0-9a-f]{4}");
        if (!primary && !removableVolume) return null;
        if (relativePath.startsWith("/") || relativePath.contains("\\")
                || relativePath.contains("//") || relativePath.contains("../")
                || relativePath.equals("..")) return null;

        int lastSeparator = relativePath.lastIndexOf('/');
        String actualName = relativePath.substring(lastSeparator + 1);
        if (!actualName.equalsIgnoreCase(displayName)) return null;

        return (primary ? "Internal storage" : volume) + "/" + relativePath;
    }
}
