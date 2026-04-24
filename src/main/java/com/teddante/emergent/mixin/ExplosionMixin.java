package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {

    @Shadow
    public abstract ServerLevel level();

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void checkVolatileBlocks(List<BlockPos> affectedBlocks, CallbackInfo ci) {
        if (!EmergentConfig.get().volatileContainers) {
            return;
        }

        ServerLevel world = this.level();

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
            if (be instanceof RandomizableContainerBlockEntity container) {
                VolatileExplosionUtils.tryExplodeVolatileContainer(world, container, pos);
            }
        }
    }
}
