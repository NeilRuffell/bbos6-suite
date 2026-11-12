package dev.q25.bbos6.ui.compose

/** Pure Kotlin copy of the shared context-menu DPAD policy, without UI or app logic. */
internal data class Bbos6GridCell(val row: Int, val column: Int, val enabled: Boolean)
internal enum class Bbos6GridDirection { LEFT, RIGHT, UP, DOWN }

internal object Bbos6GridNavigation {
    fun firstEnabled(cells: List<Bbos6GridCell>) = cells.indexOfFirst { it.enabled }

    fun move(cells: List<Bbos6GridCell>, selected: Int, direction: Bbos6GridDirection): Int {
        if (selected !in cells.indices || !cells[selected].enabled) return firstEnabled(cells)
        val current = cells[selected]
        if (direction == Bbos6GridDirection.LEFT || direction == Bbos6GridDirection.RIGHT) {
            val right = direction == Bbos6GridDirection.RIGHT
            var best = selected
            var distance = Int.MAX_VALUE
            cells.forEachIndexed { index, cell ->
                if (cell.enabled && cell.row == current.row) {
                    val delta = cell.column - current.column
                    if ((right && delta > 0) || (!right && delta < 0)) {
                        val separation = kotlin.math.abs(delta)
                        if (separation < distance) { best = index; distance = separation }
                    }
                }
            }
            return best
        }
        val down = direction == Bbos6GridDirection.DOWN
        var nextRow = current.row + if (down) 1 else -1
        val lastRow = cells.maxOfOrNull { it.row } ?: -1
        while (nextRow in 0..lastRow) {
            var best = -1
            var distance = Int.MAX_VALUE
            cells.forEachIndexed { index, cell ->
                if (cell.enabled && cell.row == nextRow) {
                    val separation = kotlin.math.abs(cell.column - current.column)
                    if (separation < distance ||
                        (separation == distance && best >= 0 && cell.column < cells[best].column)) {
                        best = index; distance = separation
                    }
                }
            }
            if (best >= 0) return best
            nextRow += if (down) 1 else -1
        }
        return selected
    }
}
