package com.teddante.emergent.client;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

public final class EmergentClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register(EmergentClient::appendExperienceEnergyTooltip);
    }

    private static void appendExperienceEnergyTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipFlag flag,
            List<Component> tooltip) {
        if (!EmergentConfig.get().boundlessEnchanting || !flag.isAdvanced()) {
            return;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return;
        }

        int storedWork = ExperienceEnergy.enchantmentLevelBudget(enchantments);
        if (storedWork <= 0) {
            return;
        }

        tooltip.add(Component.translatable("emergent.tooltip.enchantment_energy.work", storedWork)
                .withStyle(ChatFormatting.DARK_AQUA));

        int applicationWork = ExperienceEnergy.enchantmentApplicationLevelCost(stack);
        if (stack.has(DataComponents.STORED_ENCHANTMENTS) && applicationWork != storedWork) {
            tooltip.add(Component.translatable("emergent.tooltip.enchantment_energy.application_work", applicationWork)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
