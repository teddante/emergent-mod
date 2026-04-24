package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Crude structural stress: after nearby explosions or erosion, unsupported
 * stone/dirt gets downgraded (stone→cobble→gravel) and may cave in as a
 * FallingBlockEntity.
 */
public final class StructuralStress {

    public static final TagKey<Block> STRUCTURAL = TagKey.create(Registries.BLOCK,
            Identifier.parse("emergent:structural"));

    private StructuralStress() {
    }

    public static void applyAfterExplosion(ServerLevel level, List<BlockPos> affectedBlocks) {
        if (!EmergentConfig.get().structuralStress)
            return;

        // For each affected block, look at its vertical neighbors and crack
        // unsupported structural blocks above the blast.
        int count = 0;
        for (BlockPos pos : affectedBlocks) {
            if (count > 32)
                break; // cap work per tick
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos above = pos.above(dy);
                BlockState state = level.getBlockState(above);
                if (stress(level, above, state)) {
                    count++;
                    break;
                }
            }
        }
    }

    /**
     * Stress a single block: degrade it by one stage, or drop it if already gravel.
     * Returns true if anything changed.
     */
    public static boolean stress(ServerLevel level, BlockPos pos, BlockState state) {
        if (!EmergentConfig.get().structuralStress)
            return false;
        if (state.isAir())
            return false;
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN))
            return false;
        if (state.is(BlockTags.FEATURES_CANNOT_REPLACE))
            return false;

        // Only stress "structural" blocks or natural stone/dirt to avoid grief on
        // built walls of mixed materials.
        if (!state.is(STRUCTURAL) && !state.is(BlockTags.BASE_STONE_OVERWORLD)
                && !state.is(BlockTags.BASE_STONE_NETHER) && !state.is(BlockTags.DIRT)) {
            return false;
        }

        if (level.getRandom().nextFloat() > 0.35f)
            return false;

        if (state.is(Blocks.STONE) || state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.4f, 0.8f);
            return true;
        }
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) {
            level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 3);
            return true;
        }
        if (state.is(Blocks.DEEPSLATE)) {
            level.setBlock(pos, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3);
            return true;
        }
        if (state.is(Blocks.SANDSTONE)) {
            level.setBlock(pos, Blocks.SAND.defaultBlockState(), 3);
            return true;
        }
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.DIRT)) {
            // Potential cave-in — spawn a FallingBlockEntity if unsupported.
            BlockPos below = pos.below();
            if (level.getBlockState(below).isAir()) {
                FallingBlockEntity.fall(level, pos, state);
                return true;
            }
        }
        return false;
    }
}
