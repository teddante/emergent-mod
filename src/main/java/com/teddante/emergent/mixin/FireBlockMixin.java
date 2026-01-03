package com.teddante.emergent.mixin;

import net.minecraft.block.FireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

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

            // Check if the target block is a container
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof LockableContainerBlockEntity container) {

                // Check if it contains explosives
                List<ItemStack> volatiles = new ArrayList<>();
                for (int i = 0; i < container.size(); i++) {
                    ItemStack stack = container.getStack(i);
                    if (VolatileExplosionUtils.isVolatile(stack)) {
                        volatiles.add(stack);
                    }
                }

                if (!volatiles.isEmpty()) {
                    float power = VolatileExplosionUtils.calculateExplosionPower(volatiles);
                    if (power > 0) {
                        // Clear items to prevent recursion
                        for (ItemStack stack : volatiles) {
                            stack.setCount(0);
                        }

                        // Create the explosion
                        world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power,
                                World.ExplosionSourceType.TNT);

                        // We destroyed the block with an explosion, so we can cancel the fire spread
                        // (or let it fail naturally, but explosion removes the block so cancel is
                        // cleaner)
                        ci.cancel();
                    }
                }
            }
        }
    }

    // Keep the existing redirect if it's still needed for other logic (age
    // handling)
    // Assuming the user wants to KEEP the previous functionality as well.
    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/FireBlock;trySpreadingFire(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/util/math/random/Random;I)V"))
    private void emergent$redirectTrySpread(FireBlock instance, World world, BlockPos pos, int spreadChance,
            Random random, int age) {
        this.trySpreadingFire(world, pos, spreadChance, random, 0);
    }
}
