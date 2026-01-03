package com.teddante.emergent.mixin;

import net.minecraft.block.FireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.teddante.emergent.VolatileExplosionUtils;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Shadow
    protected abstract void trySpreadingFire(World world, BlockPos pos, int spreadChance, Random random, int age);

    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void checkVolatileDestruction(World world, BlockPos pos, int spreadChance, Random random, int age,
            CallbackInfo ci) {
        // If we are about to spread fire TO a block, check if that block is a volatile
        // container.
        // We only care if it's a server world (explosions are server-side).
        if (world instanceof ServerWorld) {

            // Check if the target block is a volatile container
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof LockableContainerBlockEntity container) {
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
    @ModifyArg(method = "scheduledTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/FireBlock;trySpreadingFire(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/util/math/random/Random;I)V"), index = 4)
    private int emergent$modifyFireAge(int age) {
        return 0;
    }
}
