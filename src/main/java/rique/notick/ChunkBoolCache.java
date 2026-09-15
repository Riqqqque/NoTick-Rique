package rique.notick;

final class ChunkBoolCache {
    static final byte UNKNOWN = -1;
    static final byte FALSE = 0;
    static final byte TRUE = 1;

    private long gameTime = Long.MIN_VALUE;
    private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap cache = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();

    ChunkBoolCache() {
        cache.defaultReturnValue(UNKNOWN);
    }

    void ensureGameTime(long now) {
        if (gameTime != now) {
            gameTime = now;
            cache.clear();
        }
    }

    byte get(long key) {
        return cache.get(key);
    }

    void put(long key, boolean value) {
        cache.put(key, value ? TRUE : FALSE);
    }
}
