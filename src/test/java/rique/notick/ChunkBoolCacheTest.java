package rique.notick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkBoolCacheTest {

    @Test
    void unknownByDefault() {
        ChunkBoolCache cache = new ChunkBoolCache();
        assertEquals(ChunkBoolCache.UNKNOWN, cache.get(0L));
        assertEquals(ChunkBoolCache.UNKNOWN, cache.get(123456789L));
        assertEquals(ChunkBoolCache.UNKNOWN, cache.get(-987654321L));
    }

    @Test
    void storesTrueAndFalse() {
        ChunkBoolCache cache = new ChunkBoolCache();
        cache.put(5L, true);
        cache.put(9L, false);

        assertEquals(ChunkBoolCache.TRUE, cache.get(5L));
        assertEquals(ChunkBoolCache.FALSE, cache.get(9L));
        assertEquals(ChunkBoolCache.UNKNOWN, cache.get(7L));
    }

    @Test
    void clearsWhenGameTimeChanges() {
        ChunkBoolCache cache = new ChunkBoolCache();
        cache.ensureGameTime(100L);
        cache.put(5L, true);

        cache.ensureGameTime(100L);
        assertEquals(ChunkBoolCache.TRUE, cache.get(5L));

        cache.ensureGameTime(101L);
        assertEquals(ChunkBoolCache.UNKNOWN, cache.get(5L));
    }

    @Test
    void clearsWhenGameTimeMovesBackward() {
        ChunkBoolCache cache = new ChunkBoolCache();
        cache.ensureGameTime(100L);
        cache.put(5L, true);

        cache.ensureGameTime(50L);
        assertEquals(ChunkBoolCache.UNKNOWN, cache.get(5L));
    }
}
