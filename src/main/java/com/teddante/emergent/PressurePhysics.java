package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pressure chain reactions: if an explosion detonates in a confined space, the
 * blast effectively "echoes" — surviving solid blocks bounce the shockwave.
 */
public final class PressurePhysics {

    private PressurePhysics() {
    }

    /**
     * Returns a confinement factor in [0, 1] — 0 is fully open air, 1 is a sealed
     * pocket. Sampled from a 5x5x5 cube around the origin.
     */
    public static float confinementFactor(ServerLevel level, double x, double y, double z) {
        if (!EmergentConfig.get().pressureExplosions)
            return 0f;
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        int cz = (int) Math.floor(z);
        int solid = 0;
        int total = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    m.set(cx + dx, cy + dy, cz + dz);
                    BlockState state = level.getBlockState(m);
                    total++;
                    if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty()) {
                        solid++;
                    }
                }
            }
        }
        return total == 0 ? 0f : (float) solid / total;
    }

    /**
     * Given a base explosion power and a confinement factor, return the "effective"
     * power scaled up by up to 1.6x when confinement is high.
     */
    public static float pressureScaledPower(float basePower, float confinement) {
        if (!EmergentConfig.get().pressureExplosions || confinement <= 0.3f)
            return basePower;
        float boost = 1.0f + Math.min(0.6f, (confinement - 0.3f) * 1.2f);
        return basePower * boost;
    }
}
