package dev.q25.bbfiles

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesDocumentIdsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun rootAndNestedDocumentsRoundTrip() {
        val root = temp.newFolder("storage")
        val folder = File(root, "Photos 2026").also { it.mkdir() }
        val file = File(folder, "photo #1.jpg").also { it.writeText("image") }

        assertEquals("primary:", FilesDocumentIds.forFile("primary", root, root))
        assertEquals(root.canonicalFile, FilesDocumentIds.resolve("primary", root, "primary:"))
        assertEquals("primary:Photos 2026/photo #1.jpg",
            FilesDocumentIds.forFile("primary", root, file))
        assertEquals(file.canonicalFile, FilesDocumentIds.resolve(
            "primary", root, "primary:Photos 2026/photo #1.jpg"))
    }

    @Test fun documentIdsAreScopedToOneVolume() {
        val primary = temp.newFolder("primary")
        val sd = temp.newFolder("sd")
        val sdFile = File(sd, "file.txt").also { it.writeText("sd") }

        assertNull(FilesDocumentIds.forFile("primary", primary, sdFile))
        assertNull(FilesDocumentIds.resolve("primary", primary, "sd-123:file.txt"))
        assertNull(FilesDocumentIds.resolve("sd-123", sd, "primary:file.txt"))
        assertEquals(sdFile.canonicalFile, FilesDocumentIds.resolve(
            "sd-123", sd, "sd-123:file.txt"))
    }

    @Test fun rejectsTraversalAndMalformedIdentifiers() {
        val root = temp.newFolder("root")
        val invalid = listOf(
            "primary:../outside", "primary:folder/../../outside", "primary:/absolute",
            "primary:folder//file", "primary:folder/./file", "primary:folder/",
            "primary:folder\\file", "primary:folder/\u0000file", "primary"
        )
        assertTrue(invalid.all { FilesDocumentIds.resolve("primary", root, it) == null })
        assertNull(FilesDocumentIds.forFile("invalid:root", root, root))
    }

    @Test fun rejectsSymbolicLinksEscapingAuthorizedRoot() {
        val root = temp.newFolder("root")
        val outside = temp.newFolder("outside")
        val outsideFile = File(outside, "private.txt").also { it.writeText("secret") }
        val link = File(root, "shortcut.txt")
        Files.createSymbolicLink(link.toPath(), outsideFile.toPath())

        assertNull(FilesDocumentIds.resolve("primary", root, "primary:shortcut.txt"))
        assertNull(FilesDocumentIds.forFile("primary", root, link))
    }
}
