package com.teddante.emergent.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin makes burning entities spread fire to flammable blocks they touch.
 * When a mob, player, or any entity is on fire, they have a chance to ignite
 * nearby flammable blocks, creating more emergent fire spread behavior.
 */
@Mixin(Entity.class)
public abstract class EntityFireSpreadMixin {

    @Unique
    private int emergent$fireSpreadCooldown = 0;

    /**
     * Inject at the end of the entity tick to check for fire spreading.
     * We use a cooldown to avoid spreading fire every single tick.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$spreadFireFromBurningEntity(CallbackInfo ci) {
        // Cast to Entity to access methods directly (avoids @Shadow issues with Yarn)
        Entity self = (Entity) (Object) this;
        World world = self.getEntityWorld();

        // Only run on server side and if the entity is on fire
        if (world.isClient() || !self.isOnFire()) {
            emergent$fireSpreadCooldown = 0;
            return;
        }

        // Cooldown to not spread fire every tick (roughly every 0.5-1 second)
        if (emergent$fireSpreadCooldown > 0) {
            emergent$fireSpreadCooldown--;
            return;
        }

        // Random chance to spread fire (about 15% chance when cooldown expires)
        if (world.getRandom().nextFloat() > 0.15f) {
            emergent$fireSpreadCooldown = 10; // Reset cooldown even on failed chance
            return;
        }

        // Get the entity's bounding box and check blocks around it
        Box box = self.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.ceil(box.maxX);
        int maxY = (int) Math.ceil(box.maxY);
        int maxZ = (int) Math.ceil(box.maxZ);

        FireBlock fireBlock = (FireBlock) Blocks.FIRE;
        boolean spreadFire = false;

        // Check all blocks the entity is touching
        for (int x = minX; x <= maxX && !spreadFire; x++) {
            for (int y = minY; y <= maxY && !spreadFire; y++) {
                for (int z = minZ; z <= maxZ && !spreadFire; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState stateAtPos = world.getBlockState(pos);

                    // Skip if this position already has fire or isn't air
                    if (!stateAtPos.isAir()) {
                        continue;
                    }

                    // Fire can only be placed on solid blocks or if there's a burnable neighbor
                    if (emergent$canPlaceFireAt(world, pos, self)) {
                        // Place fire at this position
                        BlockState fireState = fireBlock.getDefaultState();
                        world.setBlockState(pos, fireState, 3);
                        spreadFire = true;
                    }
                }
            }
        }

        // Reset cooldown (10-20 ticks = 0.5-1 second)
        emergent$fireSpreadCooldown = 10 + world.getRandom().nextInt(10);
    }

    /**
     * Check if fire can be placed at the given position.
     * Fire needs either a solid block below or a flammable block adjacent.
     */
    @Unique
    private boolean emergent$canPlaceFireAt(World world, BlockPos pos, Entity entity) {
        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);

        // Can place fire on solid surfaces if there's something flammable nearby
        if (belowState.isSolidSurface(world, below, entity, Direction.UP)) {
            return emergent$hasFlammableNeighbor(world, pos);
        }

        // Or if the block below is flammable
        if (belowState.isBurnable()) {
            return true;
        }

        return false;
    }

    /**
     * Check if there's a flammable block next to the given position.
     */
    @Unique
    private boolean emergent$hasFlammableNeighbor(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = world.getBlockState(pos.offset(direction));
            if (neighbor.isBurnable()) {
                return true;
            }
        }
        return false;
    }
}
