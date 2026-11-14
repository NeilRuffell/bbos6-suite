package dev.q25.bbfiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DirectoryReturnSelectionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun returningToParentHighlightsChildFolder() {
        val parent = temp.newFolder("parent")
        val child = File(parent, "child").apply { mkdir() }
        val target = DirectoryReturnSelection(parent.path, child.path)

        assertEquals(child.path, target.selectedIdFor(parent))
    }

    @Test fun unrelatedDirectoryDoesNotReuseStaleSelection() {
        val parent = temp.newFolder("parent")
        val other = temp.newFolder("other")
        val child = File(parent, "child").apply { mkdir() }
        val target = DirectoryReturnSelection(parent.path, child.path)

        assertNull(target.selectedIdFor(other))
    }

    @Test fun nestedBackUpdatesSelectionToJustExitedDirectory() {
        val root = temp.newFolder("root")
        val parent = File(root, "parent").apply { mkdir() }
        val child = File(parent, "child").apply { mkdir() }

        val firstBack = DirectoryReturnSelection(parent.path, child.path)
        val secondBack = DirectoryReturnSelection(root.path, parent.path)
        assertEquals(child.path, firstBack.selectedIdFor(parent))
        assertEquals(parent.path, secondBack.selectedIdFor(root))
        assertNull(secondBack.selectedIdFor(parent))
    }
}
