package rique.notick.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

public final class OPACChunkClaimProvider implements IChunkClaimProvider {
    private static final String SERVER_API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile Method getServerApiMethod;
    private static volatile Method getServerClaimsManagerMethod;
    private static volatile Method getClaimMethod;

    @Override
    public boolean isInClaimedChunk(Level level, BlockPos pos) {
        if (level.isClientSide) return false;

        MinecraftServer server = level.getServer();
        if (server == null) return false;
        if (!ensureInitialized()) return false;

        try {
            Object serverApi = getServerApiMethod.invoke(null, server);
            if (serverApi == null) return false;

            Method claimsManagerMethod = getServerClaimsManagerMethod;
            if (claimsManagerMethod == null || !claimsManagerMethod.getDeclaringClass().isInstance(serverApi)) {
                claimsManagerMethod = serverApi.getClass().getMethod("getServerClaimsManager");
                getServerClaimsManagerMethod = claimsManagerMethod;
            }

            Object claimsManager = claimsManagerMethod.invoke(serverApi);
            if (claimsManager == null) return false;

            Method claimMethod = getClaimMethod;
            if (claimMethod == null || !claimMethod.getDeclaringClass().isInstance(claimsManager)) {
                claimMethod = claimsManager.getClass().getMethod("get", ResourceLocation.class, ChunkPos.class);
                getClaimMethod = claimMethod;
            }

            ResourceLocation dimension = level.dimension().location();
            Object claim = claimMethod.invoke(claimsManager, dimension, new ChunkPos(pos));
            return claim != null;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return false;
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;

        try {
            Class<?> serverApiClass = Class.forName(SERVER_API_CLASS);
            getServerApiMethod = serverApiClass.getMethod("get", MinecraftServer.class);
            available = true;
        } catch (ReflectiveOperationException ignored) {
            available = false;
        }

        return available;
    }
}
