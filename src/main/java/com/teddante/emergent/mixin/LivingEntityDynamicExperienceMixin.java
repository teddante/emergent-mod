package com.teddante.emergent.mixin;

import com.teddante.emergent.DynamicExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDynamicExperienceMixin {
    @Shadow
    protected abstract int getBaseExperienceReward(ServerLevel level);

    @Redirect(
            method = "getExperienceReward",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getBaseExperienceReward(Lnet/minecraft/server/level/ServerLevel;)I"))
    private int emergent$useDynamicBaseExperienceReward(LivingEntity entity, ServerLevel level) {
        int vanillaBaseReward = this.getBaseExperienceReward(level);
        return DynamicExperience.baseRewardForVanillaProcessing(entity, vanillaBaseReward);
    }
}
