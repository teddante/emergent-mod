package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

public final class SurfaceWeatherPhysics {
    private static final double RAIN_DEEPEN_CHANCE = 0.04;
    private static final double RAIN_PUDDLE_CHANCE = 0.0125;
    private static final int RAIN_PUDDLE_AMOUNT = 1;
    private static final double ABSORBENT_SURFACE_FACTOR = 0.25;

    private SurfaceWeatherPhysics() {
    }

    public static void processWeatherSample(ServerLevel world, BlockPos samplePos, int samples) {
        if (samples <= 0 || !EmergentConfig.get().rainAccumulation) {
            return;
        }

        BlockPos topPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, samplePos);
        BlockPos surfacePos = topPos.below();
        Holder<Biome> biomeHolder = world.getBiome(topPos);
        Biome biome = biomeHolder.value();
        double climateMoistureFactor = climateMoistureFactor(biomeHolder);
        BlockState surfaceState = world.getBlockState(surfacePos);
        BlockState state = world.getBlockState(topPos);
        boolean skyExposed = world.canSeeSky(topPos);

        if (!world.isRaining()) {
            EnvironmentalExposure.applyAmbientSurfaceExchange(
                    world,
                    surfacePos,
                    surfaceState,
                    biome.getBaseTemperature(),
                    skyExposed,
                    ThermalPhysics.neighboringHeat(world, surfacePos),
                    climateMoistureFactor,
                    world.isBrightOutside() ? 1.0 : 0.0,
                    surfaceAirExposureFactor(world, surfacePos, skyExposed),
                    samples);
            ThermalPhysics.tryFreezeMoistSurface(world, surfacePos, world.getBlockState(surfacePos));
            ThermalPhysics.tryMeltFrozenSurface(world, surfacePos, world.getBlockState(surfacePos));
            if (EmergentConfig.get().materialReactions) {
                tryClimateStressExposedBlock(world, topPos, surfacePos, state, surfaceState, biome.getBaseTemperature(), skyExposed, climateMoistureFactor);
            }
            return;
        }

        Biome.Precipitation precipitation = biome.getPrecipitationAt(surfacePos, world.getSeaLevel());
        if (precipitation == Biome.Precipitation.SNOW) {
            EnvironmentalExposure.addSnowfall(world, surfacePos, surfaceState, samples);
            ThermalPhysics.tryFreezeMoistSurface(world, surfacePos, world.getBlockState(surfacePos));
            return;
        }
        if (precipitation != Biome.Precipitation.RAIN) {
            return;
        }

        EnvironmentalExposure.addRainfall(world, surfacePos, surfaceState, climateMoistureFactor, samples);
        surfaceState = world.getBlockState(surfacePos);
        ThermalPhysics.tryMeltFrozenSurface(world, surfacePos, surfaceState);
        surfaceState = world.getBlockState(surfacePos);

        if (surfaceState.is(Blocks.WATER)) {
            tryDeepenRainWater(world, surfacePos, surfaceState, samples);
        } else if (state.isAir() && canRainCollectOn(world, surfacePos, surfaceState)) {
            tryCreateRainPuddle(world, topPos, surfacePos, surfaceState, samples);
        }

        if (EmergentConfig.get().materialReactions) {
            MaterialReactions.tryRainOxidize(
                    world,
                    surfacePos,
                    surfaceState,
                    world.getRandom(),
                    EnvironmentalScheduler.probabilityOverSamples(0.08, samples));
            tryRainGrowExposedBlock(world, topPos, surfacePos, state, surfaceState, samples);
        }
    }

    private static void tryDeepenRainWater(ServerLevel world, BlockPos pos, BlockState state, int samples) {
        if (world.getRandom().nextDouble() >= EnvironmentalScheduler.probabilityOverSamples(RAIN_DEEPEN_CHANCE, samples)) {
            return;
        }

        int currentLevel = state.getValue(LiquidBlock.LEVEL);
        if (currentLevel <= 0) {
            return;
        }

        world.setBlock(pos, state.setValue(LiquidBlock.LEVEL, currentLevel - 1), 3);
        EnvironmentalExposure.applyStandingWaterContact(world, pos, world.getFluidState(pos).getAmount());
        world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
    }

    private static void tryCreateRainPuddle(ServerLevel world, BlockPos pos, BlockPos surfacePos, BlockState surfaceState, int samples) {
        double readiness = EnvironmentalExposure.rainPuddleReadiness(
                surfaceState,
                EnvironmentalExposure.moisture(world, surfacePos, surfaceState));
        if (readiness <= 0.0) {
            return;
        }

        double chance = RAIN_PUDDLE_CHANCE * surfaceAbsorptionFactor(surfaceState) * Math.max(0.35, readiness);
        if (world.getRandom().nextDouble() >= EnvironmentalScheduler.probabilityOverSamples(chance, samples)) {
            return;
        }
        if (!EnvironmentalExposure.tryReleaseRainPuddleMoisture(world, surfacePos, surfaceState, RAIN_PUDDLE_AMOUNT)) {
            return;
        }

        world.setBlock(pos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 3);
        EnvironmentalExposure.applyStandingWaterContact(world, pos, world.getFluidState(pos).getAmount());
        world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
    }

    private static boolean canRainCollectOn(ServerLevel world, BlockPos surfacePos, BlockState surfaceState) {
        if (surfaceState.isAir() || !surfaceState.getFluidState().isEmpty()) {
            return false;
        }
        if (surfaceState.is(BlockTags.LEAVES) || surfaceState.is(BlockTags.LOGS)) {
            return false;
        }

        return surfaceState.isFaceSturdy(world, surfacePos, Direction.UP);
    }

    private static double surfaceAbsorptionFactor(BlockState surfaceState) {
        if (surfaceState.is(BlockTags.DIRT)
                || surfaceState.is(BlockTags.GRASS_BLOCKS)
                || surfaceState.is(BlockTags.MUD)
                || surfaceState.is(BlockTags.SAND)) {
            return ABSORBENT_SURFACE_FACTOR;
        }

        return 1.0;
    }

    private static double surfaceAirExposureFactor(ServerLevel world, BlockPos surfacePos, boolean skyExposed) {
        int openHorizontalFaces = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState adjacentState = world.getBlockState(surfacePos.relative(direction));
            if (adjacentState.isAir() || !adjacentState.getFluidState().isEmpty()) {
                openHorizontalFaces++;
            }
        }

        return EnvironmentalExposure.airExposureFactor(skyExposed, openHorizontalFaces);
    }

    private static double climateMoistureFactor(Holder<Biome> biome) {
        double factor = 1.0;
        if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)) {
            factor *= 1.35;
        } else if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) {
            factor *= 1.15;
        }

        if (biome.is(BiomeTags.IS_BADLANDS)
                || biome.is(BiomeTags.IS_SAVANNA)
                || biome.is(BiomeTags.HAS_DESERT_PYRAMID)
                || biome.is(BiomeTags.HAS_VILLAGE_DESERT)
                || biome.is(BiomeTags.IS_NETHER)) {
            factor *= 0.55;
        }

        return EnvironmentalExposure.climateMoistureFactor(factor);
    }

    private static void tryRainGrowExposedBlock(
            ServerLevel world,
            BlockPos topPos,
            BlockPos surfacePos,
            BlockState topState,
            BlockState surfaceState,
            int samples) {
        if (tryRainGrowAt(world, topPos, topState, samples)) {
            return;
        }

        tryRainGrowAt(world, surfacePos, surfaceState, samples);
    }

    private static boolean tryRainGrowAt(ServerLevel world, BlockPos pos, BlockState state, int samples) {
        if (!state.is(MaterialReactionTags.RAIN_GROWS)) {
            return false;
        }

        MaterialReactions.tryRainGrow(
                world,
                pos,
                state,
                world.getRandom(),
                EnvironmentalScheduler.probabilityOverSamples(MaterialReactions.rainGrowthChance(world, pos, state), samples));
        return true;
    }

    private static void tryClimateStressExposedBlock(
            ServerLevel world,
            BlockPos topPos,
            BlockPos surfacePos,
            BlockState topState,
            BlockState surfaceState,
            float biomeTemperature,
            boolean skyExposed,
            double climateMoistureFactor) {
        if (tryClimateStressAt(world, topPos, topState, biomeTemperature, skyExposed, climateMoistureFactor)) {
            return;
        }

        tryClimateStressAt(world, surfacePos, surfaceState, biomeTemperature, skyExposed, climateMoistureFactor);
    }

    private static boolean tryClimateStressAt(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            float biomeTemperature,
            boolean skyExposed,
            double climateMoistureFactor) {
        return MaterialReactions.tryClimateStress(world, pos, state, biomeTemperature, skyExposed, climateMoistureFactor);
    }
}
