package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ChangeItemDamage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(ChangeItemDamage.class)
public abstract class ChangeItemDamageMixin {
    @Redirect(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"))
    private void emergent$itemDamageCostFromStoredExperienceEnergy(
            ItemStack targetStack,
            int amount,
            ServerLevel hurtLevel,
            ServerPlayer player,
            Consumer<Item> onBreak,
            ServerLevel serverLevel,
            int enchantmentLevel,
            EnchantedItemInUse item,
            Entity entity,
            Vec3 position) {
        int adjustedAmount = EmergentConfig.get().boundlessEnchanting
                ? ExperienceEnergy.itemDamageCostFromStoredEnergy(
                        item.itemStack(),
                        enchantmentLevel,
                        (ChangeItemDamage) (Object) this,
                        amount)
                : amount;
        targetStack.hurtAndBreak(adjustedAmount, hurtLevel, player, onBreak);
    }
}
