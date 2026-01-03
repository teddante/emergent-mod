package com.teddante.emergent;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Centralized water physics helper.
 * Provides utility methods and handles evaporation logic for water.
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

    /**
     * Handles evaporation based on biome temperature and exposure.
     * Called from WaterFluidMixin during random ticks.
     */
    public static void handleEvaporation(ServerWorld world, BlockPos pos, FluidState state, Random random) {
        // Only evaporate if exposed to air or in a very hot biome
        boolean exposed = world.isAir(pos.up());
        float temperature = world.getBiome(pos).value().getTemperature();

        double chance = 0.05; // Base chance (5%)

        if (temperature > 1.0) { // Hot biomes (Desert, Badlands, Nether)
            chance = 1.0; // Rapid evaporation
        } else if (temperature > 0.8) { // Warm biomes (Savanna)
            chance = 0.2;
        } else if (temperature < 0.2) { // Cold biomes
            chance = 0.01;
        }

        if (exposed || temperature > 1.5) {
            if (random.nextDouble() < chance) {
                int newLevel = state.getLevel() - 1;
                if (newLevel <= 0) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                } else {
                    // Update to a flowing state with lower level
                    Fluid fluid = state.getFluid();
                    if (fluid instanceof FlowableFluid flowable) {
                        boolean falling = state.contains(FlowableFluid.FALLING)
                                ? state.get(FlowableFluid.FALLING)
                                : false;
                        world.setBlockState(pos, flowable.getFlowing(newLevel, falling).getBlockState(),
                                Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }
}
