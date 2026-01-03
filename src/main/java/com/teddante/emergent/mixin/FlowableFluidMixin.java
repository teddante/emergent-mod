package com.teddante.emergent.mixin;

import com.teddante.emergent.WaterPhysics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
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

    @Shadow
    public abstract FluidState getStill(boolean falling);

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

                // Check if water can continue falling (for falling flag)
                BlockPos twoBelow = below.down();
                BlockState twoBelowState = world.getBlockState(twoBelow);
                boolean canContinueFalling = canFlowInto(world, twoBelow, twoBelowState);

                // Update below - only set falling if can continue falling
                setWaterLevel(world, below, newBelowLevel, canContinueFalling);

                // Update current position after transferring water down
                if (newCurrentLevel <= 0) {
                    // Check if this is a waterlogged block
                    if (blockState.contains(Properties.WATERLOGGED) && blockState.get(Properties.WATERLOGGED)) {
                        world.setBlockState(pos, blockState.with(Properties.WATERLOGGED, false), Block.NOTIFY_ALL);
                    } else {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
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

                // Update current position after horizontal distribution
                if (currentLevel <= 0) {
                    // Check if this is a waterlogged block
                    if (blockState.contains(Properties.WATERLOGGED) && blockState.get(Properties.WATERLOGGED)) {
                        world.setBlockState(pos, blockState.with(Properties.WATERLOGGED, false), Block.NOTIFY_ALL);
                    } else {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
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
        // Allow flowing into waterloggable blocks that aren't already waterlogged
        if (state.getBlock() instanceof Waterloggable) {
            if (state.contains(Properties.WATERLOGGED) && !state.get(Properties.WATERLOGGED)) {
                return true;
            }
        }
        return !state.isSolid();
    }

    /**
     * Checks if the target position is a waterloggable block (not already
     * waterlogged).
     */
    @Unique
    private boolean isWaterloggableTarget(BlockState state) {
        if (state.getBlock() instanceof Waterloggable) {
            return state.contains(Properties.WATERLOGGED) && !state.get(Properties.WATERLOGGED);
        }
        return false;
    }

    @Unique
    private void setWaterLevel(ServerWorld world, BlockPos pos, int level, boolean falling) {
        BlockState currentState = world.getBlockState(pos);

        if (level <= 0) {
            // Remove water - check if it's a waterlogged block first
            if (currentState.contains(Properties.WATERLOGGED) && currentState.get(Properties.WATERLOGGED)) {
                world.setBlockState(pos, currentState.with(Properties.WATERLOGGED, false), Block.NOTIFY_ALL);
            } else if (!currentState.isAir() && currentState.getFluidState().isEmpty()) {
                // Non-water block, don't modify
            } else {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        } else if (level >= 8) {
            // Check if target is a waterloggable block
            if (isWaterloggableTarget(currentState)) {
                world.setBlockState(pos, currentState.with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL);
            } else {
                // Use still/source for level 8 so buckets can pick it up
                FluidState newState = this.getStill(false);
                world.setBlockState(pos, newState.getBlockState(), Block.NOTIFY_ALL);
            }
        } else {
            // Partial levels can't waterlog (you need full water)
            // Only set if target isn't a solid block
            if (currentState.isAir() || WaterPhysics.isWater(currentState.getFluidState().getFluid())) {
                FluidState newState = this.getFlowing(level, falling);
                world.setBlockState(pos, newState.getBlockState(), Block.NOTIFY_ALL);
            }
        }
    }
}
