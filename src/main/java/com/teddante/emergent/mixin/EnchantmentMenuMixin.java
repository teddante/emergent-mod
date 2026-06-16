package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Redirect(
            method = "lambda$clickMenuButton$0(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/player/Player;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void emergent$spendEnchantmentCostAsRawExperienceEnergy(Player player, ItemStack itemStack, int enchantmentCost) {
        if (EmergentConfig.get().boundlessEnchanting) {
            ExperienceEnergy.spendWholeLevelCostAsRawEnergy(player, itemStack, enchantmentCost, true);
            return;
        }

        player.onEnchantmentPerformed(itemStack, enchantmentCost);
    }
}
