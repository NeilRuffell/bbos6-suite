package dev.q25.bbfiles

import java.io.File

/**
 * Files-owned directory navigation anchor. After Back, highlight the child
 * directory we just left in its parent list. The shared page list owns
 * selection and viewport restoration; only Files knows directory relations.
 */
internal data class DirectoryReturnSelection(
    val parentPath: String,
    val childPath: String
) {
    fun selectedIdFor(directory: File): String? =
        childPath.takeIf { directory.path == parentPath }
}
