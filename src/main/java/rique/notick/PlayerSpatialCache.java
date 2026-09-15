package rique.notick;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

final class PlayerSpatialCache {
    private static final long BUCKET_CLEANUP_INTERVAL_TICKS = 20L * 60L;

    private long gameTime = Long.MIN_VALUE;
    private long lastBucketCleanupTime = Long.MIN_VALUE;
    private int chunkRadius = -1;
    private int playerCount;
    private final ObjectArrayList<PlayerSnapshot> players = new ObjectArrayList<>();
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<PlayerBucket> playersByChunk = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    boolean beginRefresh(long now, int horizontalDistanceBlocks) {
        int nextChunkRadius = horizontalDistanceBlocks > 256
                ? 17
                : (horizontalDistanceBlocks + 15) >> 4;
        if (now == gameTime && nextChunkRadius == chunkRadius) return false;

        if (now < gameTime) {
            playersByChunk.clear();
            lastBucketCleanupTime = Long.MIN_VALUE;
        }
        gameTime = now;
        chunkRadius = nextChunkRadius;
        playerCount = 0;
        return true;
    }

    void addPlayer(double x, double y, double z) {
        PlayerSnapshot snapshot;
        if (playerCount < players.size()) {
            snapshot = players.get(playerCount);
        } else {
            snapshot = new PlayerSnapshot();
            players.add(snapshot);
        }
        snapshot.update(x, y, z);
        playerCount++;
    }

    void finishRefresh() {
        if (playerCount <= 4 || chunkRadius > 16) {
            cleanupBuckets(gameTime);
            return;
        }

        for (int index = 0; index < playerCount; index++) {
            PlayerSnapshot player = players.get(index);
            long key = packChunkPos(player.chunkX, player.chunkZ);
            PlayerBucket bucket = playersByChunk.get(key);
            if (bucket == null) {
                bucket = new PlayerBucket();
                playersByChunk.put(key, bucket);
            }
            bucket.add(gameTime, player);
        }
        cleanupBuckets(gameTime);
    }

    boolean isNear(int posX, int posY, int posZ, int maxHeight, long maxDistSquared) {
        if (playerCount == 0) return false;

        if (playerCount <= 4 || chunkRadius > 16) {
            for (int index = 0; index < playerCount; index++) {
                if (isNearPlayer(players.get(index), posX, posY, posZ, maxHeight, maxDistSquared)) return true;
            }
            return false;
        }

        int chunkX = posX >> 4;
        int chunkZ = posZ >> 4;
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                long key = packChunkPos(chunkX + x, chunkZ + z);
                PlayerBucket bucket = playersByChunk.get(key);
                if (bucket == null || bucket.gameTime != gameTime) continue;
                for (PlayerSnapshot player : bucket.players) {
                    if (isNearPlayer(player, posX, posY, posZ, maxHeight, maxDistSquared)) return true;
                }
            }
        }
        return false;
    }

    private void cleanupBuckets(long now) {
        if (lastBucketCleanupTime != Long.MIN_VALUE
                && now - lastBucketCleanupTime < BUCKET_CLEANUP_INTERVAL_TICKS) {
            return;
        }

        var iterator = playersByChunk.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            PlayerBucket bucket = iterator.next().getValue();
            if (bucket.gameTime != now) {
                iterator.remove();
            }
        }
        lastBucketCleanupTime = now;
    }

    private static boolean isNearPlayer(PlayerSnapshot player, int posX, int posY, int posZ, int maxHeight, long maxDistSquared) {
        if (Math.abs(player.y - posY) > maxHeight) return false;
        double x = player.x - posX;
        double z = player.z - posZ;
        return (x * x + z * z) <= maxDistSquared;
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    private static final class PlayerSnapshot {
        private double x;
        private double y;
        private double z;
        private int chunkX;
        private int chunkZ;

        private void update(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            chunkX = ((int) Math.floor(x)) >> 4;
            chunkZ = ((int) Math.floor(z)) >> 4;
        }
    }

    private static final class PlayerBucket {
        private long gameTime = Long.MIN_VALUE;
        private final ObjectArrayList<PlayerSnapshot> players = new ObjectArrayList<>();

        private void add(long now, PlayerSnapshot player) {
            if (gameTime != now) {
                gameTime = now;
                players.clear();
            }
            players.add(player);
        }
    }
}
