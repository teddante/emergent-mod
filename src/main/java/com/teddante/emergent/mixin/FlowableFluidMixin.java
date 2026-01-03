package com.teddante.emergent.mixin;

import com.teddante.emergent.WaterPhysics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cellular Automata Water Physics.
 * 
 * Replaces vanilla's flow generation with true volume conservation:
 * 1. Water flows DOWN first (gravity priority)
 * 2. Then equalizes horizontally with neighbors
 * 3. Total volume is conserved - water moves, never created/destroyed
 */
@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin extends Fluid {

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    // Water tick rate is 5 ticks
    @Unique
    private static final int WATER_TICK_RATE = 5;

    /**
     * Override the scheduled tick to implement cellular automata water physics.
     * This replaces vanilla's flow generation with volume-conserving equalization.
     */
    @Inject(method = "onScheduledTick", at = @At("HEAD"), cancellable = true)
    private void emergent$cellularAutomataWater(ServerWorld world, BlockPos pos, BlockState blockState,
            FluidState fluidState, CallbackInfo ci) {
        if (!WaterPhysics.isWater((Fluid) (Object) this))
            return;

        // Cancel vanilla behavior for water
        ci.cancel();

        int currentLevel = fluidState.getLevel();
        if (currentLevel <= 0)
            return;

        // STEP 1: Gravity - try to flow down
        BlockPos below = pos.down();
        BlockState belowBlockState = world.getBlockState(below);
        FluidState belowFluidState = belowBlockState.getFluidState();

        if (canFlowInto(world, below, belowBlockState)) {
            int belowLevel = WaterPhysics.isWater(belowFluidState.getFluid()) ? belowFluidState.getLevel() : 0;
            int spaceBelow = 8 - belowLevel;

            if (spaceBelow > 0) {
                // Transfer as much as possible downward
                int transfer = Math.min(currentLevel, spaceBelow);
                int newBelowLevel = belowLevel + transfer;
                int newCurrentLevel = currentLevel - transfer;

                // Update below
                setWaterLevel(world, below, newBelowLevel, true);

                // Update current
                if (newCurrentLevel <= 0) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                } else {
                    setWaterLevel(world, pos, newCurrentLevel, false);
                }

                // Schedule next tick
                world.scheduleFluidTick(pos, (Fluid) (Object) this, WATER_TICK_RATE);
                world.scheduleFluidTick(below, (Fluid) (Object) this, WATER_TICK_RATE);
                return;
            }
        }

        // STEP 2: Horizontal equalization
        // Find all horizontal neighbors and calculate average level
        int totalLevel = currentLevel;
        int count = 1;
        BlockPos[] neighbors = new BlockPos[4];
        int[] neighborLevels = new int[4];
        boolean[] canFlow = new boolean[4];

        int idx = 0;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborBlockState = world.getBlockState(neighborPos);
            neighbors[idx] = neighborPos;

            if (canFlowInto(world, neighborPos, neighborBlockState)) {
                FluidState neighborFluidState = neighborBlockState.getFluidState();
                int neighborLevel = WaterPhysics.isWater(neighborFluidState.getFluid())
                        ? neighborFluidState.getLevel()
                        : 0;
                neighborLevels[idx] = neighborLevel;
                canFlow[idx] = true;

                // Only equalize with lower neighbors (water flows to lower pressure)
                if (neighborLevel < currentLevel) {
                    totalLevel += neighborLevel;
                    count++;
                }
            } else {
                canFlow[idx] = false;
                neighborLevels[idx] = 0;
            }
            idx++;
        }

        // Calculate target level (average, but we can only give, not take)
        if (count > 1) {
            int avgLevel = totalLevel / count;

            // Only transfer if we're above average
            if (currentLevel > avgLevel) {
                int toDistribute = currentLevel - avgLevel;

                // Give 1 level to each lower neighbor until we've distributed enough
                for (int i = 0; i < 4 && toDistribute > 0; i++) {
                    if (canFlow[i] && neighborLevels[i] < currentLevel) {
                        int give = Math.min(1, toDistribute);
                        int newNeighborLevel = neighborLevels[i] + give;

                        if (newNeighborLevel <= 8) {
                            setWaterLevel(world, neighbors[i], newNeighborLevel, false);
                            world.scheduleFluidTick(neighbors[i], (Fluid) (Object) this, WATER_TICK_RATE);
                            toDistribute -= give;
                            currentLevel -= give;
                        }
                    }
                }

                // Update current position
                if (currentLevel <= 0) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                } else {
                    setWaterLevel(world, pos, currentLevel, false);
                }
            }
        }

        // Schedule next tick if we still have water
        FluidState newState = world.getFluidState(pos);
        if (!newState.isEmpty()) {
            world.scheduleFluidTick(pos, (Fluid) (Object) this, WATER_TICK_RATE);
        }
    }

    @Unique
    private boolean canFlowInto(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir())
            return true;
        if (WaterPhysics.isWater(state.getFluidState().getFluid()))
            return true;
        // Could add more checks for waterloggable blocks etc.
        return !state.isSolid();
    }

    @Unique
    private void setWaterLevel(ServerWorld world, BlockPos pos, int level, boolean falling) {
        if (level <= 0) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        } else if (level >= 8) {
            // Use flowing at level 8 (not source, to maintain uniform behavior)
            FluidState newState = this.getFlowing(8, falling);
            world.setBlockState(pos, newState.getBlockState(), Block.NOTIFY_ALL);
        } else {
            FluidState newState = this.getFlowing(level, falling);
            world.setBlockState(pos, newState.getBlockState(), Block.NOTIFY_ALL);
        }
    }
}
