package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
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
    private static final int CHAR_WEIGHT = 35;
    private static final int SCORCH_WEIGHT = 55;
    private static final int FLASH_BURN_WEIGHT = 70;
    private static final int BURN_AWAY_WEIGHT = 50;
    private static final float WETNESS_REACTION_DAMPENING = 0.7f;
    private static final float MAX_WETNESS_DAMPENING = 0.85f;
    private static final float LIVING_SURFACE_MOISTURE = 0.35f;
    private static final float SCORCHED_SURFACE_IGNITION_CHANCE = 0.35f;
    private static final float FLASH_UPWARD_IGNITION_CHANCE = 0.6f;
    private static final float FLASH_SIDE_IGNITION_CHANCE = 0.35f;

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

    public static boolean canReactToFire(BlockState state) {
        return state.is(MaterialReactionTags.SUSTAINS_FIRE)
                || state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)
                || state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)
                || state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE)
                || state.is(MaterialReactionTags.CHARS_IN_FIRE) && CHARRED_LOGS.containsKey(state.getBlock());
    }

    public static boolean tryReactToFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        return exposeToFire(world, pos, state, 1.0f, random);
    }

    public static boolean exposeToFire(ServerLevel world, BlockPos pos, BlockState state, float heat, RandomSource random) {
        trySustainFire(world, pos, state, random);

        if (heat <= 0.0f) {
            return false;
        }

        int charWeight = state.is(MaterialReactionTags.CHARS_IN_FIRE) && CHARRED_LOGS.containsKey(state.getBlock()) ? CHAR_WEIGHT : 0;
        int scorchWeight = state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE) ? SCORCH_WEIGHT : 0;
        int flashWeight = state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE) ? FLASH_BURN_WEIGHT : 0;
        int burnAwayWeight = state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE) ? BURN_AWAY_WEIGHT : 0;
        int totalWeight = charWeight + scorchWeight + flashWeight + burnAwayWeight;
        if (totalWeight <= 0) {
            addSensibleFireHeat(world, pos, state, heat);
            return false;
        }

        float wetness = FireWetness.getWetness(world, pos);
        float dampening = Math.min(MAX_WETNESS_DAMPENING, wetness * WETNESS_REACTION_DAMPENING);
        if (dampening >= 1.0f) {
            return true;
        }

        double storedHeat = EnvironmentalExposure.heat(world, pos, state);
        double effectiveHeat = heat * (1.0f - dampening)
                * MaterialPhysicsProfiles.dryFireExposureMultiplier(state, wetness, storedHeat);
        if (state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)) {
            effectiveHeat *= 1.0f - Math.min(0.9f, wetness + LIVING_SURFACE_MOISTURE);
        }

        if (effectiveHeat <= 0.01) {
            if (random.nextFloat() < 0.05f) {
                world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 1.2f);
            }
            return true;
        }

        double exposure = EnvironmentalExposure.addHeat(world, pos, state, effectiveHeat);
        if (exposure < fireReactionThreshold(world, pos, state)) {
            return true;
        }

        EnvironmentalExposure.clearHeat(world, pos);
        return reactAfterSustainedFireExposure(world, pos, state, charWeight, scorchWeight, flashWeight, random);
    }

    private static boolean reactAfterSustainedFireExposure(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            int charWeight,
            int scorchWeight,
            int flashWeight,
            RandomSource random) {
        if (flashWeight > 0) {
            return flashBurnFromFire(world, pos, state, random);
        }
        if (charWeight > 0) {
            return charFromFire(world, pos, state);
        }
        if (scorchWeight > 0) {
            return scorchToDirtFromFire(world, pos, random);
        }

        return burnAwayFromFire(world, pos, state);
    }

    private static double fireReactionThreshold(ServerLevel world, BlockPos pos, BlockState state) {
        double threshold = 5.5;
        if (state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)) {
            threshold = 2.25;
        } else if (state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE)) {
            threshold = 4.5;
        } else if (state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)) {
            threshold = 6.5;
        } else if (state.is(MaterialReactionTags.CHARS_IN_FIRE)) {
            threshold = 8.0;
        }

        return threshold * heatThresholdVariance(world, pos, state);
    }

    private static void clearFireExposure(ServerLevel world, BlockPos pos) {
        EnvironmentalExposure.clearHeat(world, pos);
    }

    private static void addSensibleFireHeat(ServerLevel world, BlockPos pos, BlockState state, float heat) {
        if (state.getDestroySpeed(world, pos) < 0.0F) {
            return;
        }

        double sensibleHeat = MaterialPhysicsProfiles.sensibleFireHeat(state, heat);
        if (sensibleHeat <= 0.0) {
            return;
        }

        EnvironmentalExposure.addHeat(world, pos, state, sensibleHeat);
        ThermalPhysics.tryMeltFrozenSurface(world, pos, state);
    }

    private static double heatThresholdVariance(ServerLevel world, BlockPos pos, BlockState state) {
        long hash = world.getSeed();
        hash ^= pos.asLong() * 0x9E3779B97F4A7C15L;
        hash ^= (long) Block.getId(state) * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;

        double unit = (hash >>> 11) * 0x1.0p-53;
        return 0.85 + (unit * 0.3);
    }

    public static boolean tryCharFromFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.CHARS_IN_FIRE) || random.nextFloat() > 0.35f) {
            return false;
        }

        return charFromFire(world, pos, state);
    }

    private static boolean charFromFire(ServerLevel world, BlockPos pos, BlockState state) {
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

    public static boolean tryScorchToDirtFromFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)) {
            return false;
        }

        float wetness = Math.min(0.9f, FireWetness.getWetness(world, pos) + LIVING_SURFACE_MOISTURE);
        float roll = random.nextFloat();

        if (roll < wetness) {
            if (random.nextFloat() < 0.2f) {
                world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 1.2f);
            }
            return true;
        }

        BlockState scorchedState = Blocks.DIRT.defaultBlockState();
        world.setBlock(pos, scorchedState, 3);
        world.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.45f, 0.8f);

        if (random.nextFloat() < SCORCHED_SURFACE_IGNITION_CHANCE) {
            BlockPos above = pos.above();
            if (world.getBlockState(above).isAir()) {
                world.setBlock(above, BaseFireBlock.getState(world, above), 3);
            }
        }

        return true;
    }

    public static boolean tryBurnAwayFromFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE)) {
            return false;
        }

        if (FireWetness.shouldDampenIgnition(world, pos, random)) {
            return true;
        }

        return burnAwayFromFire(world, pos, state);
    }

    public static boolean tryFlashBurnFromFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)) {
            return false;
        }

        if (random.nextFloat() < FireWetness.getWetness(world, pos) * 0.75f) {
            return true;
        }

        return flashBurnFromFire(world, pos, state, random);
    }

    private static boolean scorchToDirtFromFire(ServerLevel world, BlockPos pos, RandomSource random) {
        world.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        world.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.45f, 0.8f);
        if (random.nextFloat() < SCORCHED_SURFACE_IGNITION_CHANCE) {
            BlockPos above = pos.above();
            if (world.getBlockState(above).isAir()) {
                world.setBlock(above, BaseFireBlock.getState(world, above), 3);
            }
        }
        return true;
    }

    private static boolean burnAwayFromFire(ServerLevel world, BlockPos pos, BlockState state) {
        leaveAshResidue(world, pos, state);
        world.removeBlock(pos, false);
        world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 1.6f);
        return true;
    }

    private static boolean flashBurnFromFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        leaveAshResidue(world, pos, state);
        world.removeBlock(pos, false);
        world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.8f);
        if (random.nextFloat() < FLASH_UPWARD_IGNITION_CHANCE) {
            tryPlaceFire(world, pos.above());
        }

        if (random.nextFloat() < FLASH_SIDE_IGNITION_CHANCE) {
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            tryPlaceFire(world, pos.relative(direction));
        }

        return true;
    }

    private static void leaveAshResidue(ServerLevel world, BlockPos pos, BlockState burnedState) {
        double ashKilograms = MaterialPhysicsProfiles.ashKilogramsFromBurnedBlock(burnedState);
        if (ashKilograms <= 0.0) {
            return;
        }

        BlockPos residuePos = pos.below();
        BlockState residueState = world.getBlockState(residuePos);
        if (!residueState.isAir() && residueState.getFluidState().isEmpty()) {
            EnvironmentalExposure.addAshResidue(world, residuePos, residueState, ashKilograms);
        }
    }

    public static boolean trySustainFire(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(MaterialReactionTags.SUSTAINS_FIRE)) {
            return false;
        }

        if (FireWetness.shouldDampenIgnition(world, pos, random)) {
            return true;
        }

        tryPlaceFire(world, pos.above());
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
        if (!state.is(MaterialReactionTags.RAIN_GROWS) || random.nextFloat() > rainGrowthChance(world, pos, state)) {
            return;
        }

        Block block = state.getBlock();
        if (block instanceof BonemealableBlock growable
                && growable.isValidBonemealTarget(world, pos, state)
                && growable.isBonemealSuccess(world, random, pos, state)) {
            growable.performBonemeal(world, random, pos, state);
            consumeGrowthAsh(world, pos, state);
            return;
        }

        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_1)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_2)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_3)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_4)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_5)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_7)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_15)) {
            consumeGrowthAsh(world, pos, state);
            return;
        }
        if (tryIncrementAge(world, pos, state, BlockStateProperties.AGE_25)) {
            consumeGrowthAsh(world, pos, state);
        }
    }

    public static boolean tryClimateStress(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            float biomeTemperature,
            boolean skyExposed) {
        return tryClimateStress(world, pos, state, biomeTemperature, skyExposed, 1.0);
    }

    public static boolean tryClimateStress(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            float biomeTemperature,
            boolean skyExposed,
            double climateMoistureFactor) {
        double moisture = Math.max(
                EnvironmentalExposure.moisture(world, pos, state),
                EnvironmentalExposure.moisture(world, pos.below(), world.getBlockState(pos.below())));
        double heat = Math.max(
                EnvironmentalExposure.heat(world, pos, state),
                EnvironmentalExposure.heat(world, pos.below(), world.getBlockState(pos.below())));
        double stress = MaterialPhysicsProfiles.vegetationClimateStress(state, moisture, heat, biomeTemperature, skyExposed, climateMoistureFactor);
        if (stress <= 0.0) {
            return false;
        }

        double accumulatedStress = EnvironmentalExposure.addVegetationStress(world, pos, state, stress);
        if (accumulatedStress < climateStressThreshold(world, pos, state)) {
            return false;
        }

        EnvironmentalExposure.clearVegetationStress(world, pos);
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_1)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_2)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_3)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_4)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_5)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_7)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_15)) {
            return true;
        }
        if (state.is(MaterialReactionTags.RAIN_GROWS) && tryDecrementAge(world, pos, state, BlockStateProperties.AGE_25)) {
            return true;
        }

        BlockState degradedState = MaterialPhysicsProfiles.droughtDegradedState(state);
        if (degradedState == null) {
            return false;
        }

        if (degradedState.canSurvive(world, pos)) {
            world.setBlock(pos, degradedState, 3);
        } else {
            world.removeBlock(pos, false);
        }
        world.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.35f, 0.8f);
        return true;
    }

    private static double climateStressThreshold(ServerLevel world, BlockPos pos, BlockState state) {
        return MaterialPhysicsProfiles.vegetationStressThreshold(state) * heatThresholdVariance(world, pos, state);
    }

    private static float rainGrowthChance(ServerLevel world, BlockPos pos, BlockState state) {
        double bonus = EnvironmentalExposure.ashGrowthBonus(world, pos, state);
        BlockPos belowPos = pos.below();
        bonus = Math.max(bonus, EnvironmentalExposure.ashGrowthBonus(world, belowPos, world.getBlockState(belowPos)));
        return (float) Math.min(0.28, 0.12 + bonus);
    }

    private static void consumeGrowthAsh(ServerLevel world, BlockPos pos, BlockState state) {
        EnvironmentalExposure.consumeAshResidue(world, pos, state, 0.2);
        BlockPos belowPos = pos.below();
        EnvironmentalExposure.consumeAshResidue(world, belowPos, world.getBlockState(belowPos), 0.2);
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

    private static boolean tryDecrementAge(ServerLevel world, BlockPos pos, BlockState state, IntegerProperty property) {
        if (!state.hasProperty(property)) {
            return false;
        }

        int age = state.getValue(property);
        if (age <= 0) {
            return false;
        }

        world.setBlock(pos, state.setValue(property, age - 1), 3);
        return true;
    }

    private static boolean tryPlaceFire(ServerLevel world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }

        BlockState fireState = BaseFireBlock.getState(world, pos);
        if (!fireState.canSurvive(world, pos)) {
            return false;
        }

        world.setBlock(pos, fireState, 3);
        return true;
    }
}
