package com.teddante.emergent.mixin;

import com.teddante.emergent.ImpactPhysics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityImpactMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$applyLivingEntityImpacts(CallbackInfo ci) {
        ImpactPhysics.applyKineticImpacts((LivingEntity) (Object) this);
    }
}
