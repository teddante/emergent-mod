package com.teddante.emergent.mixin;

import com.teddante.emergent.ReactiveCreeperTracker;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ReactiveCreeperMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CreeperEntity creeper) {
            // Check recursion guard
            if (creeper instanceof ReactiveCreeperTracker tracker) {
                if (tracker.emergent$isReacting()) {
                    // System.out.println("DEBUG: Recursion guard hit for creeper " +
                    // creeper.getId());
                    return; // Already exploding, ignore further damage
                }

                if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                    // System.out.println("DEBUG: Triggering reactive explosion for creeper " +
                    // creeper.getId());
                    // Set guard
                    tracker.emergent$setReacting(true);

                    try {
                        ((CreeperInvoker) creeper).invokeExplode();
                    } finally {
                        // We strictly don't need to unset it if it dies,
                        // but for safety in case it survives (unlikely), we might.
                        // However, keeping it true prevents infinite loops if it survives ticks.
                        // So we leave it true. It's a one-way trip to boom town.
                    }

                    cir.setReturnValue(false);
                }
            }
        }
    }
}
