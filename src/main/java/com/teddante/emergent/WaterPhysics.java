package com.teddante.emergent;

import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.WaterFluid;

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
}
