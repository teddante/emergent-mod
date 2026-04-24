package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FireEcology {

    public static final TagKey<net.minecraft.world.level.block.Block> ASH_FERTILE = TagKey.create(Registries.BLOCK,
            Identifier.parse("emergent:ash_fertile"));

    private FireEcology() {
    }

    /**
     * Called when fire is about to consume a flammable block. Replaces certain
     * blocks with "ashed" equivalents and drops charcoal for logs.
     *
     * Returns true if the ecology system handled the destruction (caller should
     * skip vanilla removal).
     */
    public static boolean onFireConsumes(Level level, BlockPos pos, BlockState consumed) {
        if (!EmergentConfig.get().fireEcology)
            return false;
        if (!(level instanceof ServerLevel server))
            return false;

        // Logs / wood → drop charcoal and leave air (vanilla would also leave air, but
        // the charcoal drop is the flavor add).
        if (consumed.is(BlockTags.LOGS) || consumed.is(BlockTags.PLANKS)) {
            if (server.getRandom().nextFloat() < 0.4f) {
                ItemEntity drop = new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(Items.CHARCOAL));
                drop.setDefaultPickUpDelay();
                server.addFreshEntity(drop);
            }
            // Fall through — vanilla will turn it to air anyway.
            return false;
        }

        // Grass / dirt surfaces that are scorched → coarse dirt (ash-fertile).
        if (consumed.is(Blocks.GRASS_BLOCK) || consumed.is(Blocks.DIRT) || consumed.is(Blocks.PODZOL)
                || consumed.is(Blocks.MYCELIUM)) {
            server.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
            return true;
        }

        // Leaves → small chance of stick drop (charred branches).
        if (consumed.is(BlockTags.LEAVES)) {
            if (server.getRandom().nextFloat() < 0.15f) {
                ItemEntity drop = new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(Items.STICK));
                drop.setDefaultPickUpDelay();
                server.addFreshEntity(drop);
            }
            return false;
        }

        return false;
    }

    /**
     * Called during rain precipitation ticks — ash-fertile soil slowly regenerates
     * into fertile dirt and occasionally re-seeds with grass or a sapling.
     */
    public static void tickRainOnAshes(ServerLevel level, BlockPos topPos) {
        if (!EmergentConfig.get().fireEcology)
            return;
        BlockPos surface = topPos.below();
        BlockState surfaceState = level.getBlockState(surface);
        if (!surfaceState.is(ASH_FERTILE))
            return;

        // Slowly turn coarse_dirt back into dirt.
        if (surfaceState.is(Blocks.COARSE_DIRT) && level.getRandom().nextInt(200) == 0) {
            level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }

        // On plain dirt next to grass, small chance for grass to spread.
        if (surfaceState.is(Blocks.DIRT)) {
            for (int i = 0; i < 4; i++) {
                int dx = level.getRandom().nextInt(3) - 1;
                int dz = level.getRandom().nextInt(3) - 1;
                BlockPos neighbor = surface.offset(dx, 0, dz);
                if (level.getBlockState(neighbor).is(Blocks.GRASS_BLOCK)) {
                    if (level.getRandom().nextInt(10) == 0) {
                        level.setBlock(surface, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    }
                    break;
                }
            }
        }
    }
}
