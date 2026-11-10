package dev.q25.bbos6.ui.compose

/** Reusable popup actions have no application-specific behavior. */
data class Bbos6PopupAction(val id: String, val label: String, val enabled: Boolean = true)

internal object Bbos6PopupNavigation {
    fun initial(actions: List<Bbos6PopupAction>, initialId: String?): Int {
        val requested = actions.indexOfFirst { it.id == initialId && it.enabled }
        return if (requested >= 0) requested else actions.indexOfFirst { it.enabled }
    }

    fun move(actions: List<Bbos6PopupAction>, selected: Int, step: Int): Int {
        if (selected !in actions.indices) return actions.indexOfFirst { it.enabled }
        var index = selected + step
        while (index in actions.indices) {
            if (actions[index].enabled) return index
            index += step
        }
        return selected
    }

    /** First unused ASCII letter in each enabled action label, matching the native helper. */
    fun shortcuts(actions: List<Bbos6PopupAction>): Map<Char, Pair<Int, Int>> {
        val shortcuts = linkedMapOf<Char, Pair<Int, Int>>()
        actions.forEachIndexed { index, action ->
            if (!action.enabled) return@forEachIndexed
            val offset = action.label.indexOfFirst { c ->
                val key = c.uppercaseChar()
                key in 'A'..'Z' && key !in shortcuts
            }
            if (offset >= 0) shortcuts[action.label[offset].uppercaseChar()] = index to offset
        }
        return shortcuts
    }
}
