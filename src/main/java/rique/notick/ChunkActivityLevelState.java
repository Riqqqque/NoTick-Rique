package rique.notick;

final class ChunkActivityLevelState {
    static final long TICKS_PER_SECOND = 20L;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 10L;
    private static final long FORGET_AFTER_TICKS = 20L * 60L * 30L;

    private long lastObservedTick = Long.MIN_VALUE;
    private long lastCleanupTick = Long.MIN_VALUE;
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap secondsByChunk = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap lastSeenTickByChunk = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

    ChunkActivityLevelState() {
        secondsByChunk.defaultReturnValue(0);
        lastSeenTickByChunk.defaultReturnValue(Long.MIN_VALUE);
    }

    void observeTime(long now) {
        if (now < lastObservedTick) {
            secondsByChunk.clear();
            lastSeenTickByChunk.clear();
            lastCleanupTick = Long.MIN_VALUE;
        }
        lastObservedTick = now;

        if (lastCleanupTick == Long.MIN_VALUE || now - lastCleanupTick >= CLEANUP_INTERVAL_TICKS) {
            cleanup(now);
            lastCleanupTick = now;
        }
    }

    void recordChunk(long chunkKey, long now) {
        observeTime(now);
        if (now % TICKS_PER_SECOND != 0L) return;

        if (lastSeenTickByChunk.get(chunkKey) == now) return;

        int seconds = secondsByChunk.get(chunkKey);
        if (seconds < Integer.MAX_VALUE) {
            secondsByChunk.put(chunkKey, seconds + 1);
        }
        lastSeenTickByChunk.put(chunkKey, now);
    }

    long getSeconds(long chunkKey) {
        return secondsByChunk.get(chunkKey);
    }

    private void cleanup(long now) {
        var iterator = lastSeenTickByChunk.long2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getLongValue() > FORGET_AFTER_TICKS) {
                long key = entry.getLongKey();
                iterator.remove();
                secondsByChunk.remove(key);
            }
        }
    }
}
