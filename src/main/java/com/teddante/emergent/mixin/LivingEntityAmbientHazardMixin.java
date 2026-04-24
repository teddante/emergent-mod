package com.teddante.emergent.mixin;

import com.teddante.emergent.CreaturePanic;
import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.SmokeSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies ambient environmental effects each tick: smoke suffocation/blindness
 * and panic-driven motion for animals and villagers.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAmbientHazardMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$ambientHazards(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }

        // Smoke effects — sampled every 10 ticks for cheapness.
        if (EmergentConfig.get().smokeAndFumes && self.tickCount % 10 == 0) {
            float intensity = SmokeSystem.getSmokeIntensityAt(level, self);
            if (intensity > 0) {
                SmokeSystem.applySmokeEffects(level, self, intensity);
            }
        }

        // Threat response for animals and villagers.
        CreaturePanic.tick(self);
    }
}
