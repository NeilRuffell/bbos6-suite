package dev.q25.bbfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoritePolicyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun storesFolderAndFileWithoutDuplicating() {
        val root = folder.newFolder("storage")
        val dir = File(root, "Music").apply { mkdir() }
        val song = File(dir, "sample.mp3").apply { writeText("audio") }
        val first = requireNotNull(FavoritePolicy.create("internal", root, dir))
        val second = requireNotNull(FavoritePolicy.create("internal", root, song))
        val entries = FavoritePolicy.add(FavoritePolicy.add(listOf(first), first), second)
        assertEquals(2, entries.size)
        assertEquals(listOf(first, second), entries)
        assertTrue(FavoritePolicy.contains(entries, first))
        assertEquals(listOf(second), FavoritePolicy.remove(entries, first))
        assertEquals("Music", first.name)
        assertTrue(first.isDirectory)
        assertFalse(second.isDirectory)
    }

    @Test fun resolvesOnlyInsideTheRecordedStorageRoot() {
        val root = folder.newFolder("primary")
        val other = folder.newFolder("other")
        val item = File(root, "document.txt").apply { writeText("content") }
        val favorite = requireNotNull(FavoritePolicy.create("internal", root, item))
        assertEquals(item.canonicalFile, FavoritePolicy.resolve(root, favorite))
        assertNull(FavoritePolicy.resolve(other, favorite))
        assertNull(FavoritePolicy.create("internal", root, File(other, "unrelated")))
        assertNull(FavoritePolicy.create("internal", root, root))
        item.delete()
        assertNull(FavoritePolicy.resolve(root, favorite))
    }

    @Test fun favoritedDirectoryMustStillBeDirectory() {
        val root = folder.newFolder("primary")
        val dir = File(root, "Playlist").apply { mkdir() }
        val bookmark = requireNotNull(FavoritePolicy.create("internal", root, dir))
        dir.delete()
        File(root, "Playlist").writeText("replaced")
        assertNull(FavoritePolicy.resolve(root, bookmark))
    }
}
