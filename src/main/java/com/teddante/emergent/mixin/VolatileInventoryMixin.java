package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class VolatileInventoryMixin {

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void checkVolatileInventory(ServerLevel world, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!EmergentConfig.get().volatileInventories || !cir.getReturnValue()) {
            return;
        }

        boolean isFire = source.is(DamageTypeTags.IS_FIRE);
        boolean isExplosion = source.is(DamageTypeTags.IS_EXPLOSION);

        if (isFire || isExplosion) {
            LivingEntity entity = (LivingEntity) (Object) this;
            List<ItemStack> volatiles = new ArrayList<>();
            List<ItemStack> stacksToClear = new ArrayList<>();

            if (entity instanceof Player player) {
                emergent$collectVolatileStacks(player.getInventory(), volatiles, stacksToClear);
            } else {
                for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                    ItemStack stack = entity.getItemBySlot(slot);
                    emergent$collectVolatileStack(stack, volatiles, stacksToClear);
                }

                if (entity instanceof InventoryCarrier carrier) {
                    emergent$collectVolatileStacks(carrier.getInventory(), volatiles, stacksToClear);
                }
            }

            if (!volatiles.isEmpty()) {
                float power = VolatileExplosionUtils.calculateExplosionPower(volatiles);
                if (power > 0) {
                    // Remove items BEFORE exploding to prevent recursion (explosion -> damage ->
                    // check inventory -> explosion)
                    for (ItemStack stack : stacksToClear) {
                        stack.setCount(0);
                    }

                    world.explode(null, entity.getX(), entity.getY(), entity.getZ(), power,
                            Level.ExplosionInteraction.TNT);
                }
            }
        }
    }

    @Unique
    private void emergent$collectVolatileStacks(Container container, List<ItemStack> volatiles, List<ItemStack> stacksToClear) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            emergent$collectVolatileStack(container.getItem(i), volatiles, stacksToClear);
        }
        container.setChanged();
    }

    @Unique
    private void emergent$collectVolatileStack(ItemStack stack, List<ItemStack> volatiles, List<ItemStack> stacksToClear) {
        if (VolatileExplosionUtils.containsVolatileItems(stack)) {
            VolatileExplosionUtils.collectVolatileItems(stack, volatiles);
            stacksToClear.add(stack);
        }
    }
}
