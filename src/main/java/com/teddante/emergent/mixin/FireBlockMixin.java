package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.MaterialReactions;
import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void checkVolatileDestruction(Level world, BlockPos pos, int spreadChance, RandomSource random, int age,
            CallbackInfo ci) {
        // If we are about to spread fire TO a block, check if that block is a volatile
        // container.
        // We only care if it's a server world (explosions are server-side).
        if (world instanceof ServerLevel serverWorld && EmergentConfig.get().materialReactions
                && MaterialReactions.tryCharFromFire(serverWorld, pos, world.getBlockState(pos), random)) {
            ci.cancel();
            return;
        }

        if (EmergentConfig.get().volatileContainers && world instanceof ServerLevel) {

            // Check if the target block is a volatile container
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof Container container) {
                if (VolatileExplosionUtils.tryExplodeVolatileContainer(world, container, pos)) {
                    // We destroyed the block with an explosion, cancel the fire spread
                    ci.cancel();
                }
            }
        }
    }

    // Use @ModifyArg instead of @Redirect for better mod compatibility.
    // This only modifies the 'age' parameter (index 4) to always be 0,
    // allowing fire to spread indefinitely regardless of its current age.
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FireBlock;checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V"), index = 4)
    private int emergent$modifyFireAge(int age) {
        return EmergentConfig.get().infiniteFireSpread ? 0 : age;
    }
}
