package com.teddante.emergent;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialPhysicsProfiles {
    private static final double DEFAULT_SOLID_ABSORPTION = 0.12;
    private static final double GAME_MASS_KG_SCALE = 0.01;
    private static final double CHARRED_SURFACE_DEPTH_METERS = 0.02;
    private static final double WOOD_SURFACE_ASH_YIELD_FRACTION = 0.05;

    private MaterialPhysicsProfiles() {
    }

    public static double surfaceWaterAbsorption(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 0.0;
        }
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(BlockTags.MUD)) {
            return 0.85;
        }
        if (state.is(BlockTags.SAND)) {
            return 0.55;
        }
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return 0.35;
        }
        if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK)) {
            return 0.8;
        }
        if (state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.OBSIDIAN)) {
            return 0.06;
        }

        return DEFAULT_SOLID_ABSORPTION;
    }

    public static double dryingExposure(BlockState state) {
        return 1.0 - surfaceWaterAbsorption(state) * 0.35;
    }

    public static double thermalConductivity(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 0.0;
        }
        if (state.is(MaterialReactionTags.CONDUCTIVE)
                || state.is(Blocks.IRON_BLOCK)
                || state.is(Blocks.COPPER_BLOCK)
                || state.is(Blocks.EXPOSED_COPPER)
                || state.is(Blocks.WEATHERED_COPPER)
                || state.is(Blocks.OXIDIZED_COPPER)
                || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.LIGHTNING_ROD)) {
            return 1.0;
        }
        if (state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.MAGMA_BLOCK)) {
            return 0.35;
        }
        if (state.is(Blocks.GLASS) || state.is(Blocks.TINTED_GLASS) || state.is(MaterialReactionTags.BRITTLE)) {
            return 0.25;
        }
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(BlockTags.SAND) || state.is(BlockTags.MUD)) {
            return 0.18;
        }
        if (state.is(BlockTags.LOGS)) {
            return 0.08;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 0.04;
        }

        return 0.12;
    }

    public static double densityKilogramsPerCubicMeter(BlockState state) {
        if (state.is(BlockTags.ANVIL)) {
            return 7_800.0;
        }
        if (state.is(BlockTags.LOGS)) {
            return 650.0;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 120.0;
        }
        if (state.is(BlockTags.SAND)) {
            return 1_600.0;
        }
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(BlockTags.MUD)) {
            return 1_450.0;
        }
        if (state.is(Blocks.GLASS) || state.is(Blocks.TINTED_GLASS)) {
            return 2_500.0;
        }
        if (state.is(Blocks.OBSIDIAN)) {
            return 2_600.0;
        }
        if (state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.TUFF)) {
            return 2_700.0;
        }

        return 1_200.0 + Math.sqrt(Math.max(0.0F, state.getBlock().getExplosionResistance())) * 120.0;
    }

    public static double blockMassKilograms(BlockState state) {
        return densityKilogramsPerCubicMeter(state) * EnvironmentalExposure.BLOCK_VOLUME_CUBIC_METERS;
    }

    public static double blockGameMass(BlockState state) {
        return blockMassKilograms(state) * GAME_MASS_KG_SCALE;
    }

    public static double structuralStressThreshold(BlockState state) {
        double resistance = Math.max(0.1, state.getBlock().getExplosionResistance());
        double brittleness = state.is(MaterialReactionTags.BRITTLE) ? 0.45 : 1.0;
        return resistance * brittleness;
    }

    public static double thermalShockStress(BlockState state, double removedHeat) {
        if (removedHeat <= 0.0 || state.isAir() || !state.getFluidState().isEmpty()) {
            return 0.0;
        }

        double threshold = structuralStressThreshold(state);
        if (state.is(MaterialReactionTags.BRITTLE)) {
            return threshold * removedHeat * 0.45;
        }
        if (state.is(MaterialReactionTags.ERODES_IN_WATER)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)) {
            return threshold * removedHeat * 0.18;
        }

        return 0.0;
    }

    public static BlockState thermalFractureState(BlockState state) {
        if (state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (state.is(Blocks.DEEPSLATE)) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (state.is(Blocks.SANDSTONE)) {
            return Blocks.SAND.defaultBlockState();
        }
        if (state.is(Blocks.RED_SANDSTONE)) {
            return Blocks.RED_SAND.defaultBlockState();
        }
        if (state.is(Blocks.CALCITE) || state.is(Blocks.TUFF) || state.is(Blocks.DRIPSTONE_BLOCK)) {
            return Blocks.GRAVEL.defaultBlockState();
        }

        return null;
    }

    public static double sedimentKilogramsFromErodedBlock(BlockState state, double energy) {
        double detachedVolume = Math.min(0.08, Math.max(0.0, energy) * 0.0025);
        if (state.is(MaterialReactionTags.WASHES_AWAY_IN_WATER)) {
            detachedVolume *= 2.0;
        }

        return densityKilogramsPerCubicMeter(state) * detachedVolume;
    }

    public static BlockState sedimentDepositState(double sedimentKilograms) {
        if (sedimentKilograms >= 120.0) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (sedimentKilograms >= 60.0) {
            return Blocks.MUD.defaultBlockState();
        }

        return Blocks.DIRT.defaultBlockState();
    }

    public static double ashKilogramsFromBurnedBlock(BlockState state) {
        if (state.is(BlockTags.LEAVES)) {
            return 0.35;
        }
        if (state.is(BlockTags.LOGS)) {
            return 1.2;
        }
        if (state.is(BlockTags.CROPS)) {
            return 0.45;
        }
        if (state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE) || state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)) {
            return 0.25;
        }

        return 0.0;
    }

    public static double ashKilogramsFromCharredSurface(BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return 0.0;
        }

        return densityKilogramsPerCubicMeter(state)
                * EnvironmentalExposure.BLOCK_VOLUME_CUBIC_METERS
                * CHARRED_SURFACE_DEPTH_METERS
                * WOOD_SURFACE_ASH_YIELD_FRACTION;
    }

    public static double dryFireExposureMultiplier(BlockState state, double moisture, double storedHeat) {
        if (!isFireReactive(state)) {
            return 1.0;
        }

        double dryness = 1.0 - Math.min(1.0, Math.max(0.0, moisture));
        double heatReadiness = Math.min(0.35, Math.max(0.0, storedHeat) * 0.05);
        return 1.0 + dryness * 0.12 + heatReadiness;
    }

    public static double sensibleFireHeat(BlockState state, double contactHeat) {
        if (contactHeat <= 0.0 || state.isAir() || !state.getFluidState().isEmpty()) {
            return 0.0;
        }

        double referenceDensityKilogramsPerCubicMeter = 1_000.0;
        return contactHeat * referenceDensityKilogramsPerCubicMeter / densityKilogramsPerCubicMeter(state);
    }

    public static double vegetationClimateStress(BlockState state, double moisture, double heat, float biomeTemperature, boolean skyExposed) {
        return vegetationClimateStress(state, moisture, heat, biomeTemperature, skyExposed, 1.0);
    }

    public static double vegetationClimateStress(
            BlockState state,
            double moisture,
            double heat,
            float biomeTemperature,
            boolean skyExposed,
            double climateMoistureFactor) {
        if (!isVegetation(state)) {
            return 0.0;
        }

        double dryness = Math.max(0.0, 1.0 - moisture);
        double dryAirMultiplier = 1.0 / EnvironmentalExposure.climateMoistureFactor(climateMoistureFactor);
        double heatPressure = Math.max(0.0, heat * 0.35 + Math.max(0.0F, biomeTemperature - 0.8F) * 0.8) * dryAirMultiplier;
        double exposure = skyExposed ? 1.0 : 0.45;
        double plantSensitivity = state.is(BlockTags.CROPS) || state.is(MaterialReactionTags.RAIN_GROWS) ? 1.2 : 1.0;
        if (state.is(BlockTags.LEAVES)) {
            plantSensitivity = 0.55;
        }

        double stress = dryness * heatPressure * exposure * plantSensitivity;
        return stress < 0.03 ? 0.0 : stress;
    }

    public static double vegetationStressThreshold(BlockState state) {
        if (state.is(BlockTags.CROPS) || state.is(MaterialReactionTags.RAIN_GROWS)) {
            return 0.85;
        }
        if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK) || state.is(BlockTags.GRASS_BLOCKS)) {
            return 1.15;
        }
        if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES)) {
            return 0.7;
        }
        if (state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE) || state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)) {
            return 0.75;
        }

        return 1.0;
    }

    public static BlockState droughtDegradedState(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (state.is(BlockTags.CROPS) || state.is(MaterialReactionTags.RAIN_GROWS)) {
            return Blocks.DEAD_BUSH.defaultBlockState();
        }
        if (state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
            return Blocks.DEAD_BUSH.defaultBlockState();
        }
        if (state.is(BlockTags.LEAVES)) {
            return Blocks.LEAF_LITTER.defaultBlockState();
        }

        return null;
    }

    private static boolean isFireReactive(BlockState state) {
        return state.is(MaterialReactionTags.CHARS_IN_FIRE)
                || state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)
                || state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)
                || state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE)
                || state.is(MaterialReactionTags.SUSTAINS_FIRE);
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.CROPS)
                || state.is(BlockTags.GRASS_BLOCKS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(MaterialReactionTags.RAIN_GROWS)
                || state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)
                || state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE)
                || state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE);
    }
}
