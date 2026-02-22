package rique.notick.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class OPACChunkClaimProvider implements IChunkClaimProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OPACChunkClaimProvider.class);
    private static final String SERVER_API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean warnedFailure;
    private static volatile Method getServerApiMethod;
    private static volatile Method getServerClaimsManagerMethod;
    private static volatile Method getClaimMethod;

    @Override
    public boolean isInClaimedChunk(Level level, BlockPos pos) {
        if (level.isClientSide) return false;

        MinecraftServer server = level.getServer();
        if (server == null) {
            warnFailure("MinecraftServer was null while querying OPAC claims", null);
            return true;
        }
        if (!ensureInitialized()) {
            warnFailure("Failed to initialize OPAC integration", null);
            return true;
        }

        try {
            Object serverApi = getServerApiMethod.invoke(null, server);
            if (serverApi == null) {
                warnFailure("OPAC server API returned null", null);
                return true;
            }

            Method claimsManagerMethod = getServerClaimsManagerMethod;
            if (claimsManagerMethod == null || !claimsManagerMethod.getDeclaringClass().isInstance(serverApi)) {
                claimsManagerMethod = serverApi.getClass().getMethod("getServerClaimsManager");
                getServerClaimsManagerMethod = claimsManagerMethod;
            }

            Object claimsManager = claimsManagerMethod.invoke(serverApi);
            if (claimsManager == null) {
                warnFailure("OPAC claims manager was null", null);
                return true;
            }

            Method claimMethod = getClaimMethod;
            if (claimMethod == null || !claimMethod.getDeclaringClass().isInstance(claimsManager)) {
                claimMethod = claimsManager.getClass().getMethod("get", ResourceLocation.class, ChunkPos.class);
                getClaimMethod = claimMethod;
            }

            ResourceLocation dimension = level.dimension().location();
            Object claim = claimMethod.invoke(claimsManager, dimension, new ChunkPos(pos));
            return claim != null;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            warnFailure("OPAC claim lookup failed", exception);
            return true;
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;

        try {
            Class<?> serverApiClass = Class.forName(SERVER_API_CLASS);
            getServerApiMethod = serverApiClass.getMethod("get", MinecraftServer.class);
            available = true;
        } catch (ReflectiveOperationException exception) {
            available = false;
            warnFailure("Unable to bind OPAC API via reflection", exception);
        }

        return available;
    }

    private static void warnFailure(String message, Throwable throwable) {
        if (warnedFailure) return;
        warnedFailure = true;
        if (throwable == null) {
            LOGGER.warn("[NoTick] {}. Falling back to fail-open chunk protection for gameplay safety.", message);
        } else {
            LOGGER.warn("[NoTick] {}. Falling back to fail-open chunk protection for gameplay safety.", message, throwable);
        }
    }
}
