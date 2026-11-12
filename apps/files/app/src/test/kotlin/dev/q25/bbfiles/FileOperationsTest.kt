package dev.q25.bbfiles

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun createRenameAndDeleteFolder() {
        val root = temp.newFolder("root")
        assertTrue(FileOperations.newFolder(root, root, "New folder") is FileOperations.Result.Success)
        val old = File(root, "New folder")
        assertTrue(FileOperations.rename(root, old, "Renamed") is FileOperations.Result.Success)
        assertTrue(File(root, "Renamed").exists())
        assertTrue(FileOperations.delete(root, File(root, "Renamed")) is FileOperations.Result.Success)
        assertFalse(File(root, "Renamed").exists())
    }

    @Test fun invalidNameAndCollisionsNeverOverwrite() {
        val root = temp.newFolder("root")
        File(root, "existing").writeText("original")
        assertFalse(FileOperations.validName("../bad"))
        assertFalse(FileOperations.validName(" name "))
        assertTrue(FileOperations.newFolder(root, root, "../bad") is FileOperations.Result.Failure)
        assertTrue(FileOperations.rename(root, File(root, "existing"), "bad/name") is FileOperations.Result.Failure)
        assertTrue(FileOperations.newFolder(root, root, "existing") is FileOperations.Result.Failure)
        assertEquals("original", File(root, "existing").readText())
    }

    @Test fun recursiveCopyAndMove() {
        val root = temp.newFolder("root")
        val source = File(root, "Folder").also { it.mkdir() }
        File(source, "child.txt").writeText("hello")
        val targetFolder = File(root, "dest").also { it.mkdir() }
        val copy = FileOperations.paste(root, source, root, targetFolder, move = false)
        assertTrue(copy is FileOperations.Result.Success)
        assertEquals("hello", File(targetFolder, "Folder/child.txt").readText())
        assertTrue(source.exists())
        val otherRoot = temp.newFolder("other")
        val move = FileOperations.paste(root, source, otherRoot, otherRoot, move = true)
        assertTrue(move is FileOperations.Result.Success)
        assertFalse(source.exists())
        assertEquals("hello", File(otherRoot, "Folder/child.txt").readText())
    }

    @Test fun preventsSelfNestingAndVolumeRootDeletion() {
        val root = temp.newFolder("root")
        val folder = File(root, "folder").also { it.mkdir() }
        val child = File(folder, "child").also { it.mkdir() }
        assertTrue(FileOperations.paste(root, folder, root, child, false) is FileOperations.Result.Failure)
        assertTrue(FileOperations.delete(root, root) is FileOperations.Result.Failure)
        assertTrue(FileOperations.rename(root, root, "changed") is FileOperations.Result.Failure)
    }

    @Test fun protectsTargetFromExistingContent() {
        val root = temp.newFolder("root")
        val source = File(root, "source.txt").apply { writeText("new") }
        val target = File(root, "target").also { it.mkdir() }
        File(target, "source.txt").writeText("old")
        assertTrue(FileOperations.paste(root, source, root, target, false) is FileOperations.Result.Failure)
        assertEquals("old", File(target, "source.txt").readText())
    }
}
