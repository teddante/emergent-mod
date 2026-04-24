package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ReactiveCreeperTracker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ReactiveCreeperMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onDamage(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (EmergentConfig.get().reactiveCreepers && (Object) this instanceof Creeper creeper) {
            // Check recursion guard
            if (creeper instanceof ReactiveCreeperTracker tracker) {
                if (tracker.emergent$isReacting()) {
                    return; // Already exploding, ignore further damage
                }

                if (source.is(DamageTypeTags.IS_EXPLOSION)) {
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
