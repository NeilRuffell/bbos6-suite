package dev.q25.bbfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexedMediaCategoryTest {
    @Test fun categoryKeysAreStableAndDistinct() {
        assertEquals(
            listOf("images", "audio", "videos", "documents"),
            IndexedMediaCategory.entries.map { it.id }
        )
        assertEquals(4, IndexedMediaCategory.entries.map { it.id }.toSet().size)
    }

    @Test fun lookupUsesExactCategoryIds() {
        assertEquals(IndexedMediaCategory.IMAGES, IndexedMediaCategory.fromId("images"))
        assertEquals(IndexedMediaCategory.VIDEOS, IndexedMediaCategory.fromId("videos"))
        assertEquals(IndexedMediaCategory.DOCUMENTS, IndexedMediaCategory.fromId("documents"))
        assertNull(IndexedMediaCategory.fromId("downloads"))
        assertNull(IndexedMediaCategory.fromId("Documents"))
    }

    @Test fun documentMimePolicyIncludesCommonDocumentTypes() {
        val documentTypes = listOf(
            "application/pdf",
            "text/plain",
            "text/csv",
            "text/html",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "application/epub+zip",
            "application/json",
            "application/xml",
            "APPLICATION/PDF"
        )
        documentTypes.forEach { mime ->
            assertTrue("Expected document MIME: $mime", DocumentMimePolicy.matches(mime))
        }
    }

    @Test fun documentMimePolicyExcludesOtherFiles() {
        val otherTypes = listOf(
            "image/jpeg",
            "audio/mpeg",
            "video/mp4",
            "application/zip",
            "application/vnd.android.package-archive",
            "application/octet-stream",
            "application/x-msdownload",
            ""
        )
        otherTypes.forEach { mime ->
            assertFalse("Unexpected document MIME: $mime", DocumentMimePolicy.matches(mime))
        }
        assertFalse(DocumentMimePolicy.matches(null))
    }

    @Test fun sqlSelectionParametersMatchPolicy() {
        val selection = DocumentMimePolicy.selection("mime_type")
        val args = DocumentMimePolicy.selectionArgs()
        assertTrue(selection.startsWith("(") && selection.endsWith(")"))
        assertEquals(args.size, selection.count { it == '?' })
        assertTrue(args.contains("text/%"))
        assertTrue(args.contains("application/pdf"))
    }
}
