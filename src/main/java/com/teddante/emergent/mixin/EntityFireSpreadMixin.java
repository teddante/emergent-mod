package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin makes burning entities spread fire to flammable blocks they touch.
 * Burning entities leave fire trails as they move and ignite nearby flammable
 * blocks.
 */
@Mixin(Entity.class)
public abstract class EntityFireSpreadMixin {

    /**
     * Inject at the end of the entity tick to check for fire spreading.
     * Creates visible fire trails behind burning entities.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$spreadFireFromBurningEntity(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level world = self.level();

        // Only run on server side and if the entity is on fire
        if (!EmergentConfig.get().burningEntityFireSpread || world.isClientSide() || !self.isOnFire()) {
            return;
        }

        // 50% chance per tick to attempt fire spread - creates consistent trails
        if (world.getRandom().nextFloat() > 0.50f) {
            return;
        }

        // PRIORITY 1: Try to place fire at the entity's feet (creates fire trails)
        BlockPos feetPos = self.blockPosition();
        if (emergent$tryPlaceFireAt(world, feetPos, self)) {
            // Successfully placed fire at feet, also try adjacent positions for wider trail
            for (Direction horizontal : Direction.Plane.HORIZONTAL) {
                if (world.getRandom().nextFloat() < 0.3f) { // 30% chance for each adjacent
                    emergent$tryPlaceFireAt(world, feetPos.relative(horizontal), self);
                }
            }
            return;
        }

        // PRIORITY 2: Check one block above feet (for when standing in grass/flowers)
        BlockPos aboveFeet = feetPos.above();
        if (emergent$tryPlaceFireAt(world, aboveFeet, self)) {
            return;
        }

        // PRIORITY 3: Scan the entity's bounding box for any valid fire positions
        AABB box = self.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.ceil(box.maxX);
        int maxY = (int) Math.ceil(box.maxY);
        int maxZ = (int) Math.ceil(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (emergent$tryPlaceFireAt(world, pos, self)) {
                        return; // Only place one fire per tick from bounding box scan
                    }
                }
            }
        }
    }

    /**
     * Attempt to place fire at a position. Returns true if fire was placed.
     */
    @Unique
    private boolean emergent$tryPlaceFireAt(Level world, BlockPos pos, Entity entity) {
        BlockState stateAtPos = world.getBlockState(pos);

        // Must be air to place fire
        if (!stateAtPos.isAir()) {
            return false;
        }

        // Check if fire can exist here
        if (!emergent$canPlaceFireAt(world, pos, entity)) {
            return false;
        }

        // Place the same fire state vanilla would choose for this position.
        world.setBlock(pos, BaseFireBlock.getState(world, pos), 3);
        return true;
    }

    /**
     * Check if fire can be placed at the given position.
     * Fire needs either a solid block below or a flammable block adjacent.
     */
    @Unique
    private boolean emergent$canPlaceFireAt(Level world, BlockPos pos, Entity entity) {
        BlockPos below = pos.below();
        BlockState belowState = world.getBlockState(below);

        // Can place fire on top of flammable blocks (wood, leaves, wool, etc.)
        if (belowState.ignitedByLava()) {
            return true;
        }

        // Can place fire on solid surfaces if there's something flammable nearby
        if (belowState.entityCanStandOnFace(world, below, entity, Direction.UP)) {
            return emergent$hasFlammableNeighbor(world, pos);
        }

        return false;
    }

    /**
     * Check if there's a flammable block next to the given position.
     */
    @Unique
    private boolean emergent$hasFlammableNeighbor(Level world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = world.getBlockState(pos.relative(direction));
            if (neighbor.ignitedByLava()) {
                return true;
            }
        }
        return false;
    }
}
