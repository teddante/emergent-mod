package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ErosionPhysics;
import com.teddante.emergent.WaterPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cellular Automata Water Physics.
 * 
 * Replaces vanilla's flow generation with true volume conservation:
 * 1. Water flows DOWN first (gravity priority)
 * 2. Then equalizes horizontally with neighbors
 * 3. Total volume is conserved - water moves, never created/destroyed
 */
@Mixin(FlowingFluid.class)
public abstract class FlowableFluidMixin extends Fluid {

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    @Shadow
    public abstract FluidState getSource(boolean falling);

    // Water tick rate is 5 ticks
    @Unique
    private static final int WATER_TICK_RATE = 5;

    /**
     * Override the scheduled tick to implement cellular automata water physics.
     * This replaces vanilla's flow generation with volume-conserving equalization.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void emergent$cellularAutomataWater(ServerLevel world, BlockPos pos, BlockState blockState,
            FluidState fluidState, CallbackInfo ci) {
        if (!WaterPhysics.isWater((Fluid) (Object) this))
            return;

        if (EmergentConfig.get().hydraulicErosion) {
            ErosionPhysics.attemptErosion(world, pos, fluidState);
        }

        if (!EmergentConfig.get().finiteWaterFlow) {
            return;
        }

        // Cancel vanilla behavior for water
        ci.cancel();

        int currentLevel = fluidState.getAmount();
        if (currentLevel <= 0)
            return;

        // STEP 1: Gravity - try to flow down
        BlockPos below = pos.below();
        BlockState belowBlockState = world.getBlockState(below);
        FluidState belowFluidState = belowBlockState.getFluidState();

        if (canFlowInto(world, below, belowBlockState)) {
            int belowLevel = WaterPhysics.isWater(belowFluidState.getType()) ? belowFluidState.getAmount() : 0;
            int spaceBelow = 8 - belowLevel;

            if (spaceBelow > 0) {
                // Calculate ideal transfer
                int transfer = Math.min(currentLevel, spaceBelow);

                // CONSTRAINT: Waterloggable blocks (Source only)
                // We must ensure the transaction leaves the Source in a valid state.
                // We DO NOT check the Target: If the Target is waterloggable but we can't fill
                // it
                // (e.g. Level 1 flow into Fence), we will destructively replace the Fence
                // (break it)
                // to preserve the water volume.

                // Check Source (Current)
                // Note: We re-check assuming the transfer calculated above
                if (transfer > 0 && !isValidSourceLevel(blockState, currentLevel - transfer)) {
                    // Source is restrictive. It needs to reach exactly 0.
                    if (currentLevel - transfer > 0) {
                        transfer = 0; // Abort: Cannot partially drain source
                    }
                }

                if (transfer > 0) {
                    int newBelowLevel = belowLevel + transfer;
                    int newCurrentLevel = currentLevel - transfer;

                    // Update below
                    setWaterLevel(world, below, newBelowLevel, false);

                    // Update current position after transferring water down
                    if (newCurrentLevel <= 0) {
                        // Check if this is a waterlogged block
                        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
                            world.setBlock(pos, blockState.setValue(BlockStateProperties.WATERLOGGED, false), 3);
                        } else {
                            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    } else {
                        setWaterLevel(world, pos, newCurrentLevel, false);
                    }

                    // Schedule next tick
                    world.scheduleTick(pos, (Fluid) (Object) this, WATER_TICK_RATE);
                    world.scheduleTick(below, (Fluid) (Object) this, WATER_TICK_RATE);
                    return;
                }
            }
        }

        // STEP 2: Horizontal equalization

        // Constraint: If Source is Waterloggable (restrictive), we cannot partially
        // drain it horizontally.
        if (blockState.getBlock() instanceof LiquidBlockContainer) {
            // Schedule tick just in case context changes (e.g. block below clears up)
            world.scheduleTick(pos, (Fluid) (Object) this, WATER_TICK_RATE);
            return;
        }

        // Find all horizontal neighbors and calculate average level
        int totalLevel = currentLevel;
        int count = 1;
        BlockPos[] neighbors = new BlockPos[4];
        int[] neighborLevels = new int[4];
        boolean[] canFlow = new boolean[4];

        int idx = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborBlockState = world.getBlockState(neighborPos);
            neighbors[idx] = neighborPos;

            if (canFlowInto(world, neighborPos, neighborBlockState)) {
                FluidState neighborFluidState = neighborBlockState.getFluidState();
                int neighborLevel = WaterPhysics.isWater(neighborFluidState.getType())
                        ? neighborFluidState.getAmount()
                        : 0;

                // Filter: Removed target restriction. We flow into anything we can.
                // setWaterLevel will handle replacing the block if needed.

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

                // Iterative Fill Algorithm:
                // 1. Find the lowest water level among neighbors.
                // 2. Fill all neighbors at that lowest level by 1.
                // 3. Repeat until toDistribute is exhausted.
                // 4. If we don't have enough to fill all lowest neighbors, Randomize them.

                while (toDistribute > 0) {
                    // Find current minimum level among valid neighbors that we can flow into
                    int minLevel = 9; // Max is 8
                    for (int i = 0; i < 4; i++) {
                        if (canFlow[i] && neighborLevels[i] < currentLevel) { // Only fill explicitly valid targets
                            if (neighborLevels[i] < minLevel) {
                                minLevel = neighborLevels[i];
                            }
                        }
                    }

                    // Collect all neighbors at this minimum level
                    List<Integer> minLevelIndices = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        if (canFlow[i] && neighborLevels[i] == minLevel && neighborLevels[i] < currentLevel) {
                            minLevelIndices.add(i);
                        }
                    }

                    if (minLevelIndices.isEmpty()) {
                        break; // Should not happen given logic
                    }

                    // Do we have enough to give 1 to everyone?
                    if (toDistribute >= minLevelIndices.size()) {
                        // Yes, give 1 to all
                        for (int index : minLevelIndices) {
                            neighborLevels[index]++;
                            currentLevel--; // Update virtual current level
                            toDistribute--;
                        }
                    } else {
                        // No, we must choose lucky winners
                        Collections.shuffle(minLevelIndices);
                        for (int i = 0; i < toDistribute; i++) {
                            int chosenIndex = minLevelIndices.get(i);
                            neighborLevels[chosenIndex]++;
                            currentLevel--;
                        }
                        toDistribute = 0; // All gone
                    }
                }

                // Apply changes to world
                for (int i = 0; i < 4; i++) {
                    if (canFlow[i]) {
                        // Optimization: The implementation of setWaterLevel does extensive checks.
                        setWaterLevel(world, neighbors[i], neighborLevels[i], false);
                        world.scheduleTick(neighbors[i], (Fluid) (Object) this, WATER_TICK_RATE);
                    }
                }

                // Update current position after horizontal distribution
                if (currentLevel <= 0) {
                    // Check if this is a waterlogged block
                    if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
                        world.setBlock(pos, blockState.setValue(BlockStateProperties.WATERLOGGED, false), 3);
                    } else {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                } else {
                    setWaterLevel(world, pos, currentLevel, false);
                }
            }
        }

        // Schedule next tick if we still have water
        FluidState newState = world.getFluidState(pos);
        if (!newState.isEmpty()) {
            world.scheduleTick(pos, (Fluid) (Object) this, WATER_TICK_RATE);
        }
    }

    @Unique
    private boolean isValidSourceLevel(BlockState state, int level) {
        // Waterloggable blocks (Fences, Slabs, etc) used as SOURCE only support binary
        // water states.
        if (state.getBlock() instanceof LiquidBlockContainer) {
            return level == 0 || level == 8;
        }
        return true;
    }

    @Unique
    private boolean canFlowInto(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.isAir())
            return true;
        if (WaterPhysics.isWater(state.getFluidState().getType()))
            return true;
        // Allow flowing into waterloggable blocks
        if (state.getBlock() instanceof LiquidBlockContainer) {
            if (state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED)) {
                return true;
            }
        }
        // Allow flowing into any non-solid block (High grass, flowers, etc)
        // Also allow flowing into waterloggable blocks even without the property set
        // (redundant but safe)
        return !state.isSolid() || state.getBlock() instanceof LiquidBlockContainer;
    }

    @Unique
    private boolean isWaterloggableTarget(BlockState state) {
        if (state.getBlock() instanceof LiquidBlockContainer) {
            return state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED);
        }
        return false;
    }

    @Unique
    private void setWaterLevel(ServerLevel world, BlockPos pos, int level, boolean falling) {
        BlockState currentState = world.getBlockState(pos);
        FluidState currentFluidState = currentState.getFluidState();
        boolean isWater = WaterPhysics.isWater(currentFluidState.getType());

        if (level <= 0) {
            // Remove water - check if it's a waterlogged block first
            if (currentState.hasProperty(BlockStateProperties.WATERLOGGED) && currentState.getValue(BlockStateProperties.WATERLOGGED)) {
                world.setBlock(pos, currentState.setValue(BlockStateProperties.WATERLOGGED, false), 3);
            } else if (!currentState.isAir() && currentFluidState.isEmpty()) {
                // Non-water block, don't modify
            } else {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        } else if (level >= 8) {
            // Check if target is a waterloggable block
            if (isWaterloggableTarget(currentState)) {
                world.setBlock(pos, currentState.setValue(BlockStateProperties.WATERLOGGED, true), 3);
            } else {
                // Use still/source for level 8 so buckets can pick it up
                FluidState newState = this.getSource(false);
                world.setBlock(pos, newState.createLegacyBlock(), 3);
            }
        } else {
            // Partial levels (1-7)

            // Check if we need to destroy a block (Destructive Flow)
            // If the target is NOT Air and NOT Water, we must break it to place water here.
            if (!currentState.isAir() && !isWater) {
                Block.dropResources(currentState, world, pos,
                        currentState.hasBlockEntity() ? world.getBlockEntity(pos) : null);
                // Note: We don't need to manually set to Air first, setting to water block
                // replaces it.
            }

            // CRITICAL FIX: Always pass falling=false for partial levels.
            FluidState newState = this.getFlowing(level, false);
            world.setBlock(pos, newState.createLegacyBlock(), 3);
        }
    }
}
