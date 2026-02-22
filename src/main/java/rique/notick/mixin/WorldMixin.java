package rique.notick.mixin;

import rique.notick.NoTick;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = Level.class, priority = 2000)
public abstract class WorldMixin {
    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private void optimizeEntitiesTick(Consumer<Entity> consumerEntity, Entity entity, CallbackInfo ci) {
        if (!NoTick.isTickable(entity)) ci.cancel();
    }
}
