package rique.notick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkActivityLevelStateTest {
    private static final long TICKS_PER_SECOND = ChunkActivityLevelState.TICKS_PER_SECOND;
    private static final long FORGET_AFTER_TICKS = 20L * 60L * 30L;

    @Test
    void recordsOncePerWholeSecond() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();
        long key = 42L;

        for (int tick = 0; tick < TICKS_PER_SECOND * 3; tick++) {
            state.recordChunk(key, tick);
        }

        assertEquals(3L, state.getSeconds(key));
    }

    @Test
    void deduplicatesSameTick() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();
        long key = 7L;

        state.recordChunk(key, 0L);
        state.recordChunk(key, 0L);
        state.recordChunk(key, 0L);

        assertEquals(1L, state.getSeconds(key));
    }

    @Test
    void ignoresNonSecondTicks() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();
        long key = 7L;

        for (int tick = 1; tick < TICKS_PER_SECOND; tick++) {
            state.recordChunk(key, tick);
        }

        assertEquals(0L, state.getSeconds(key));
    }

    @Test
    void forgetsChunksAfterExpiry() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();
        long key = 11L;

        state.recordChunk(key, 0L);
        assertEquals(1L, state.getSeconds(key));

        state.observeTime(FORGET_AFTER_TICKS + 400L);

        assertEquals(0L, state.getSeconds(key));
    }

    @Test
    void keepsRecentlySeenChunks() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();
        long key = 11L;

        state.recordChunk(key, 0L);
        state.observeTime(FORGET_AFTER_TICKS);

        assertEquals(1L, state.getSeconds(key));
    }

    @Test
    void resetsWhenTimeMovesBackward() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();
        long key = 3L;

        state.recordChunk(key, TICKS_PER_SECOND * 100L);
        assertEquals(1L, state.getSeconds(key));

        state.observeTime(TICKS_PER_SECOND * 50L);

        assertEquals(0L, state.getSeconds(key));
    }

    @Test
    void differentChunksTrackedIndependently() {
        ChunkActivityLevelState state = new ChunkActivityLevelState();

        state.recordChunk(1L, 0L);
        state.recordChunk(2L, 0L);
        state.recordChunk(1L, TICKS_PER_SECOND);

        assertEquals(2L, state.getSeconds(1L));
        assertEquals(1L, state.getSeconds(2L));
        assertEquals(0L, state.getSeconds(3L));
    }
}
