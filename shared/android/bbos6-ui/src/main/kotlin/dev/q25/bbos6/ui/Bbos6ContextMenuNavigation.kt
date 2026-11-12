package dev.q25.bbos6.ui

/**
 * Deterministic Q25 context-grid DPAD/trackpad movement. No Android
 * dependencies and no edge wrapping. Retains Java test/caller API.
 */
internal object Bbos6ContextMenuNavigation {
    enum class Direction { LEFT, RIGHT, UP, DOWN }
    class Cell(
        @JvmField val row: Int,
        @JvmField val column: Int,
        @JvmField val enabled: Boolean
    )

    @JvmStatic
    fun firstEnabled(cells: List<Cell>): Int = cells.indexOfFirst { it.enabled }

    @JvmStatic
    fun move(cells: List<Cell>, selected: Int, direction: Direction): Int {
        val current = cells.getOrNull(selected)
        if (current?.enabled != true) return firstEnabled(cells)
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            val right = direction == Direction.RIGHT
            var best = selected
            var distance = Int.MAX_VALUE
            for ((index, candidate) in cells.withIndex()) {
                if (!candidate.enabled || candidate.row != current.row) continue
                val delta = candidate.column - current.column
                if ((right && delta <= 0) || (!right && delta >= 0)) continue
                val separation = kotlin.math.abs(delta)
                if (separation < distance) {
                    best = index
                    distance = separation
                }
            }
            return best
        }

        val down = direction == Direction.DOWN
        var nextRow = current.row + if (down) 1 else -1
        val lastRow = cells.maxOfOrNull { it.row } ?: -1
        while (nextRow in 0..lastRow) {
            var best = -1
            var distance = Int.MAX_VALUE
            for ((index, candidate) in cells.withIndex()) {
                if (!candidate.enabled || candidate.row != nextRow) continue
                val separation = kotlin.math.abs(candidate.column - current.column)
                if (separation < distance ||
                    (separation == distance && best >= 0 &&
                        candidate.column < cells[best].column)
                ) {
                    best = index
                    distance = separation
                }
            }
            if (best >= 0) return best
            nextRow += if (down) 1 else -1
        }
        return selected
    }
}
