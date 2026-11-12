package dev.q25.bbfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexedMediaCategoryTest {
    @Test fun categoryKeysAreStableAndDistinct() {
        assertEquals(
            listOf("images", "audio", "videos"),
            IndexedMediaCategory.entries.map { it.id }
        )
        assertEquals(3, IndexedMediaCategory.entries.map { it.id }.toSet().size)
    }

    @Test fun lookupUsesExactCategoryIds() {
        assertEquals(
            IndexedMediaCategory.IMAGES,
            IndexedMediaCategory.fromId("images")
        )
        assertEquals(
            IndexedMediaCategory.VIDEOS,
            IndexedMediaCategory.fromId("videos")
        )
        assertNull(IndexedMediaCategory.fromId("documents"))
        assertNull(IndexedMediaCategory.fromId("Images"))
    }
}
