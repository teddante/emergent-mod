package com.teddante.emergent.mixin;

import com.teddante.emergent.MovementPhysics;
import com.teddante.emergent.access.PassengerMomentumCarrier;
import com.teddante.emergent.access.CarriedInertiaCarrier;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPassengerMomentumMixin {
    @Inject(method = "stopRiding", at = @At("TAIL"))
    private void emergent$applyVehicleMomentumAfterDismount(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        PassengerMomentumCarrier momentumCarrier = (PassengerMomentumCarrier) this;
        Vec3 vehicleVelocity = momentumCarrier.emergent$getDismountVehicleVelocity();
        momentumCarrier.emergent$setDismountVehicleVelocity(null);

        if (vehicleVelocity == null || self.getVehicle() != null || MovementPhysics.isPlayerInDirectFlightControl(self)) {
            return;
        }

        self.push(vehicleVelocity.x, vehicleVelocity.y, vehicleVelocity.z);
        ((CarriedInertiaCarrier) self).emergent$startCarriedInertia(vehicleVelocity.horizontalDistance());
        if (!self.level().isClientSide() && self instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }
}
