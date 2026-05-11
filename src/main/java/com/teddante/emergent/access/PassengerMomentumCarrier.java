package com.teddante.emergent.access;

import net.minecraft.world.phys.Vec3;

public interface PassengerMomentumCarrier {
    Vec3 emergent$getDismountVehicleVelocity();

    void emergent$setDismountVehicleVelocity(Vec3 velocity);
}
