package com.teddante.emergent;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialPhysicsProfiles {
    private static final double DEFAULT_SOLID_ABSORPTION = 0.12;

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

    public static double dryFireExposureMultiplier(BlockState state, double moisture, double storedHeat) {
        if (!isFireReactive(state)) {
            return 1.0;
        }

        double dryness = 1.0 - Math.min(1.0, Math.max(0.0, moisture));
        double heatReadiness = Math.min(0.35, Math.max(0.0, storedHeat) * 0.05);
        return 1.0 + dryness * 0.12 + heatReadiness;
    }

    private static boolean isFireReactive(BlockState state) {
        return state.is(MaterialReactionTags.CHARS_IN_FIRE)
                || state.is(MaterialReactionTags.SCORCHES_TO_DIRT_IN_FIRE)
                || state.is(MaterialReactionTags.FLASH_BURNS_IN_FIRE)
                || state.is(MaterialReactionTags.BURNS_AWAY_IN_FIRE)
                || state.is(MaterialReactionTags.SUSTAINS_FIRE);
    }
}
