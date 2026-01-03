package com.teddante.emergent.mixin;

import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class VolatileInventoryMixin {

    // Updated signature: serverWorld is the first arg
    @Inject(method = "damage", at = @At("HEAD"))
    private void checkVolatileInventory(ServerWorld world, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        // World is ServerWorld, so logic only runs on server. Perfect.

        boolean isFire = source.isIn(DamageTypeTags.IS_FIRE);
        boolean isExplosion = source.isIn(DamageTypeTags.IS_EXPLOSION);

        if (isFire || isExplosion) {
            LivingEntity entity = (LivingEntity) (Object) this;
            List<ItemStack> volatiles = new ArrayList<>();

            if (entity instanceof PlayerEntity player) {
                // Main Inventory
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (VolatileExplosionUtils.isVolatile(stack)) {
                        volatiles.add(stack);
                    }
                }
            } else {
                // Check Equipment
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack stack = entity.getEquippedStack(slot);
                    if (VolatileExplosionUtils.isVolatile(stack)) {
                        volatiles.add(stack);
                    }
                }
            }

            if (!volatiles.isEmpty()) {
                float power = VolatileExplosionUtils.calculateExplosionPower(volatiles);
                if (power > 0) {
                    // Remove items BEFORE exploding to prevent recursion (explosion -> damage ->
                    // check inventory -> explosion)
                    for (ItemStack stack : volatiles) {
                        stack.setCount(0);
                    }

                    world.createExplosion(null, entity.getX(), entity.getY(), entity.getZ(), power,
                            World.ExplosionSourceType.TNT);
                }
            }
        }
    }
}
