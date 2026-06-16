package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EmergentProfiler;
import com.teddante.emergent.EnvironmentalExposure;
import com.teddante.emergent.ErosionPhysics;
import com.teddante.emergent.FiniteFluidQuietCache;
import com.teddante.emergent.FiniteFluidBudgetSettings;
import com.teddante.emergent.MaterialReactions;
import com.teddante.emergent.ThermalPhysics;
import com.teddante.emergent.WaterPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cellular automata fluid physics.
 * 
 * Replaces vanilla water/lava flow generation with volume conservation:
 * 1. Fluid flows down first (gravity priority)
 * 2. Then equalizes horizontally with neighbors
 * 3. Thin layers settle instead of spreading forever on flat ground
 */
@Mixin(FlowingFluid.class)
public abstract class FlowableFluidMixin extends Fluid {
    @Unique
    private static final int emergent$FINITE_FLUID_ACTIVE_TICK_BUDGET = FiniteFluidBudgetSettings.activeTickBudget();
    @Unique
    private static final int emergent$FINITE_FLUID_ACTIVE_CHUNK_TICK_BUDGET = FiniteFluidBudgetSettings.activeChunkTickBudget();
    @Unique
    private static final int emergent$FINITE_FLUID_INSPECTION_TICK_BUDGET = FiniteFluidBudgetSettings.inspectionTickBudget();
    @Unique
    private static final int emergent$FINITE_FLUID_INSPECTION_CHUNK_TICK_BUDGET = FiniteFluidBudgetSettings.inspectionChunkTickBudget();
    @Unique
    private static final int emergent$FINITE_FLUID_BUDGET_DEFER_SPREAD_TICKS = FiniteFluidBudgetSettings.budgetDeferSpreadTicks();
    @Unique
    private static final int emergent$MAX_HORIZONTAL_DIRECTIONS = 4;
    @Unique
    private static final Map<ServerLevel, EmergentFiniteFluidBudget> emergent$FINITE_FLUID_BUDGETS = new WeakHashMap<>();

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    @Shadow
    public abstract FluidState getSource(boolean falling);

    /**
     * Override the scheduled tick to implement cellular automata water physics.
     * This replaces vanilla's flow generation with volume-conserving equalization.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void emergent$cellularAutomataWater(ServerLevel world, BlockPos pos, BlockState blockState,
            FluidState fluidState, CallbackInfo ci) {
        Fluid fluid = (Fluid) (Object) this;
        if (!WaterPhysics.isFiniteFlowFluid(fluid))
            return;

        if (EmergentConfig.get().hydraulicErosion
                && WaterPhysics.canHydraulicallyErode(fluid)
                && !EmergentConfig.get().finiteWaterFlow) {
            ErosionPhysics.attemptErosion(world, pos, fluidState);
        }

        if (!EmergentConfig.get().finiteWaterFlow) {
            return;
        }

        // Cancel vanilla behavior for finite fluids.
        ci.cancel();
        long emergent$profileStart = EmergentProfiler.start();
        try {
        EmergentProfiler.count(world, "finite_fluid_ticks", 1);
        EmergentProfiler.recordChunk(world, EmergentProfiler.FINITE_FLUIDS, pos);

        int currentLevel = fluidState.getAmount();
        if (currentLevel <= 0)
            return;

        int startingLevel = currentLevel;
        int tickDelay = fluid.getTickDelay(world);

        boolean isWater = WaterPhysics.isWater(fluid);
        boolean isLava = WaterPhysics.isLava(fluid);
        if (isWater) {
            EmergentProfiler.count(world, "finite_fluid_water_ticks", 1);
            EmergentProfiler.recordChunk(world, EmergentProfiler.FINITE_WATER, pos);
        }
        if (isLava) {
            EmergentProfiler.count(world, "finite_fluid_lava_ticks", 1);
            EmergentProfiler.recordChunk(world, EmergentProfiler.FINITE_LAVA, pos);
        }

        if (!emergent$claimFiniteFluidInspectionSlot(world, pos, fluid, tickDelay)) {
            return;
        }

        String cachedQuietTickReason = emergent$cachedFiniteFluidQuietReason(world, pos, fluid, currentLevel);
        if (cachedQuietTickReason != null) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_hits", 1);
            if (isWater) {
                EmergentProfiler.count(world, "finite_fluid_water_thermal_cache_skips", 1);
            }
            EmergentProfiler.count(world, "finite_fluid_quiet_tick_skips", 1);
            EmergentProfiler.count(world, "finite_fluid_quiet_tick_" + cachedQuietTickReason + "_skips", 1);
            return;
        }

        if (isWater) {
            if (!ThermalPhysics.finiteWaterMayChangeThermally(world, pos, currentLevel)) {
                EmergentProfiler.count(world, "finite_fluid_water_thermal_quiet_skips", 1);
            } else {
                int evaporatedByEnvironment = ThermalPhysics.evaporateWaterInEvaporatingEnvironment(world, pos, currentLevel);
                if (evaporatedByEnvironment <= 0) {
                    EmergentProfiler.count(world, "finite_fluid_environment_evaporations", 1);
                    removeWaterAt(world, pos, blockState);
                    return;
                }
                currentLevel = evaporatedByEnvironment;

                if (ThermalPhysics.tryFreezeWaterFromStoredCold(world, pos, currentLevel)) {
                    EmergentProfiler.count(world, "finite_fluid_freezes", 1);
                    return;
                }

                int evaporatedLevel = ThermalPhysics.evaporateWaterNearHeat(world, pos, currentLevel);
                if (evaporatedLevel != currentLevel) {
                    EmergentProfiler.count(world, "finite_fluid_heat_evaporations", 1);
                    if (evaporatedLevel <= 0) {
                        removeWaterAt(world, pos, blockState);
                    } else {
                        setWaterLevel(world, pos, evaporatedLevel, false);
                        emergent$scheduleFiniteFluidIfActive(world, pos, fluid, evaporatedLevel, tickDelay);
                    }
                    return;
                }
            }
        }

        if (currentLevel >= 8 && emergent$isStableFiniteSource(world, pos, fluid)) {
            EmergentProfiler.count(world, "finite_fluid_stable_sources", 1);
            emergent$rememberFiniteFluidQuietReason(world, pos, fluid, currentLevel, "stable_source");
            return;
        }

        String earlyQuietTickReason = emergent$cheapFiniteFluidQuietReason(world, pos, fluid, currentLevel);
        if (earlyQuietTickReason != null) {
            EmergentProfiler.count(world, "finite_fluid_quiet_tick_skips", 1);
            EmergentProfiler.count(world, "finite_fluid_quiet_tick_" + earlyQuietTickReason + "_skips", 1);
            emergent$rememberFiniteFluidQuietReason(world, pos, fluid, currentLevel, earlyQuietTickReason);
            return;
        }

        if (!emergent$claimFiniteFluidWorkSlot(world, pos, fluid, tickDelay)) {
            return;
        }

        if (isLava && EmergentConfig.get().materialReactions) {
            EmergentProfiler.count(world, "finite_fluid_lava_heat", 1);
            ThermalPhysics.applyLavaContactHeat(world, pos, currentLevel);
        }

        String quietTickReason = emergent$finiteFluidQuietReason(world, pos, fluid, currentLevel);
        if (quietTickReason != null) {
            EmergentProfiler.count(world, "finite_fluid_quiet_tick_skips", 1);
            EmergentProfiler.count(world, "finite_fluid_quiet_tick_" + quietTickReason + "_skips", 1);
            emergent$rememberFiniteFluidQuietReason(world, pos, fluid, currentLevel, quietTickReason);
            return;
        }

        // STEP 1: Gravity - try to flow down
        BlockPos below = pos.below();
        BlockState belowBlockState = world.getBlockState(below);
        FluidState belowFluidState = belowBlockState.getFluidState();

        ThermalPhysics.FluidContactResult downwardThermalResult = tryReactWithOtherFiniteFluid(
                world, pos, currentLevel, below, belowBlockState, Direction.DOWN);
        if (downwardThermalResult.reacted()) {
            EmergentProfiler.count(world, "finite_fluid_thermal_reactions", 1);
            updateSourceAfterThermalReaction(world, pos, blockState, fluid, tickDelay, downwardThermalResult);
            return;
        }

        boolean belowWaterloggable = isWaterloggableTarget(world, below, belowBlockState);
        if (emergent$canFlowInto(world, below, belowBlockState, belowFluidState, fluid, Direction.DOWN)
                && (!belowWaterloggable || currentLevel >= 8)) {
            int belowLevel = WaterPhysics.isSameFluid(fluid, belowFluidState) ? belowFluidState.getAmount() : 0;
            int spaceBelow = 8 - belowLevel;

            if (spaceBelow > 0) {
                // Calculate ideal transfer
                int transfer = belowWaterloggable ? 8 : Math.min(currentLevel, spaceBelow);

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
                    EmergentProfiler.count(world, "finite_fluid_downward_moves", 1);
                    int newBelowLevel = belowLevel + transfer;
                    int newCurrentLevel = currentLevel - transfer;

                    if (EmergentConfig.get().hydraulicErosion && WaterPhysics.canHydraulicallyErode(fluid)) {
                        ErosionPhysics.attemptFlowErosion(world, pos, fluidState, Direction.DOWN, transfer);
                    }

                    // Update below
                    if (!setWaterLevel(world, below, newBelowLevel, false)) {
                        return;
                    }
                    emergent$transferSuspendedSediment(world, pos, blockState, below, transfer, startingLevel);

                    // Update current position after transferring water down
                    if (newCurrentLevel <= 0) {
                        removeWaterAt(world, pos, blockState);
                    } else {
                        setWaterLevel(world, pos, newCurrentLevel, false);
                    }

                    emergent$scheduleFiniteFluidIfActive(world, pos, fluid, newCurrentLevel, tickDelay);
                    emergent$scheduleFiniteFluidIfActive(world, below, fluid, newBelowLevel, tickDelay);
                    return;
                }
            }
        }

        // STEP 2: Horizontal equalization
        if (WaterPhysics.isWater(fluid)
                && currentLevel >= 8
                && tryMoveSourceIntoWaterloggableNeighbor(world, pos, blockState, fluidState, tickDelay)) {
            EmergentProfiler.count(world, "finite_fluid_waterloggable_moves", 1);
            return;
        }

        if (currentLevel <= WaterPhysics.settledThinLayerAmount(fluid)) {
            if (WaterPhysics.isWater(fluid) && EmergentConfig.get().hydraulicErosion
                    && ErosionPhysics.tryDepositSediment(world, pos, fluid, currentLevel)) {
                EmergentProfiler.count(world, "finite_fluid_sediment_deposits", 1);
                return;
            }
            EmergentProfiler.count(world, "finite_fluid_thin_settled", 1);
            return;
        }

        // Constraint: If Source is Waterloggable (restrictive), we cannot partially
        // drain it horizontally.
        if (blockState.getBlock() instanceof LiquidBlockContainer) {
            EmergentProfiler.count(world, "finite_fluid_waterloggable_quiet", 1);
            return;
        }

        // Find all horizontal neighbors and calculate average level
        int totalLevel = currentLevel;
        int count = 1;
        BlockPos[] neighbors = new BlockPos[4];
        Direction[] directions = new Direction[4];
        int[] neighborLevels = new int[4];
        boolean[] canFlow = new boolean[4];
        int idx = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborBlockState = world.getBlockState(neighborPos);
            FluidState neighborFluidState = neighborBlockState.getFluidState();
            neighbors[idx] = neighborPos;
            directions[idx] = dir;

            ThermalPhysics.FluidContactResult thermalResult = tryReactWithOtherFiniteFluid(
                    world, pos, currentLevel, neighborPos, neighborBlockState, dir);
            if (thermalResult.reacted()) {
                EmergentProfiler.count(world, "finite_fluid_thermal_reactions", 1);
                updateSourceAfterThermalReaction(world, pos, blockState, fluid, tickDelay, thermalResult);
                return;
            } else if (emergent$canFlowInto(world, neighborPos, neighborBlockState, neighborFluidState, fluid, dir)
                    && !isWaterloggableTarget(world, neighborPos, neighborBlockState)) {
                int neighborLevel = WaterPhysics.isSameFluid(fluid, neighborFluidState)
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
                int sourceLevelBeforeHorizontal = currentLevel;
                int toDistribute = currentLevel - avgLevel;

                // Iterative Fill Algorithm:
                // 1. Find the lowest water level among neighbors.
                // 2. Fill all neighbors at that lowest level by 1.
                // 3. Repeat until toDistribute is exhausted.
                // 4. If we don't have enough to fill all lowest neighbors, use a stable
                // position-based ordering so the same world settles the same way each run.

                int[] minLevelIndices = new int[emergent$MAX_HORIZONTAL_DIRECTIONS];
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
                    int minLevelCount = 0;
                    for (int i = 0; i < 4; i++) {
                        if (canFlow[i] && neighborLevels[i] == minLevel && neighborLevels[i] < currentLevel) {
                            minLevelIndices[minLevelCount] = i;
                            minLevelCount++;
                        }
                    }

                    if (minLevelCount == 0) {
                        break; // Should not happen given logic
                    }

                    // Do we have enough to give 1 to everyone?
                    if (toDistribute >= minLevelCount) {
                        // Yes, give 1 to all
                        for (int i = 0; i < minLevelCount; i++) {
                            int index = minLevelIndices[i];
                            neighborLevels[index]++;
                            currentLevel--; // Update virtual current level
                            toDistribute--;
                        }
                    } else {
                        // No, we must choose lucky winners
                        sortDeterministically(minLevelIndices, minLevelCount, pos);
                        for (int i = 0; i < toDistribute; i++) {
                            int chosenIndex = minLevelIndices[i];
                            neighborLevels[chosenIndex]++;
                            currentLevel--;
                        }
                        toDistribute = 0; // All gone
                    }
                }

                boolean movedHorizontally = false;
                int actualMovedHorizontally = 0;
                // Apply changes to world
                for (int i = 0; i < 4; i++) {
                    if (canFlow[i]) {
                        FluidState neighborFluidState = world.getFluidState(neighbors[i]);
                        int existingNeighborLevel = WaterPhysics.isSameFluid(fluid, neighborFluidState)
                                ? neighborFluidState.getAmount()
                                : 0;
                        int movedAmount = Math.max(0, neighborLevels[i] - existingNeighborLevel);
                        if (movedAmount <= 0) {
                            continue;
                        }

                        if (movedAmount > 0
                                && EmergentConfig.get().hydraulicErosion
                                && WaterPhysics.canHydraulicallyErode(fluid)) {
                            ErosionPhysics.attemptFlowErosion(world, pos, fluidState, directions[i], movedAmount);
                        }

                        // Optimization: The implementation of setWaterLevel does extensive checks.
                        if (!setWaterLevel(world, neighbors[i], neighborLevels[i], false)) {
                            continue;
                        }
                        emergent$transferSuspendedSediment(world, pos, blockState, neighbors[i], movedAmount, startingLevel);
                        emergent$scheduleFiniteFluidIfActive(world, neighbors[i], fluid, neighborLevels[i], tickDelay);
                        actualMovedHorizontally += movedAmount;
                        movedHorizontally = true;
                    }
                }

                if (movedHorizontally) {
                    EmergentProfiler.count(world, "finite_fluid_horizontal_moves", 1);
                    currentLevel = sourceLevelBeforeHorizontal - actualMovedHorizontally;
                    // Update current position after horizontal distribution.
                    if (currentLevel <= 0) {
                        removeWaterAt(world, pos, blockState);
                    } else {
                        setWaterLevel(world, pos, currentLevel, false);
                        emergent$scheduleFiniteFluidIfActive(world, pos, fluid, currentLevel, tickDelay);
                    }
                }
            }
        }
        } finally {
            EmergentProfiler.record(world, EmergentProfiler.FINITE_FLUIDS, emergent$profileStart);
        }
    }

    @Unique
    private boolean emergent$claimFiniteFluidInspectionSlot(ServerLevel world, BlockPos pos, Fluid fluid, int tickDelay) {
        if (emergent$FINITE_FLUID_INSPECTION_TICK_BUDGET <= 0
                && emergent$FINITE_FLUID_INSPECTION_CHUNK_TICK_BUDGET <= 0) {
            return true;
        }

        EmergentFiniteFluidBudget budget = emergent$finiteFluidBudgetForTick(world);
        if (emergent$FINITE_FLUID_INSPECTION_TICK_BUDGET > 0
                && budget.inspected >= emergent$FINITE_FLUID_INSPECTION_TICK_BUDGET) {
            emergent$deferFiniteFluidInspection(world, pos, fluid, tickDelay, "global");
            return false;
        }

        long chunkKey = ChunkPos.pack(pos);
        int chunkInspected = budget.chunkInspections.getOrDefault(chunkKey, 0);
        if (emergent$FINITE_FLUID_INSPECTION_CHUNK_TICK_BUDGET > 0
                && chunkInspected >= emergent$FINITE_FLUID_INSPECTION_CHUNK_TICK_BUDGET) {
            emergent$deferFiniteFluidInspection(world, pos, fluid, tickDelay, "chunk");
            return false;
        }

        budget.inspected++;
        budget.chunkInspections.put(chunkKey, chunkInspected + 1);
        EmergentProfiler.count(world, "finite_fluid_inspection_claims", 1);
        EmergentProfiler.count(world, "finite_fluid_inspection_chunk_claims", 1);
        return true;
    }

    @Unique
    private boolean emergent$claimFiniteFluidWorkSlot(ServerLevel world, BlockPos pos, Fluid fluid, int tickDelay) {
        if (emergent$FINITE_FLUID_ACTIVE_TICK_BUDGET <= 0
                && emergent$FINITE_FLUID_ACTIVE_CHUNK_TICK_BUDGET <= 0) {
            return true;
        }

        EmergentFiniteFluidBudget budget = emergent$finiteFluidBudgetForTick(world);

        if (emergent$FINITE_FLUID_ACTIVE_TICK_BUDGET > 0
                && budget.claimed >= emergent$FINITE_FLUID_ACTIVE_TICK_BUDGET) {
            emergent$deferFiniteFluidWork(world, pos, fluid, tickDelay, "global");
            return false;
        }

        long chunkKey = ChunkPos.pack(pos);
        int chunkClaimed = budget.chunkClaims.getOrDefault(chunkKey, 0);
        if (emergent$FINITE_FLUID_ACTIVE_CHUNK_TICK_BUDGET > 0
                && chunkClaimed >= emergent$FINITE_FLUID_ACTIVE_CHUNK_TICK_BUDGET) {
            emergent$deferFiniteFluidWork(world, pos, fluid, tickDelay, "chunk");
            return false;
        }

        budget.claimed++;
        budget.chunkClaims.put(chunkKey, chunkClaimed + 1);
        EmergentProfiler.count(world, "finite_fluid_budget_claims", 1);
        EmergentProfiler.count(world, "finite_fluid_budget_chunk_claims", 1);
        return true;
    }

    @Unique
    private EmergentFiniteFluidBudget emergent$finiteFluidBudgetForTick(ServerLevel world) {
        EmergentFiniteFluidBudget budget = emergent$FINITE_FLUID_BUDGETS.computeIfAbsent(
                world,
                ignored -> new EmergentFiniteFluidBudget(world.getGameTime()));
        if (budget.gameTime != world.getGameTime()) {
            budget.gameTime = world.getGameTime();
            budget.claimed = 0;
            budget.inspected = 0;
            budget.chunkClaims.clear();
            budget.chunkInspections.clear();
        }
        return budget;
    }

    @Unique
    private void emergent$deferFiniteFluidInspection(
            ServerLevel world,
            BlockPos pos,
            Fluid fluid,
            int tickDelay,
            String reason) {
        EmergentProfiler.count(world, "finite_fluid_inspection_deferrals", 1);
        EmergentProfiler.count(world, "finite_fluid_inspection_" + reason + "_deferrals", 1);
        emergent$scheduleDeferredFiniteFluidTick(world, pos, fluid, tickDelay);
    }

    @Unique
    private void emergent$deferFiniteFluidWork(
            ServerLevel world,
            BlockPos pos,
            Fluid fluid,
            int tickDelay,
            String reason) {
        EmergentProfiler.count(world, "finite_fluid_budget_deferrals", 1);
        EmergentProfiler.count(world, "finite_fluid_budget_" + reason + "_deferrals", 1);
        emergent$scheduleDeferredFiniteFluidTick(world, pos, fluid, tickDelay);
    }

    @Unique
    private void emergent$scheduleDeferredFiniteFluidTick(ServerLevel world, BlockPos pos, Fluid fluid, int tickDelay) {
        int spread = Math.max(1, emergent$FINITE_FLUID_BUDGET_DEFER_SPREAD_TICKS);
        int chunkPhase = Math.floorMod(ChunkPos.pack(pos), spread);
        int phaseDelay = 1 + Math.floorMod(directionRank(pos, 0) + pos.getY() + chunkPhase, spread);
        world.scheduleTick(pos, fluid, Math.max(1, tickDelay + phaseDelay));
    }

    @Unique
    private boolean isValidSourceLevel(BlockState state, int level) {
        // Waterloggable blocks (fences, slabs, etc.) used as sources only support
        // binary water states.
        if (state.getBlock() instanceof LiquidBlockContainer) {
            return level == 0 || level == 8;
        }
        return true;
    }

    @Unique
    private boolean emergent$isStableFiniteSource(ServerLevel world, BlockPos pos, Fluid fluid) {
        BlockPos below = pos.below();
        BlockState belowState = world.getBlockState(below);
        FluidState belowFluidState = belowState.getFluidState();
        if (emergent$fluidContactWouldReact(fluid, belowFluidState)) {
            return false;
        }

        if (emergent$targetCanAcceptFiniteFluid(world, below, belowState, belowFluidState, fluid)) {
            return false;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = world.getBlockState(targetPos);
            FluidState targetFluidState = targetState.getFluidState();
            if (emergent$fluidContactWouldReact(fluid, targetFluidState)) {
                return false;
            }

            if (WaterPhysics.isWater(fluid) && isWaterloggableTarget(world, targetPos, targetState)) {
                return false;
            }

            if (emergent$targetCanAcceptFiniteFluid(world, targetPos, targetState, targetFluidState, fluid)) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private boolean emergent$targetCanAcceptFiniteFluid(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            FluidState targetFluidState,
            Fluid fluid) {
        if (WaterPhysics.isSameFluid(fluid, targetFluidState)) {
            return targetFluidState.getAmount() < 8;
        }

        return state.isAir()
                || isWaterloggableTarget(world, pos, state)
                || (targetFluidState.isEmpty() && state.canBeReplaced(fluid));
    }

    @Unique
    private boolean emergent$fluidContactWouldReact(Fluid fluid, FluidState targetFluidState) {
        return EmergentConfig.get().materialReactions
                && !targetFluidState.isEmpty()
                && !WaterPhysics.isSameFluid(fluid, targetFluidState);
    }

    @Unique
    private boolean canFlowInto(ServerLevel world, BlockPos pos, BlockState state, Direction direction) {
        Fluid fluid = (Fluid) (Object) this;
        return emergent$canFlowInto(world, pos, state, state.getFluidState(), fluid, direction);
    }

    @Unique
    private boolean emergent$canFlowInto(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            FluidState targetFluidState,
            Fluid fluid,
            Direction direction) {
        if (state.isAir())
            return true;
        if (WaterPhysics.isSameFluid(fluid, targetFluidState))
            return true;
        if (isWaterloggableTarget(world, pos, state))
            return true;
        if (!targetFluidState.isEmpty())
            return false;
        return state.canBeReplaced(fluid);
    }

    @Unique
    private boolean isWaterloggableTarget(ServerLevel world, BlockPos pos, BlockState state) {
        if (!WaterPhysics.isWater((Fluid) (Object) this)) {
            return false;
        }

        if (state.getBlock() instanceof LiquidBlockContainer container) {
            return state.getFluidState().isEmpty()
                    && container.canPlaceLiquid(null, world, pos, state, (Fluid) (Object) this);
        }
        return false;
    }

    @Unique
    private boolean setWaterLevel(ServerLevel world, BlockPos pos, int level, boolean falling) {
        BlockState currentState = world.getBlockState(pos);
        FluidState currentFluidState = currentState.getFluidState();
        Fluid fluid = (Fluid) (Object) this;
        boolean isSameFluid = WaterPhysics.isSameFluid(fluid, currentFluidState);

        if (level <= 0) {
            // Remove fluid - check if it's a waterlogged block first.
            if (currentState.hasProperty(BlockStateProperties.WATERLOGGED) && currentState.getValue(BlockStateProperties.WATERLOGGED)) {
                world.setBlock(pos, currentState.setValue(BlockStateProperties.WATERLOGGED, false), 3);
            } else if (!currentState.isAir() && currentFluidState.isEmpty()) {
                // Non-water block, don't modify
            } else {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return true;
        } else if (level >= 8) {
            // Check if target is a waterloggable block
            if (WaterPhysics.isWater(fluid)
                    && isWaterloggableTarget(world, pos, currentState)
                    && currentState.getBlock() instanceof LiquidBlockContainer container) {
                if (container.placeLiquid(world, pos, currentState, this.getSource(false))) {
                    emergent$afterWaterPlaced(world, pos);
                    return true;
                }
                return false;
            } else {
                // Use still/source for level 8 so buckets can pick it up
                FluidState newState = this.getSource(false);
                world.setBlock(pos, newState.createLegacyBlock(), 3);
                emergent$afterWaterPlaced(world, pos);
                return true;
            }
        } else {
            // Partial levels (1-7)

            // Check if we need to destroy a block. If the target is neither air nor
            // the same fluid, we must break it to preserve fluid volume.
            if (!currentState.isAir() && !isSameFluid) {
                if (!currentState.canBeReplaced((Fluid) (Object) this)) {
                    return false;
                }
                Block.dropResources(currentState, world, pos,
                        currentState.hasBlockEntity() ? world.getBlockEntity(pos) : null);
                // Note: We don't need to manually set to Air first, setting to water block
                // replaces it.
            }

            // CRITICAL FIX: Always pass falling=false for partial levels.
            FluidState newState = this.getFlowing(level, false);
            world.setBlock(pos, newState.createLegacyBlock(), 3);
            emergent$afterWaterPlaced(world, pos);
            return true;
        }
    }

    @Unique
    private boolean tryMoveSourceIntoWaterloggableNeighbor(
            ServerLevel world,
            BlockPos pos,
            BlockState blockState,
            FluidState fluidState,
            int tickDelay) {
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            directions.add(direction);
        }
        sortDirectionsDeterministically(directions, pos);

        for (Direction direction : directions) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = world.getBlockState(targetPos);
            if (canFlowInto(world, targetPos, targetState, direction)
                    && !isWaterloggableTarget(world, targetPos, targetState)) {
                return false;
            }
        }

        for (Direction direction : directions) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = world.getBlockState(targetPos);
            if (!isWaterloggableTarget(world, targetPos, targetState)) {
                continue;
            }

            if (EmergentConfig.get().hydraulicErosion) {
                ErosionPhysics.attemptFlowErosion(world, pos, fluidState, direction, 8);
            }
            if (setWaterLevel(world, targetPos, 8, false)) {
                emergent$transferSuspendedSediment(world, pos, blockState, targetPos, 8, 8);
                removeWaterAt(world, pos, blockState);
                emergent$scheduleFiniteFluidIfActive(world, targetPos, (Fluid) (Object) this, 8, tickDelay);
                return true;
            }
        }

        return false;
    }

    @Unique
    private void removeWaterAt(ServerLevel world, BlockPos pos, BlockState blockState) {
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            world.setBlock(pos, blockState.setValue(BlockStateProperties.WATERLOGGED, false), 3);
        } else {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Unique
    private void emergent$afterWaterPlaced(ServerLevel world, BlockPos pos) {
        if (EmergentConfig.get().materialReactions && WaterPhysics.isWater((Fluid) (Object) this)) {
            int waterAmount = world.getFluidState(pos).getAmount();
            if (waterAmount > 0) {
                EnvironmentalExposure.applyStandingWaterContact(world, pos, waterAmount);
            }
            MaterialReactions.shortConductiveNeighbors(world, pos, world.getRandom());
        }
    }

    @Unique
    private void emergent$transferSuspendedSediment(
            ServerLevel world,
            BlockPos sourcePos,
            BlockState sourceState,
            BlockPos targetPos,
            int movedAmount,
            int sourceAmount) {
        if (!EmergentConfig.get().hydraulicErosion || !WaterPhysics.isWater((Fluid) (Object) this) || movedAmount <= 0) {
            return;
        }

        EnvironmentalExposure.transferSuspendedSediment(
                world,
                sourcePos,
                sourceState,
                targetPos,
                world.getBlockState(targetPos),
                movedAmount,
                sourceAmount);
    }

    @Unique
    private ThermalPhysics.FluidContactResult tryReactWithOtherFiniteFluid(
            ServerLevel world,
            BlockPos sourcePos,
            int sourceLevel,
            BlockPos targetPos,
            BlockState targetState,
            Direction direction) {
        Fluid fluid = (Fluid) (Object) this;
        return ThermalPhysics.reactFiniteFluidContact(world, sourcePos, fluid, sourceLevel, targetPos, targetState, direction);
    }

    @Unique
    private void updateSourceAfterThermalReaction(
            ServerLevel world,
            BlockPos sourcePos,
            BlockState sourceState,
            Fluid fluid,
            int tickDelay,
            ThermalPhysics.FluidContactResult result) {
        if (result.sourceBlockChanged()) {
            return;
        }

        if (result.remainingSourceAmount() <= 0) {
            removeWaterAt(world, sourcePos, sourceState);
            return;
        }

        setWaterLevel(world, sourcePos, result.remainingSourceAmount(), false);
        emergent$scheduleFiniteFluidIfActive(world, sourcePos, fluid, result.remainingSourceAmount(), tickDelay);
    }

    @Unique
    private void emergent$scheduleFiniteFluidIfActive(ServerLevel world, BlockPos pos, Fluid fluid, int amount, int tickDelay) {
        if (amount <= 0 || !WaterPhysics.isSameFluid(fluid, world.getFluidState(pos))) {
            return;
        }

        String quietReason = emergent$finiteFluidQuietReason(world, pos, fluid, amount);
        if (quietReason != null) {
            EmergentProfiler.count(world, "finite_fluid_quiet_schedule_skips", 1);
            EmergentProfiler.count(world, "finite_fluid_quiet_" + quietReason + "_skips", 1);
            emergent$rememberFiniteFluidQuietReason(world, pos, fluid, amount, quietReason);
            return;
        }

        EmergentProfiler.count(world, "finite_fluid_active_schedules", 1);
        world.scheduleTick(pos, fluid, tickDelay);
    }

    @Unique
    private String emergent$cheapFiniteFluidQuietReason(ServerLevel world, BlockPos pos, Fluid fluid, int amount) {
        if (amount <= WaterPhysics.settledThinLayerAmount(fluid)) {
            if (WaterPhysics.isWater(fluid)
                    && EmergentConfig.get().hydraulicErosion
                    && ErosionPhysics.tryDepositSediment(world, pos, fluid, amount)) {
                EmergentProfiler.count(world, "finite_fluid_sediment_deposits", 1);
            }
            return "thin";
        }

        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlockContainer) {
            return "waterloggable";
        }

        return null;
    }

    @Unique
    private String emergent$finiteFluidQuietReason(ServerLevel world, BlockPos pos, Fluid fluid, int amount) {
        if (amount <= WaterPhysics.settledThinLayerAmount(fluid)) {
            if (WaterPhysics.isWater(fluid)
                    && EmergentConfig.get().hydraulicErosion
                    && ErosionPhysics.tryDepositSediment(world, pos, fluid, amount)) {
                EmergentProfiler.count(world, "finite_fluid_sediment_deposits", 1);
            }
            return "thin";
        }

        if (amount >= 8 && emergent$isStableFiniteSource(world, pos, fluid)) {
            return "stable_source";
        }

        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlockContainer) {
            return "waterloggable";
        }

        BlockPos below = pos.below();
        BlockState belowState = world.getBlockState(below);
        FluidState belowFluidState = belowState.getFluidState();
        if (emergent$fluidContactWouldReact(fluid, belowFluidState)) {
            return null;
        }
        if (emergent$targetCanAcceptFiniteFluid(world, below, belowState, belowFluidState, fluid)) {
            return null;
        }

        boolean blockedByWaterloggableNeighbor = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = world.getBlockState(targetPos);
            FluidState targetFluidState = targetState.getFluidState();
            if (emergent$fluidContactWouldReact(fluid, targetFluidState)) {
                return null;
            }
            if (WaterPhysics.isSameFluid(fluid, targetFluidState)) {
                if (targetFluidState.getAmount() < amount) {
                    return null;
                }
                continue;
            }
            if (isWaterloggableTarget(world, targetPos, targetState)) {
                if (amount >= 8 && WaterPhysics.isWater(fluid)) {
                    return null;
                }
                blockedByWaterloggableNeighbor = true;
                continue;
            }
            if (emergent$targetCanAcceptFiniteFluid(world, targetPos, targetState, targetFluidState, fluid)) {
                return null;
            }
        }

        return blockedByWaterloggableNeighbor ? "waterloggable" : "no_work";
    }

    @Unique
    private String emergent$cachedFiniteFluidQuietReason(ServerLevel world, BlockPos pos, Fluid fluid, int amount) {
        return FiniteFluidQuietCache.reason(world, pos, fluid, amount);
    }

    @Unique
    private void emergent$rememberFiniteFluidQuietReason(ServerLevel world, BlockPos pos, Fluid fluid, int amount, String reason) {
        FiniteFluidQuietCache.remember(world, pos, fluid, amount, reason);
    }

    @Unique
    private void sortDeterministically(int[] indices, int count, BlockPos pos) {
        for (int i = 1; i < count; i++) {
            int value = indices[i];
            int rank = directionRank(pos, value);
            int j = i - 1;
            while (j >= 0 && directionRank(pos, indices[j]) > rank) {
                indices[j + 1] = indices[j];
                j--;
            }
            indices[j + 1] = value;
        }
    }

    @Unique
    private void sortDirectionsDeterministically(List<Direction> directions, BlockPos pos) {
        directions.sort((left, right) -> Integer.compare(directionRank(pos, directionIndex(left)), directionRank(pos, directionIndex(right))));
    }

    @Unique
    private int directionRank(BlockPos pos, int index) {
        int hash = pos.getX() * 73428767 ^ pos.getY() * 912931 ^ pos.getZ() * 4382893;
        return Math.floorMod(hash + index * 0x9E3779B9, 4);
    }

    @Unique
    private int directionIndex(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    @Unique
    private static final class EmergentFiniteFluidBudget {
        private long gameTime;
        private int claimed;
        private int inspected;
        private final Map<Long, Integer> chunkClaims = new LinkedHashMap<>();
        private final Map<Long, Integer> chunkInspections = new LinkedHashMap<>();

        private EmergentFiniteFluidBudget(long gameTime) {
            this.gameTime = gameTime;
        }
    }
}
