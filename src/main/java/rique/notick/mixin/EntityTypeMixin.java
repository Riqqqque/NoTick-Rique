package rique.notick.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import rique.notick.NoTick;
import rique.notick.api.Tickable;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityType.class)
public class EntityTypeMixin implements Tickable.EntityType {
    @Shadow @Final private Holder.Reference<EntityType<?>> builtInRegistryHolder;
    @Unique private int notick$whitelistRevision = Integer.MIN_VALUE;
    @Unique private boolean notick$alwaysTick;
    @Unique private boolean notick$alwaysTickInRaid;

    @Override
    public boolean notick$shouldAlwaysTick() {
        notick$refreshWhitelistState();
        return notick$alwaysTick;
    }

    @Override
    public boolean notick$shouldAlwaysTickInRaid() {
        notick$refreshWhitelistState();
        return notick$alwaysTickInRaid;
    }

    @Unique
    private void notick$refreshWhitelistState() {
        int currentRevision = NoTick.getWhitelistRevision();
        if (notick$whitelistRevision == currentRevision) return;

        var id = builtInRegistryHolder.key().location();
        notick$alwaysTick = NoTick.isEntityTypeWhitelisted(id);
        notick$alwaysTickInRaid = NoTick.isRaidEntityTypeWhitelisted(id);
        notick$whitelistRevision = currentRevision;
    }
}
