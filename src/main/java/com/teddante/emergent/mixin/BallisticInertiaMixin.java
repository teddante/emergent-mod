package com.teddante.emergent.mixin;

import com.teddante.emergent.MovementPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class, FallingBlockEntity.class, ItemEntity.class, AbstractMinecart.class, AbstractBoat.class})
public abstract class BallisticInertiaMixin {
    @Unique
    private Vec3 emergent$ballisticVelocityBeforeTick;

    @Inject(method = "tick", at = @At("HEAD"))
    private void emergent$captureBallisticVelocity(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        emergent$ballisticVelocityBeforeTick = MovementPhysics.shouldPreserveBallistics(self) ? self.getDeltaMovement() : null;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$restoreBallisticVelocity(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Vec3 previousVelocity = emergent$ballisticVelocityBeforeTick;
        emergent$ballisticVelocityBeforeTick = null;

        if (previousVelocity == null || !MovementPhysics.shouldPreserveBallistics(self)) {
            return;
        }

        double retention = MovementPhysics.airRetention(self);
        Vec3 currentVelocity = self.getDeltaMovement();
        Vec3 expectedVelocity = new Vec3(previousVelocity.x * retention,
                MovementPhysics.expectedVerticalVelocity(self, previousVelocity, currentVelocity, retention),
                previousVelocity.z * retention);
        Vec3 restoredVelocity = MovementPhysics.preserveAxes(previousVelocity, currentVelocity, expectedVelocity);

        if (!restoredVelocity.equals(currentVelocity)) {
            self.setDeltaMovement(restoredVelocity);
            self.hurtMarked = true;
        }
    }
}
