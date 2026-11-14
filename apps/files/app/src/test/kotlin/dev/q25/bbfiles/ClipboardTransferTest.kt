package dev.q25.bbfiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ClipboardTransferTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun successfulCopyHasNoPendingItems() {
        val root = temp.newFolder("root")
        val source = File(root, "one.txt").apply { writeText("one") }
        val target = File(root, "target").apply { mkdir() }

        val outcome = ClipboardTransfer.paste(root, listOf(source), root, target, move = false)

        assertTrue(outcome.operation is FileOperations.Result.Success)
        assertTrue(outcome.remainingSources.isEmpty())
        assertTrue(source.exists())
        assertEquals("one", File(target, "one.txt").readText())
    }

    @Test fun successfulCutHasNoPendingItems() {
        val root = temp.newFolder("root")
        val source = File(root, "one.txt").apply { writeText("one") }
        val target = File(root, "target").apply { mkdir() }

        val outcome = ClipboardTransfer.paste(root, listOf(source), root, target, move = true)

        assertTrue(outcome.operation is FileOperations.Result.Success)
        assertTrue(outcome.remainingSources.isEmpty())
        assertFalse(source.exists())
        assertEquals("one", File(target, "one.txt").readText())
    }

    @Test fun partialTransferRetriesOnlyFailedItemsWithoutOverwriting() {
        val root = temp.newFolder("root")
        val first = File(root, "first.txt").apply { writeText("one") }
        val second = File(root, "second.txt").apply { writeText("two") }
        val target = File(root, "target").apply { mkdir() }
        val existing = File(target, "second.txt").apply { writeText("keep") }

        val firstPass = ClipboardTransfer.paste(
            root, listOf(first, second), root, target, move = false
        )

        assertTrue(firstPass.operation is FileOperations.Result.Failure)
        assertEquals(listOf(second), firstPass.remainingSources)
        assertEquals("one", File(target, "first.txt").readText())
        assertEquals("keep", existing.readText())

        assertTrue(existing.delete())
        val retry = ClipboardTransfer.paste(
            root, firstPass.remainingSources, root, target, move = false
        )
        assertTrue(retry.operation is FileOperations.Result.Success)
        assertTrue(retry.remainingSources.isEmpty())
        assertEquals("two", File(target, "second.txt").readText())
    }
}
