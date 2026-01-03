package com.teddante.emergent.mixin;

import com.teddante.emergent.Emergent;
import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Makes dropped explosive items (ItemEntity) react to fire, lava, and
 * explosions.
 * 
 * When a volatile item takes fire/explosion/lava damage, it detonates with
 * power
 * proportional to the stack size.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityVolatileMixin {

    @Shadow
    public abstract ItemStack getStack();

    /**
     * Override explosion immunity for volatile items.
     * 
     * Vanilla ItemEntity returns true (immune) by default. We make volatile items
     * vulnerable so they can be triggered by nearby explosions.
     */
    @Inject(method = "isImmuneToExplosion", at = @At("HEAD"), cancellable = true)
    private void makeVolatilesExplosionSensitive(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        if (VolatileExplosionUtils.isVolatile(this.getStack())) {
            cir.setReturnValue(false); // Not immune - can be damaged by explosions
        }
    }

    /**
     * Trigger explosion when a volatile item takes fire, lava, or explosion damage.
     * 
     * This hooks into the vanilla damage method which is called by:
     * - Fire tick damage (Entity.baseTick)
     * - Lava damage (Entity.setOnFireFromLava)
     * - Explosion damage (when not immune)
     */
    @Inject(method = "damage", at = @At("HEAD"))
    private void explodeOnDamage(ServerWorld world, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {

        boolean isFire = source.isIn(DamageTypeTags.IS_FIRE);
        boolean isExplosion = source.isIn(DamageTypeTags.IS_EXPLOSION);

        if (isFire || isExplosion) {
            ItemEntity self = (ItemEntity) (Object) this;
            ItemStack stack = self.getStack();

            if (VolatileExplosionUtils.isVolatile(stack)) {
                float power = VolatileExplosionUtils.calculateExplosionPower(List.of(stack));

                if (power > 0) {
                    // Clear stack BEFORE exploding to prevent recursion
                    // (explosion -> damage -> check volatile -> explosion)
                    int count = stack.getCount();
                    stack.setCount(0);
                    self.discard();

                    // Create explosion at item location
                    world.createExplosion(null, self.getX(), self.getY(), self.getZ(),
                            power, World.ExplosionSourceType.TNT);

                    Emergent.LOGGER.debug("Volatile ItemEntity detonated: {} x{} at {} with power {}",
                            stack.getItem(), count, self.getBlockPos(), power);
                }
            }
        }
    }
}
