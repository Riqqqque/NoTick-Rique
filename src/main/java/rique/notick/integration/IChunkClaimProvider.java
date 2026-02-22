package rique.notick.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface IChunkClaimProvider {
    boolean isInClaimedChunk(Level level, BlockPos pos);
}
