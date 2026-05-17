package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Local heat-transfer rules for interactions that Minecraft already models as
 * block-scale events: lava solidifying, water quenching, and shallow puddles
 * evaporating near heat.
 */
public final class ThermalPhysics {
    private static final double STORED_HEAT_EVAPORATION_THRESHOLD = 0.35;
    private static final double STORED_COLD_FREEZE_THRESHOLD = 0.55;
    private static final double SNOW_LAYER_MELT_HEAT = 0.22;
    private static final double SNOW_BLOCK_MELT_HEAT = 0.85;
    private static final double ICE_MELT_HEAT = 1.0;
    private static final double PACKED_ICE_MELT_HEAT = 2.5;
    private static final double MELT_WATER_MOISTURE_PER_LAYER = 0.08;
    private static final double LAVA_CONTACT_HEAT_PER_CUBIC_METER = 0.45;

    public record FluidContactResult(boolean reacted, int remainingSourceAmount, boolean sourceBlockChanged) {
        public static FluidContactResult none(int sourceAmount) {
            return new FluidContactResult(false, sourceAmount, false);
        }

        public static FluidContactResult targetChanged(int sourceAmount) {
            return new FluidContactResult(true, sourceAmount, false);
        }

        public static FluidContactResult sourceChanged() {
            return new FluidContactResult(true, 0, true);
        }

        public static FluidContactResult sourceEvaporated() {
            return new FluidContactResult(true, 0, false);
        }
    }

    private ThermalPhysics() {
    }

    public static FluidContactResult reactFiniteFluidContact(
            ServerLevel world,
            BlockPos sourcePos,
            Fluid sourceFluid,
            int sourceAmount,
            BlockPos targetPos,
            BlockState targetState,
            Direction direction) {
        FluidState targetFluidState = targetState.getFluidState();
        if (targetFluidState.isEmpty() || WaterPhysics.isSameFluid(sourceFluid, targetFluidState)) {
            return FluidContactResult.none(sourceAmount);
        }

        if (WaterPhysics.isLava(sourceFluid) && WaterPhysics.isWater(targetFluidState.getType())) {
            if (direction == Direction.DOWN) {
                world.setBlockAndUpdate(targetPos, Blocks.STONE.defaultBlockState());
                fizz(world, targetPos);
                return FluidContactResult.targetChanged(sourceAmount);
            }

            world.setBlockAndUpdate(sourcePos, lavaContactBlock(sourceAmount).defaultBlockState());
            fizz(world, sourcePos);
            return FluidContactResult.sourceChanged();
        }

        if (WaterPhysics.isWater(sourceFluid) && WaterPhysics.isLava(targetFluidState.getType())) {
            if (sourceAmount <= WaterPhysics.settledThinLayerAmount(sourceFluid)) {
                fizz(world, sourcePos);
                return FluidContactResult.sourceEvaporated();
            }

            world.setBlockAndUpdate(targetPos, lavaContactBlock(targetFluidState).defaultBlockState());
            fizz(world, targetPos);
            return FluidContactResult.targetChanged(sourceAmount);
        }

        return FluidContactResult.none(sourceAmount);
    }

    public static int evaporateWaterNearHeat(ServerLevel world, BlockPos pos, int amount) {
        if (amount >= 8) {
            return amount;
        }

        int heat = neighboringHeat(world, pos) + storedHeatEvaporationStrength(world, pos);
        if (heat <= 0) {
            return amount;
        }

        if (amount <= WaterPhysics.settledThinLayerAmount(world.getFluidState(pos).getType())) {
            fizz(world, pos);
            return 0;
        }

        if (amount <= 4) {
            fizz(world, pos);
            return Math.max(0, amount - heat);
        }

        return amount;
    }

    public static int evaporateWaterInEvaporatingEnvironment(boolean waterEvaporates, int amount) {
        return waterEvaporates && amount > 0 ? 0 : amount;
    }

    public static int evaporateWaterInEvaporatingEnvironment(ServerLevel world, BlockPos pos, int amount) {
        int remainingAmount = evaporateWaterInEvaporatingEnvironment(
                world.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos),
                amount);
        if (remainingAmount != amount) {
            fizz(world, pos);
        }
        return remainingAmount;
    }

    public static void applyLavaContactHeat(ServerLevel world, BlockPos lavaPos, int lavaAmount) {
        double heat = lavaContactHeat(lavaAmount);
        if (heat <= 0.0) {
            return;
        }

        for (Direction direction : Direction.values()) {
            BlockPos targetPos = lavaPos.relative(direction);
            BlockState targetState = world.getBlockState(targetPos);
            if (targetState.isAir() || !targetState.getFluidState().isEmpty() || targetState.getDestroySpeed(world, targetPos) < 0.0F) {
                continue;
            }

            EnvironmentalExposure.addHeat(world, targetPos, targetState, heat);
            ThermalPhysics.tryMeltFrozenSurface(world, targetPos, targetState);
        }
    }

    public static double lavaContactHeat(int lavaAmount) {
        return EnvironmentalExposure.fluidAmountCubicMeters(lavaAmount) * LAVA_CONTACT_HEAT_PER_CUBIC_METER;
    }

    public static boolean tryFreezeWaterFromStoredCold(ServerLevel world, BlockPos pos, int amount) {
        if (amount <= 0 || EnvironmentalExposure.cold(world, pos, world.getBlockState(pos)) < STORED_COLD_FREEZE_THRESHOLD) {
            return false;
        }
        if (EnvironmentalExposure.heat(world, pos, world.getBlockState(pos)) >= STORED_HEAT_EVAPORATION_THRESHOLD) {
            return false;
        }

        if (amount >= 8) {
            world.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
            EnvironmentalExposure.clearCold(world, pos);
            return true;
        }

        if (amount <= WaterPhysics.settledThinLayerAmount(Fluids.WATER)) {
            BlockState frozenFilm = Blocks.SNOW.defaultBlockState()
                    .setValue(SnowLayerBlock.LAYERS, Math.max(1, amount));
            BlockState belowState = world.getBlockState(pos.below());
            if (!belowState.isAir() && belowState.getFluidState().isEmpty() && frozenFilm.canSurvive(world, pos)) {
                world.setBlockAndUpdate(pos, frozenFilm);
                EnvironmentalExposure.clearCold(world, pos);
                return true;
            }
        }

        return false;
    }

    public static boolean tryMeltFrozenSurface(ServerLevel world, BlockPos pos, BlockState state) {
        double heat = storedAndNeighboringHeat(world, pos, state);
        if (state.is(Blocks.SNOW) && state.hasProperty(SnowLayerBlock.LAYERS)) {
            return tryMeltSnowLayer(world, pos, state, heat);
        }
        if (state.is(Blocks.SNOW_BLOCK) && heat >= SNOW_BLOCK_MELT_HEAT) {
            world.setBlockAndUpdate(pos, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
            EnvironmentalExposure.clearHeat(world, pos);
            fizz(world, pos);
            return true;
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
            return tryMeltIce(world, pos, ICE_MELT_HEAT, heat);
        }
        if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return tryMeltIce(world, pos, PACKED_ICE_MELT_HEAT, heat);
        }

        return false;
    }

    public static boolean tryResolveThermalStress(ServerLevel world, BlockPos pos, BlockState state) {
        double stress = EnvironmentalExposure.structuralStress(world, pos, state);
        if (stress < MaterialPhysicsProfiles.structuralStressThreshold(state)) {
            return false;
        }

        EnvironmentalExposure.clearStructuralStress(world, pos);
        BlockState fracturedState = MaterialPhysicsProfiles.thermalFractureState(state);
        if (fracturedState != null) {
            world.setBlockAndUpdate(pos, fracturedState);
            world.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.45f, 0.9f);
            return true;
        }

        if (state.is(MaterialReactionTags.BRITTLE)) {
            world.destroyBlock(pos, true);
            world.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.55f, 1.1f);
            return true;
        }

        return false;
    }

    public static boolean isHeatSource(BlockState state) {
        if (!state.is(MaterialReactionTags.HEAT_SOURCES)) {
            return false;
        }

        if (state.hasProperty(BlockStateProperties.LIT)) {
            return state.getValue(BlockStateProperties.LIT);
        }

        return true;
    }

    public static int neighboringHeat(ServerLevel world, BlockPos pos) {
        int heat = 0;
        for (Direction direction : Direction.values()) {
            BlockState state = world.getBlockState(pos.relative(direction));
            if (state.getBlock() instanceof LiquidBlock && WaterPhysics.isLava(state.getFluidState().getType())) {
                heat = Math.max(heat, 2);
            } else if (isHeatSource(state)) {
                heat = Math.max(heat, state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) ? 2 : 1);
            }
        }
        return heat;
    }

    private static boolean tryMeltSnowLayer(ServerLevel world, BlockPos pos, BlockState state, double heat) {
        if (heat < SNOW_LAYER_MELT_HEAT) {
            return false;
        }

        int layers = state.getValue(SnowLayerBlock.LAYERS);
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        if (!supportState.isAir() && supportState.getFluidState().isEmpty()) {
            EnvironmentalExposure.addMoisture(
                    world,
                    supportPos,
                    supportState,
                    layers * MELT_WATER_MOISTURE_PER_LAYER * MaterialPhysicsProfiles.surfaceWaterAbsorption(supportState));
        }

        if (layers <= 1) {
            world.removeBlock(pos, false);
        } else {
            world.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
        }
        EnvironmentalExposure.clearHeat(world, pos);
        fizz(world, pos);
        return true;
    }

    private static boolean tryMeltIce(ServerLevel world, BlockPos pos, double threshold, double heat) {
        if (heat < threshold) {
            return false;
        }

        world.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        EnvironmentalExposure.clearHeat(world, pos);
        fizz(world, pos);
        return true;
    }

    private static int storedHeatEvaporationStrength(ServerLevel world, BlockPos pos) {
        double storedHeat = Math.max(
                EnvironmentalExposure.heat(world, pos, world.getBlockState(pos)),
                EnvironmentalExposure.heat(world, pos.below(), world.getBlockState(pos.below())));
        if (storedHeat >= ICE_MELT_HEAT) {
            return 2;
        }
        if (storedHeat >= STORED_HEAT_EVAPORATION_THRESHOLD) {
            return 1;
        }

        return 0;
    }

    private static double storedAndNeighboringHeat(ServerLevel world, BlockPos pos, BlockState state) {
        return EnvironmentalExposure.heat(world, pos, state) + neighboringHeat(world, pos) * STORED_HEAT_EVAPORATION_THRESHOLD;
    }

    private static net.minecraft.world.level.block.Block lavaContactBlock(FluidState lavaState) {
        return lavaState.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;
    }

    private static net.minecraft.world.level.block.Block lavaContactBlock(int lavaAmount) {
        return lavaAmount >= 8 ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;
    }

    private static void fizz(ServerLevel world, BlockPos pos) {
        world.levelEvent(1501, pos, 0);
    }
}
