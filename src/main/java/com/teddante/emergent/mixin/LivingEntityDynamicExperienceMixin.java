package com.teddante.emergent.mixin;

import com.teddante.emergent.DynamicExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDynamicExperienceMixin {
    @Inject(method = "getExperienceReward", at = @At("RETURN"), cancellable = true)
    private void emergent$useDynamicExperienceReward(
            ServerLevel level,
            @Nullable Entity killer,
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(DynamicExperience.rewardAfterVanillaProcessing((LivingEntity) (Object) this, cir.getReturnValue()));
    }
}
