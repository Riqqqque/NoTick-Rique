package rique.notick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSpatialCacheTest {

    private static PlayerSpatialCache refreshed(long gameTime, int horizontalDistance, double... xyz) {
        PlayerSpatialCache cache = new PlayerSpatialCache();
        assertTrue(cache.beginRefresh(gameTime, horizontalDistance));
        for (int i = 0; i < xyz.length; i += 3) {
            cache.addPlayer(xyz[i], xyz[i + 1], xyz[i + 2]);
        }
        cache.finishRefresh();
        return cache;
    }

    @Test
    void emptyCacheIsNeverNear() {
        PlayerSpatialCache cache = new PlayerSpatialCache();
        assertFalse(cache.isNear(0, 64, 0, 32, 64L * 64L));
    }

    @Test
    void emptyPlayerListIsNeverNear() {
        PlayerSpatialCache cache = refreshed(0L, 64);
        assertFalse(cache.isNear(0, 64, 0, 32, 64L * 64L));
    }

    @Test
    void linearPathNearAndFar() {
        PlayerSpatialCache cache = refreshed(0L, 64, 0, 64, 0);
        long distSq = 64L * 64L;

        assertTrue(cache.isNear(10, 64, 0, 32, distSq));
        assertTrue(cache.isNear(64, 64, 0, 32, distSq));
        assertFalse(cache.isNear(65, 64, 0, 32, distSq));
        assertFalse(cache.isNear(500, 64, 500, 32, distSq));
    }

    @Test
    void verticalDistanceCutoff() {
        PlayerSpatialCache cache = refreshed(0L, 64, 0, 64, 0);
        long distSq = 64L * 64L;

        assertTrue(cache.isNear(0, 96, 0, 32, distSq));
        assertFalse(cache.isNear(0, 97, 0, 32, distSq));
        assertFalse(cache.isNear(0, 31, 0, 32, distSq));
        assertTrue(cache.isNear(0, 32, 0, 32, distSq));
    }

    @Test
    void bucketPathMatchesLinearPath() {
        double[] players = {
                0, 64, 0,
                500, 70, -300,
                -800, 40, 1200,
                1600, 64, 1600,
                -2000, 90, -50
        };
        PlayerSpatialCache cache = refreshed(0L, 64, players);
        long distSq = 64L * 64L;

        assertTrue(cache.isNear(30, 64, 30, 32, distSq));
        assertTrue(cache.isNear(540, 70, -300, 32, distSq));
        assertTrue(cache.isNear(-840, 40, 1200, 32, distSq));
        assertFalse(cache.isNear(100, 64, 100, 32, distSq));
        assertFalse(cache.isNear(500, 120, -300, 32, distSq));
        assertFalse(cache.isNear(0, 64, 2000, 32, distSq));
    }

    @Test
    void negativeCoordinates() {
        PlayerSpatialCache cache = refreshed(0L, 64, -1, 64, -1);
        long distSq = 64L * 64L;

        assertTrue(cache.isNear(-17, 64, -1, 32, distSq));
        assertTrue(cache.isNear(-1, 64, -65, 32, distSq));
        assertFalse(cache.isNear(100, 64, 100, 32, distSq));
    }

    @Test
    void sameTickRefreshIsSkipped() {
        PlayerSpatialCache cache = new PlayerSpatialCache();
        assertTrue(cache.beginRefresh(10L, 64));
        cache.addPlayer(0, 64, 0);
        cache.finishRefresh();

        assertFalse(cache.beginRefresh(10L, 64));

        assertTrue(cache.beginRefresh(11L, 64));
        cache.finishRefresh();
        assertFalse(cache.isNear(0, 64, 0, 32, 64L * 64L));
    }

    @Test
    void radiusChangeForcesRefresh() {
        PlayerSpatialCache cache = new PlayerSpatialCache();
        assertTrue(cache.beginRefresh(10L, 64));
        cache.addPlayer(0, 64, 0);
        cache.finishRefresh();

        assertTrue(cache.beginRefresh(10L, 128));
    }

    @Test
    void playerMovementReflectedNextTick() {
        PlayerSpatialCache cache = refreshed(0L, 64, 0, 64, 0);
        long distSq = 64L * 64L;
        assertTrue(cache.isNear(10, 64, 0, 32, distSq));
        assertFalse(cache.isNear(500, 64, 0, 32, distSq));

        assertTrue(cache.beginRefresh(1L, 64));
        cache.addPlayer(500, 64, 0);
        cache.finishRefresh();

        assertFalse(cache.isNear(10, 64, 0, 32, distSq));
        assertTrue(cache.isNear(500, 64, 0, 32, distSq));
    }

    @Test
    void largeRadiusUsesLinearPath() {
        PlayerSpatialCache cache = refreshed(0L, 512, 0, 64, 0);
        long distSq = 512L * 512L;

        assertTrue(cache.isNear(400, 64, 0, 32, distSq));
        assertFalse(cache.isNear(600, 64, 0, 32, distSq));
    }

    @Test
    void backwardTimeResetsBuckets() {
        PlayerSpatialCache cache = refreshed(100L, 64,
                0, 64, 0,
                500, 64, 500,
                -500, 64, -500,
                1000, 64, 0,
                0, 64, 1000);
        assertTrue(cache.isNear(10, 64, 0, 32, 64L * 64L));

        assertTrue(cache.beginRefresh(50L, 64));
        cache.addPlayer(9000, 64, 9000);
        cache.finishRefresh();

        assertFalse(cache.isNear(10, 64, 0, 32, 64L * 64L));
        assertTrue(cache.isNear(8990, 64, 9000, 32, 64L * 64L));
    }
}
