package com.teddante.emergent.gametest;

import com.teddante.emergent.DynamicExperience;
import com.teddante.emergent.EmergentConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class EmergentConfigGameTest {
    @GameTest(maxTicks = 20)
    public void dynamicExperienceToggleRestoresVanillaReward(GameTestHelper context) {
        boolean dynamicExperience = EmergentConfig.get().dynamicExperience;
        LivingEntity zombie = context.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(BlockPos.ZERO.above()));

        try {
            EmergentConfig.get().dynamicExperience = false;
            context.assertTrue(DynamicExperience.rewardAfterVanillaProcessing(zombie, 5) == 5,
                    "disabled dynamic experience should preserve vanilla reward values");

            EmergentConfig.get().dynamicExperience = true;
            context.assertTrue(DynamicExperience.rewardAfterVanillaProcessing(zombie, 5) > 5,
                    "enabled dynamic experience should raise rewards for tougher bodies");
        } finally {
            EmergentConfig.get().dynamicExperience = dynamicExperience;
            zombie.discard();
        }

        context.succeed();
    }

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
