package rique.notick.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class FTBChunkClaimProvider implements IChunkClaimProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FTBChunkClaimProvider.class);
    private static final String API_CLASS = "dev.ftb.mods.ftbchunks.api.FTBChunksAPI";
    private static final String CHUNK_DIM_POS_CLASS = "dev.ftb.mods.ftblibrary.math.ChunkDimPos";
    private static final String CLAIMED_CHUNKS_FIELD = "claimedChunks";

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean warnedFailure;
    private static volatile Method apiMethod;
    private static volatile Method isManagerLoadedMethod;
    private static volatile Method getManagerMethod;
    private static volatile Constructor<?> chunkDimPosConstructor;
    private static volatile Field claimedChunksField;

    @Override
    public boolean isInClaimedChunk(Level level, BlockPos pos) {
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

            Method managerMethod = getManagerMethod;
            if (managerMethod == null || !managerMethod.getDeclaringClass().isInstance(api)) {
                managerMethod = api.getClass().getMethod("getManager");
                getManagerMethod = managerMethod;
            }

            Object manager = managerMethod.invoke(api);
            Map<?, ?> claimedChunks = getClaimedChunks(manager);
            if (claimedChunks == null) {
                warnFailure("FTB Chunks manager did not expose claimed chunk data", null);
                return true;
            }

            Object chunkPosKey = chunkDimPosConstructor.newInstance(level, pos);
            return claimedChunks.containsKey(chunkPosKey);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            warnFailure("FTB Chunks claim lookup failed", exception);
            return true;
        }
    }

    private static Map<?, ?> getClaimedChunks(Object manager) {
        if (manager == null) return null;

        Field field = claimedChunksField;
        if (field == null || !field.getDeclaringClass().isInstance(manager)) {
            try {
                field = manager.getClass().getDeclaredField(CLAIMED_CHUNKS_FIELD);
                field.setAccessible(true);
                claimedChunksField = field;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        try {
            Object value = field.get(manager);
            return value instanceof Map<?, ?> map ? map : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            apiMethod = apiClass.getMethod("api");
            Class<?> chunkDimPosType = Class.forName(CHUNK_DIM_POS_CLASS);
            chunkDimPosConstructor = chunkDimPosType.getConstructor(Level.class, BlockPos.class);
            available = true;
        } catch (ReflectiveOperationException exception) {
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
}
