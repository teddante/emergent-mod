package com.teddante.emergent.mixin;

import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionMixin {

    @Shadow
    public abstract ServerWorld getWorld();

    @Inject(method = "destroyBlocks", at = @At("HEAD"))
    private void checkVolatileBlocks(List<BlockPos> affectedBlocks, CallbackInfo ci) {
        World world = this.getWorld();

        // The original code had a copy of affectedBlocks, which might be necessary
        // if the list is modified during iteration or if new explosions affect the same
        // list.
        // For now, we'll iterate directly over the provided list.
        // If concurrent modification issues arise, a copy should be made.

        // Create a copy of the list to prevent ConcurrentModificationException
        // if the recursive explosion modifies the original list within the same tick.
        List<BlockPos> affectedBlocksCopy = new ArrayList<>(affectedBlocks);

        for (BlockPos pos : affectedBlocksCopy) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof LockableContainerBlockEntity container) {
                VolatileExplosionUtils.tryExplodeVolatileContainer(world, container, pos);
            }
        }
    }
}
