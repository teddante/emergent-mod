package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Map;

public final class MaterialReactions {
    private static final Map<Block, Block> CHARRED_LOGS = Map.ofEntries(
            Map.entry(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG),
            Map.entry(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD),
            Map.entry(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG),
            Map.entry(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD),
            Map.entry(Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG),
            Map.entry(Blocks.PALE_OAK_WOOD, Blocks.STRIPPED_PALE_OAK_WOOD),
            Map.entry(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG),
            Map.entry(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD),
            Map.entry(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG),
            Map.entry(Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD),
            Map.entry(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG),
            Map.entry(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD),
            Map.entry(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG),
            Map.entry(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD),
            Map.entry(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG),
            Map.entry(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD),
            Map.entry(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG),
            Map.entry(Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_WOOD),
            Map.entry(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM),
            Map.entry(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE),
            Map.entry(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM),
            Map.entry(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE),
            Map.entry(Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK));

    private MaterialReactions() {
    }

    public static boolean tryCharFromFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.CHARS_IN_FIRE) || random.nextFloat() > 0.35f) {
            return false;
        }

        Block charredBlock = CHARRED_LOGS.get(state.getBlock());
        if (charredBlock == null) {
            return false;
        }

        BlockState charredState = charredBlock.defaultBlockState();
        if (state.hasProperty(RotatedPillarBlock.AXIS) && charredState.hasProperty(RotatedPillarBlock.AXIS)) {
            charredState = charredState.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }

        world.setBlock(pos, charredState, 3);
        world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 0.8f);
        return true;
    }

    public static void shortConductiveNeighbors(ServerLevel world, BlockPos waterPos, RandomSource random) {
        for (Direction direction : Direction.values()) {
            BlockPos pos = waterPos.relative(direction);
            BlockState state = world.getBlockState(pos);
            if (!state.is(MaterialReactionTags.CONDUCTIVE)) {
                continue;
            }

            if (state.hasProperty(RedStoneWireBlock.POWER) && state.getValue(RedStoneWireBlock.POWER) > 0) {
                world.setBlock(pos, state.setValue(RedStoneWireBlock.POWER, 0), 3);
            }

            world.updateNeighborsAt(pos, state.getBlock());
            if (random.nextFloat() < 0.25f) {
                world.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.25f, 1.5f);
            }
        }
    }

    public static void tryRainOxidize(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.RAIN_OXIDIZES) || random.nextFloat() > 0.08f) {
            return;
        }

        WeatheringCopper.getNext(state.getBlock())
                .map(block -> block.withPropertiesOf(state))
                .ifPresent(next -> world.setBlock(pos, next, 3));
    }

    public static void tryRainGrow(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.RAIN_GROWS) || random.nextFloat() > 0.12f) {
            return;
        }

        Block block = state.getBlock();
        if (block instanceof BonemealableBlock growable
                && growable.isValidBonemealTarget(world, pos, state)
                && growable.isBonemealSuccess(world, random, pos, state)) {
            growable.performBonemeal(world, random, pos, state);
            return;
        }

        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_1)) {
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_2)) {
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_3)) {
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_4)) {
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_5)) {
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_7)) {
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_15)) {
            return;
        }
        tryIncrementAge(world, pos, state, BlockStateProperties.AGE_25);
    }

    private static boolean tryIncrementAge(ServerLevel world, BlockPos pos, BlockState state, IntegerProperty property) {
        if (!state.hasProperty(property)) {
            return false;
        }

        int age = state.getValue(property);
        int maxAge = property.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
        if (age >= maxAge) {
            return false;
        }

        world.setBlock(pos, state.setValue(property, age + 1), 3);
        return true;
    }
}
