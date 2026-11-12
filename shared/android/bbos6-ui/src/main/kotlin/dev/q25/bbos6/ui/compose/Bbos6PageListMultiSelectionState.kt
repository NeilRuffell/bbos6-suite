package dev.q25.bbos6.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Opt-in multi-selection state for Bbos6PageList.
 *
 * Stable item IDs, not row positions, are retained. This controller has no
 * knowledge of file operations, media operations or application navigation.
 */
class Bbos6PageListMultiSelectionState {
    var isActive by mutableStateOf(false)
        private set

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val selectedCount: Int get() = selectedIds.size

    fun enter(initialId: String? = null) {
        isActive = true
        selectedIds = if (initialId == null) emptySet() else setOf(initialId)
    }

    fun toggle(id: String) {
        if (!isActive) return
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun selectAll(ids: Collection<String>) {
        if (!isActive) return
        selectedIds = ids.toSet()
    }

    fun deselectAll() {
        selectedIds = emptySet()
    }

    fun exit() {
        isActive = false
        deselectAll()
    }

    internal fun retainAvailable(ids: Collection<String>) {
        if (isActive) selectedIds = selectedIds.intersect(ids.toSet())
    }
}

@Composable
fun rememberBbos6PageListMultiSelectionState(): Bbos6PageListMultiSelectionState =
    remember { Bbos6PageListMultiSelectionState() }
