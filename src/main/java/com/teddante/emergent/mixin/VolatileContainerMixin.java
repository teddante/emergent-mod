package com.teddante.emergent.mixin;

import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(net.minecraft.block.Block.class)
public class VolatileContainerMixin {

    // Updated signature to use ServerWorld as per crash log expectation
    // (class_3218)
    @Inject(method = "onDestroyedByExplosion", at = @At("HEAD"))
    private void checkVolatileContents(ServerWorld world, BlockPos pos, Explosion explosion, CallbackInfo ci) {
        // ServerWorld implies !isClient, so we might not need the check, but keeping it
        // for safety/consistency is fine.
        // Actually ServerWorld IS the server side.

        // System.out.println("DEBUG: onDestroyedByExplosion at " + pos);
        // Log unconditionally to see what is happening
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        System.out.println("DEBUG: Block is " + state.getBlock().getClass().getName());

        BlockEntity be = world.getBlockEntity(pos);
        System.out.println("DEBUG: BE is " + (be == null ? "null" : be.getClass().getName()));

        if (be instanceof LockableContainerBlockEntity container) {
            System.out.println(
                    "DEBUG: Found Container BE: " + be.getClass().getName() + " with size " + container.size());
            List<ItemStack> volatiles = new ArrayList<>();

            for (int i = 0; i < container.size(); i++) {
                ItemStack stack = container.getStack(i);
                if (VolatileExplosionUtils.isVolatile(stack)) {
                    volatiles.add(stack);
                }
            }

            if (!volatiles.isEmpty()) {
                float power = VolatileExplosionUtils.calculateExplosionPower(volatiles);
                System.out.println("DEBUG: Found volatiles. Count: " + volatiles.size() + ", Power: " + power);

                if (power > 0) {
                    // Prevent recursion: Remove items BEFORE the explosion
                    for (ItemStack stack : volatiles) {
                        stack.setCount(0);
                    }
                    System.out.println("DEBUG: Creating container explosion...");
                    world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power,
                            World.ExplosionSourceType.TNT);
                }
            }
        }
    }
}
