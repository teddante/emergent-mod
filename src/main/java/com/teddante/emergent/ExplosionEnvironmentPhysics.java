package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ExplosionEnvironmentPhysics {
    private static final double VANILLA_ENVIRONMENT_EXPOSURE_RADIUS_MULTIPLIER = 2.0;
    private static final double RESIDUAL_HEAT_PER_BLAST_RADIUS = 0.12;
    private static final double STRESS_PER_RADIUS_SQUARED = 0.25;

    private ExplosionEnvironmentPhysics() {
    }

    public static void applyExplosionExposure(ServerLevel world, Vec3 center, float explosionRadius) {
        if (explosionRadius <= 1.0E-5F) {
            return;
        }

        double exposureRadius = exposureRadius(explosionRadius);
        int minX = (int) Math.floor(center.x - exposureRadius);
        int minY = (int) Math.floor(center.y - exposureRadius);
        int minZ = (int) Math.floor(center.z - exposureRadius);
        int maxX = (int) Math.ceil(center.x + exposureRadius);
        int maxY = (int) Math.ceil(center.y + exposureRadius);
        int maxZ = (int) Math.ceil(center.z + exposureRadius);

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!world.isInWorldBounds(pos)) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            if (!canRememberExplosionExposure(world, pos, state)) {
                continue;
            }

            Vec3 blockCenter = Vec3.atCenterOf(pos);
            double distance = blockCenter.distanceTo(center);
            double falloff = explosionFalloff(distance, explosionRadius);
            if (falloff <= 0.0) {
                continue;
            }

            EnvironmentalExposure.addHeat(world, pos, state, explosionHeat(explosionRadius, distance));
            EnvironmentalExposure.addStructuralStress(world, pos, state, explosionStructuralStress(explosionRadius, distance));
            ThermalPhysics.tryResolveThermalStress(world, pos, state);
        }
    }

    public static double exposureRadius(float explosionRadius) {
        return Math.max(0.0F, explosionRadius) * VANILLA_ENVIRONMENT_EXPOSURE_RADIUS_MULTIPLIER;
    }

    public static double explosionFalloff(double distanceMeters, float explosionRadius) {
        double exposureRadius = exposureRadius(explosionRadius);
        if (exposureRadius <= 0.0 || distanceMeters >= exposureRadius) {
            return 0.0;
        }

        double normalizedDistance = Math.max(0.0, distanceMeters) / exposureRadius;
        double linearFalloff = 1.0 - normalizedDistance;
        return linearFalloff * linearFalloff;
    }

    public static double explosionHeat(float explosionRadius, double distanceMeters) {
        return Math.max(0.0F, explosionRadius) * RESIDUAL_HEAT_PER_BLAST_RADIUS
                * explosionFalloff(distanceMeters, explosionRadius);
    }

    public static double explosionStructuralStress(float explosionRadius, double distanceMeters) {
        double radius = Math.max(0.0F, explosionRadius);
        return radius * radius * STRESS_PER_RADIUS_SQUARED * explosionFalloff(distanceMeters, explosionRadius);
    }

    private static boolean canRememberExplosionExposure(ServerLevel world, BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(world, pos) >= 0.0F;
    }
}
