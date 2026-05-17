package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Local heat-transfer rules for interactions that Minecraft already models as
 * block-scale events: lava solidifying, water quenching, and shallow puddles
 * evaporating near heat.
 */
public final class ThermalPhysics {
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

        int heat = neighboringHeat(world, pos);
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
