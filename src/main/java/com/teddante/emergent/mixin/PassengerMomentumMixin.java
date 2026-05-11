package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.MovementPhysics;
import com.teddante.emergent.access.PassengerMomentumCarrier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PassengerMomentumMixin implements PassengerMomentumCarrier {
    @Unique
    private Vec3 emergent$dismountVehicleVelocity;

    @Override
    public Vec3 emergent$getDismountVehicleVelocity() {
        return emergent$dismountVehicleVelocity;
    }

    @Override
    public void emergent$setDismountVehicleVelocity(Vec3 velocity) {
        emergent$dismountVehicleVelocity = velocity;
    }

    @Inject(method = "stopRiding", at = @At("HEAD"))
    private void emergent$captureVehicleMomentum(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Entity vehicle = self.getVehicle();

        if (!EmergentConfig.get().passengerMomentumTransfer || vehicle == null || !(self instanceof LivingEntity)
                || MovementPhysics.isPlayerInDirectFlightControl(self)) {
            emergent$dismountVehicleVelocity = null;
            return;
        }

        Vec3 deltaMovement = vehicle.getDeltaMovement();
        Vec3 knownMovement = vehicle.getKnownMovement();
        emergent$dismountVehicleVelocity = knownMovement.lengthSqr() > deltaMovement.lengthSqr()
                ? knownMovement
                : deltaMovement;
    }
}
