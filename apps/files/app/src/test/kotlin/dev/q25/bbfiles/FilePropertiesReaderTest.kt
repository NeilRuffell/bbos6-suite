package dev.q25.bbfiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePropertiesReaderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun fileMetadataReadsOnlyKnownFilesystemFacts() {
        val root = temp.newFolder("root")
        val file = File(root, "Sample.TXT").apply { writeText("hello") }

        val metadata = FilePropertiesReader.read(root, file, file.name)
        assertNotNull(metadata)
        metadata!!
        assertEquals("Sample.TXT", metadata.name)
        assertEquals(root.canonicalPath, metadata.location)
        assertFalse(metadata.isDirectory)
        assertEquals("txt", metadata.extension)
        assertEquals(5L, metadata.sizeBytes)
        assertTrue(metadata.modifiedMillis != null && metadata.modifiedMillis!! > 0)
    }

    @Test fun folderMetadataDoesNotInventFolderSize() {
        val root = temp.newFolder("root")
        val folder = File(root, "Folder").apply { mkdir() }
        File(folder, "child.txt").writeText("1234")

        val metadata = FilePropertiesReader.read(root, folder, folder.name)
        assertNotNull(metadata)
        metadata!!
        assertEquals("Folder", metadata.name)
        assertEquals(root.canonicalPath, metadata.location)
        assertTrue(metadata.isDirectory)
        assertNull(metadata.extension)
        assertNull(metadata.sizeBytes)
    }

    @Test fun missingAndOutsideVolumeFilesAreRejected() {
        val root = temp.newFolder("root")
        val other = temp.newFolder("other")
        val outside = File(other, "other.txt").apply { writeText("secret") }

        assertNull(FilePropertiesReader.read(root, File(root, "absent"), "absent"))
        assertNull(FilePropertiesReader.read(root, outside, outside.name))
    }
}
