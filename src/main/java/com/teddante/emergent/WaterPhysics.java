package com.teddante.emergent;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.level.material.WaterFluid;

/**
 * Centralized water physics helper.
 * Provides utility methods for water physics.
 */
public final class WaterPhysics {

    private WaterPhysics() {
    } // Utility class

    /**
     * Single source of truth for water type checking.
     * Use this instead of scattered instanceof checks.
     */
    public static boolean isWater(Fluid fluid) {
        return fluid instanceof WaterFluid;
    }

    public static boolean isLava(Fluid fluid) {
        return fluid instanceof LavaFluid;
    }

    public static boolean isFiniteFlowFluid(Fluid fluid) {
        return isWater(fluid) || isLava(fluid);
    }

    public static boolean isSameFluid(Fluid fluid, FluidState state) {
        return state.getType().isSame(fluid);
    }

    public static boolean canHydraulicallyErode(Fluid fluid) {
        return isWater(fluid);
    }

    public static int settledThinLayerAmount(Fluid fluid) {
        return isLava(fluid) ? 3 : 2;
    }
}

