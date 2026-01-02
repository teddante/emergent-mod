package com.teddante.emergent.mixin;

import net.minecraft.block.FireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Shadow
    protected abstract void trySpreadingFire(World world, BlockPos pos, int spreadChance, Random random, int age);

    // Redirect calls to trySpread in scheduledTick.
    // We pass '0' as the age to the spread logic, so it always spreads as if it
    // were young fire.
    // However, we allow the actual age in scheduledTick to remain unchanged, so the
    // fire can naturally burnout.
    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/FireBlock;trySpreadingFire(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/util/math/random/Random;I)V"))
    private void emergent$redirectTrySpread(FireBlock instance, World world, BlockPos pos, int spreadChance,
            Random random, int age) {
        this.trySpreadingFire(world, pos, spreadChance, random, 0);
    }
}
