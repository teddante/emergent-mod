package com.teddante.emergent.mixin;

import com.teddante.emergent.ImpactPhysics;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartImpactMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$applyMinecartImpacts(CallbackInfo ci) {
        ImpactPhysics.applyKineticImpacts((AbstractMinecart) (Object) this);
    }
}
