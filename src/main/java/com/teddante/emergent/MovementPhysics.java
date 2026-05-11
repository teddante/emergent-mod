package com.teddante.emergent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

public final class MovementPhysics {
    private static final double AIR_DRAG_SCALE = 0.01;
    private static final double MIN_BALLISTIC_SPEED_SQR = 1.0E-5;

    private MovementPhysics() {
    }

    public static boolean isPlayerInDirectFlightControl(Entity entity) {
        return entity instanceof Player player && player.getAbilities().flying;
    }

    public static boolean shouldPreserveBallistics(Entity entity) {
        if (!EmergentConfig.get().ballisticInertia || isPlayerInDirectFlightControl(entity)
                || entity.noPhysics || entity.isRemoved() || entity.isPassenger() || entity.onGround()
                || entity.horizontalCollision || entity.isInWater() || entity.isInLava()
                || entity.getDeltaMovement().lengthSqr() <= MIN_BALLISTIC_SPEED_SQR) {
            return false;
        }

        if (entity instanceof AbstractMinecart minecart && minecart.isOnRails()) {
            return false;
        }

        return entity instanceof LivingEntity || entity instanceof FallingBlockEntity || entity instanceof ItemEntity
                || entity instanceof AbstractMinecart || entity instanceof AbstractBoat;
    }

    public static double expectedVerticalVelocity(Entity entity, Vec3 previousVelocity, Vec3 currentVelocity, double retention) {
        if (entity.isNoGravity()) {
            return previousVelocity.y * retention;
        }

        if (entity instanceof Player) {
            return currentVelocity.y;
        }

        if (previousVelocity.y > 0.0) {
            return (previousVelocity.y - entity.getGravity()) * retention;
        }

        return currentVelocity.y;
    }

    public static double airRetention(Entity entity) {
        double frontalArea = Math.max(0.001, entity.getBbWidth() * entity.getBbHeight());
        double mass = Math.max(0.001, ImpactPhysics.estimateMass(entity));
        return 1.0 / (1.0 + AIR_DRAG_SCALE * frontalArea / mass);
    }

    public static Vec3 preserveAxes(Vec3 previousVelocity, Vec3 currentVelocity, Vec3 expectedVelocity) {
        return new Vec3(
                preserveAxis(previousVelocity.x, currentVelocity.x, expectedVelocity.x),
                preserveAxis(previousVelocity.y, currentVelocity.y, expectedVelocity.y),
                preserveAxis(previousVelocity.z, currentVelocity.z, expectedVelocity.z));
    }

    public static double preserveAxis(double previous, double current, double expected) {
        if (Math.abs(expected) <= Math.abs(current) || Math.signum(previous) != 0.0 && Math.signum(previous) != Math.signum(expected)) {
            return current;
        }

        if (Math.abs(current) > 1.0E-6 && Math.signum(current) != Math.signum(expected)) {
            return current;
        }

        return expected;
    }
}
