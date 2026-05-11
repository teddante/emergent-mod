package com.teddante.emergent.mixin;

import com.teddante.emergent.ImpactPhysics;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockImpactMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER))
    private void emergent$applyFallingBlockImpacts(CallbackInfo ci) {
        ImpactPhysics.applyKineticImpacts((FallingBlockEntity) (Object) this);
    }
}
