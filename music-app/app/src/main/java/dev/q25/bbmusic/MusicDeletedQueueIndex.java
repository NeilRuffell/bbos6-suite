package dev.q25.bbmusic;

/** Pure-Java index policy shared by active and retained playback queues. */
final class MusicDeletedQueueIndex {
    private MusicDeletedQueueIndex() {}

    /**
     * Surviving current item keeps its index after filtering. If it was
     * removed, prefer the next survivor, or the last prior one if needed.
     * Returns -1 when all items were removed.
     */
    static int afterFiltering(int current, boolean[] kept) {
        if (kept == null || current < 0 || current >= kept.length) {
            throw new IllegalArgumentException("Invalid queue selection");
        }
        int before = 0;
        int remaining = 0;
        for (int i = 0; i < kept.length; i++) {
            if (!kept[i]) continue;
            if (i < current) before++;
            remaining++;
        }
        if (remaining == 0) return -1;
        return kept[current] ? before : Math.min(before, remaining - 1);
    }
}
