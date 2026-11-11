package dev.q25.bbmusic

/** Queue index recovery after an explicit deletion; no playback ownership. */
internal object MusicDeletedQueueIndex {
    @JvmStatic fun afterFiltering(current: Int, kept: BooleanArray?): Int {
        require(kept != null && current >= 0 && current < kept.size) {
            "Invalid queue selection"
        }
        var before = 0
        var remaining = 0
        for (i in kept.indices) {
            if (!kept[i]) continue
            if (i < current) before++
            remaining++
        }
        if (remaining == 0) return -1
        return if (kept[current]) before else minOf(before, remaining - 1)
    }
}
