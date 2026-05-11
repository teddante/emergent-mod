package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.MovementPhysics;
import com.teddante.emergent.access.CarriedInertiaCarrier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class CarriedInertiaMixin implements CarriedInertiaCarrier {
    @Unique
    private static final int EMERGENT_MAX_CARRIED_INERTIA_TICKS = 40;
    @Unique
    private static final double EMERGENT_MIN_CARRIED_INERTIA_SPEED = 0.08;
    @Unique
    private int emergent$carriedInertiaTicks;
    @Unique
    private double emergent$carriedInertiaInitialSpeed;
    @Unique
    private Vec3 emergent$carriedInertiaVelocityBeforeTravel;

    @Override
    public void emergent$startCarriedInertia(double initialHorizontalSpeed) {
        if (initialHorizontalSpeed <= EMERGENT_MIN_CARRIED_INERTIA_SPEED) {
            return;
        }

        emergent$carriedInertiaInitialSpeed = initialHorizontalSpeed;
        emergent$carriedInertiaTicks = EMERGENT_MAX_CARRIED_INERTIA_TICKS;
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void emergent$preserveCarriedInertiaForTravel(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 velocity = self.getDeltaMovement();
        if (!emergent$shouldPreserveCarriedInertia(self, velocity)) {
            emergent$carriedInertiaTicks = 0;
            emergent$carriedInertiaVelocityBeforeTravel = null;
            return;
        }

        emergent$carriedInertiaVelocityBeforeTravel = velocity;
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void emergent$tickCarriedInertiaAfterTravel(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 previousVelocity = emergent$carriedInertiaVelocityBeforeTravel;
        emergent$carriedInertiaVelocityBeforeTravel = null;

        if (previousVelocity == null || !emergent$shouldPreserveCarriedInertia(self, previousVelocity)) {
            emergent$carriedInertiaTicks = 0;
            return;
        }

        Vec3 currentVelocity = self.getDeltaMovement();
        double retention = MovementPhysics.airRetention(self);
        Vec3 restoredVelocity = new Vec3(
                MovementPhysics.preserveAxis(previousVelocity.x, currentVelocity.x, previousVelocity.x * retention),
                currentVelocity.y,
                MovementPhysics.preserveAxis(previousVelocity.z, currentVelocity.z, previousVelocity.z * retention));

        if (!restoredVelocity.equals(currentVelocity)) {
            self.setDeltaMovement(restoredVelocity);
            self.hurtMarked = true;
        }

        emergent$carriedInertiaTicks--;
    }

    @Unique
    private boolean emergent$shouldPreserveCarriedInertia(LivingEntity entity, Vec3 velocity) {
        if (!EmergentConfig.get().passengerMomentumTransfer || emergent$carriedInertiaTicks <= 0
                || MovementPhysics.isPlayerInDirectFlightControl(entity) || entity.isPassenger()
                || entity.isInWater() || entity.isInLava() || entity.horizontalCollision) {
            return false;
        }

        double currentSpeed = velocity.horizontalDistance();
        return currentSpeed > EMERGENT_MIN_CARRIED_INERTIA_SPEED
                && currentSpeed > emergent$carriedInertiaInitialSpeed * 0.08;
    }
}
