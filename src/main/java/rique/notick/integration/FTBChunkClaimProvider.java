package rique.notick.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

public final class FTBChunkClaimProvider implements IChunkClaimProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FTBChunkClaimProvider.class);
    private static final String API_CLASS = "dev.ftb.mods.ftbchunks.api.FTBChunksAPI";
    private static final String CHUNK_DIM_POS_CLASS = "dev.ftb.mods.ftblibrary.math.ChunkDimPos";

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean disabled;
    private static volatile boolean warnedFailure;
    private static volatile Method apiMethod;
    private static volatile Method isManagerLoadedMethod;
    private static volatile Method getOwningTeamMethod;
    private static volatile Method getManagerMethod;
    private static volatile Method getClaimMethod;
    private static volatile Constructor<?> chunkDimPosConstructor;

    @Override
    public boolean isInClaimedChunk(Level level, BlockPos pos) {
        if (disabled) return true;
        if (!ensureInitialized()) {
            warnFailure("Failed to initialize FTB Chunks integration", null);
            return true;
        }

        try {
            Object api = apiMethod.invoke(null);
            if (api == null) {
                warnFailure("FTB Chunks API returned null", null);
                return true;
            }

            Method loadedMethod = isManagerLoadedMethod;
            if (loadedMethod == null || !loadedMethod.getDeclaringClass().isInstance(api)) {
                loadedMethod = api.getClass().getMethod("isManagerLoaded");
                isManagerLoadedMethod = loadedMethod;
            }

            boolean managerLoaded = (boolean) loadedMethod.invoke(api);
            if (!managerLoaded) return true;

            Method owningTeamMethod = getOwningTeamMethod;
            if (owningTeamMethod != null) {
                Object result = owningTeamMethod.invoke(api, level, new ChunkPos(pos));
                if (result instanceof Optional<?> owner) {
                    return owner.isPresent();
                }

                disable("FTB Chunks ownership API returned an unexpected value", null);
                return true;
            }

            Method managerMethod = getManagerMethod;
            if (managerMethod == null || !managerMethod.getDeclaringClass().isInstance(api)) {
                managerMethod = api.getClass().getMethod("getManager");
                getManagerMethod = managerMethod;
            }

            Object manager = managerMethod.invoke(api);
            if (manager == null) {
                disable("FTB Chunks manager was unavailable", null);
                return true;
            }

            Method claimMethod = getClaimMethod;
            if (claimMethod == null || !claimMethod.getDeclaringClass().isInstance(manager)) {
                claimMethod = manager.getClass().getMethod("getChunk", chunkDimPosConstructor.getDeclaringClass());
                getClaimMethod = claimMethod;
            }

            Object chunkPosKey = chunkDimPosConstructor.newInstance(level, pos);
            return claimMethod.invoke(manager, chunkPosKey) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable("FTB Chunks claim lookup failed", exception);
            return true;
        }
    }

    @Override
    public boolean isOperational() {
        return !disabled && ensureInitialized();
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            apiMethod = apiClass.getMethod("api");
            Class<?> apiType = apiMethod.getReturnType();
            try {
                getOwningTeamMethod = apiType.getMethod("getOwningTeam", Level.class, ChunkPos.class);
            } catch (NoSuchMethodException ignored) {
                Class<?> chunkDimPosType = Class.forName(CHUNK_DIM_POS_CLASS);
                chunkDimPosConstructor = chunkDimPosType.getConstructor(Level.class, BlockPos.class);
            }
            available = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            available = false;
            warnFailure("Unable to bind FTB Chunks API via reflection", exception);
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

    private static void disable(String message, Throwable throwable) {
        disabled = true;
        warnFailure(message, throwable);
    }
}
