package dev.q25.bbfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class FileBrowserTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun listsFoldersBeforeFilesAlphabetically() {
        val root = temp.newFolder("root")
        File(root, "Zeta.txt").writeText("z")
        File(root, "apple.txt").writeText("a")
        File(root, "Folder Z").mkdir()
        File(root, "Folder A").mkdir()
        val result = FileBrowser.list(root, root) as FileBrowser.Listing.Available
        assertEquals(
            listOf("Folder A", "Folder Z", "apple.txt", "Zeta.txt"),
            result.entries.map { it.name }
        )
    }

    @Test fun guardsRootAndParent() {
        val root = temp.newFolder("root")
        val inner = File(root, "inner").apply { mkdir() }
        val outside = temp.newFolder("outside")
        assertTrue(FileBrowser.isWithin(root, inner))
        assertFalse(FileBrowser.isWithin(root, outside))
        assertEquals(root.canonicalFile, FileBrowser.parentWithin(root, inner))
        assertEquals(null, FileBrowser.parentWithin(root, root))
        assertTrue(FileBrowser.list(root, outside) is FileBrowser.Listing.Unavailable)
    }

    @Test fun returnsEmptyFolderWithoutInventingRows() {
        val root = temp.newFolder("empty")
        val result = FileBrowser.list(root, root) as FileBrowser.Listing.Available
        assertTrue(result.entries.isEmpty())
        assertEquals("Internal Storage", FileBrowser.relativeTitle(root, root, "Internal Storage"))
    }
}
