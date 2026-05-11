package com.teddante.emergent.mixin;

import com.teddante.emergent.ImpactPhysics;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatImpactMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$applyBoatImpacts(CallbackInfo ci) {
        ImpactPhysics.applyKineticImpacts((AbstractBoat) (Object) this);
    }
}
