package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

public class EmergentConfigGameTest {
    @GameTest(maxTicks = 20)
    public void unrestrictedEnchantmentsToggleAllowsExclusiveEnchantments(GameTestHelper context) {
        boolean unrestrictedEnchantments = EmergentConfig.get().unrestrictedEnchantments;
        Holder.Reference<Enchantment> sharpness = context.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        Holder.Reference<Enchantment> smite = context.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SMITE);

        try {
            EmergentConfig.get().unrestrictedEnchantments = false;
            context.assertFalse(EnchantmentHelper.isEnchantmentCompatible(List.of(sharpness), smite),
                    "disabled unrestricted enchantments should keep vanilla exclusive enchantment rules");

            EmergentConfig.get().unrestrictedEnchantments = true;
            context.assertTrue(EnchantmentHelper.isEnchantmentCompatible(List.of(sharpness), smite),
                    "enabled unrestricted enchantments should allow otherwise exclusive enchantments");
        } finally {
            EmergentConfig.get().unrestrictedEnchantments = unrestrictedEnchantments;
        }

        context.succeed();
    }
}
