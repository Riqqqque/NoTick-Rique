package rique.notick.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import rique.notick.NoTick;
import rique.notick.api.Tickable;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityType.class)
public class EntityTypeMixin implements Tickable.EntityType {
    @Shadow @Final private Holder.Reference<EntityType<?>> builtInRegistryHolder;

    @Override
    public boolean doespotatotick$shouldAlwaysTick() {
        return NoTick.isEntityTypeWhitelisted(builtInRegistryHolder.key().location());
    }

    @Override
    public boolean doespotatotick$shouldAlwaysTickInRaid() {
        return NoTick.isRaidEntityTypeWhitelisted(builtInRegistryHolder.key().location());
    }
}
