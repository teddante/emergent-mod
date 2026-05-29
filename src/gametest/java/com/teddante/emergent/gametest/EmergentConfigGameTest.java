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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
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
    public void dynamicExperienceToggleControlsSculkCatalystCharge(GameTestHelper context) {
        boolean dynamicExperience = EmergentConfig.get().dynamicExperience;
        BlockPos catalystPos = BlockPos.ZERO;
        BlockPos mobPos = catalystPos.east(2);
        context.setBlock(catalystPos, Blocks.SCULK_CATALYST);
        SculkCatalystBlockEntity catalyst = (SculkCatalystBlockEntity) context.getLevel()
                .getBlockEntity(context.absolutePos(catalystPos));
        context.assertTrue(catalyst != null, "test fixture should create a sculk catalyst block entity");

        LivingEntity vanillaRavager = context.spawn(EntityType.RAVAGER, Vec3.atBottomCenterOf(mobPos.above()));
        LivingEntity dynamicRavager = null;
        try {
            EmergentConfig.get().dynamicExperience = false;
            int vanillaReward = vanillaRavager.getExperienceReward(context.getLevel(), null);
            int disabledCharge = sculkChargeFromDeath(context, catalyst, vanillaRavager, mobPos);
            context.assertTrue(disabledCharge == vanillaReward,
                    "disabled dynamic experience should leave sculk catalyst charge on the vanilla reward path");

            catalyst.getListener().getSculkSpreader().clear();
            EmergentConfig.get().dynamicExperience = true;
            dynamicRavager = context.spawn(EntityType.RAVAGER, Vec3.atBottomCenterOf(mobPos.above()));
            int dynamicReward = dynamicRavager.getExperienceReward(context.getLevel(), null);
            int enabledCharge = sculkChargeFromDeath(context, catalyst, dynamicRavager, mobPos);

            context.assertTrue(enabledCharge == dynamicReward,
                    "enabled dynamic experience should feed the same body-energy reward into sculk charge");
            context.assertTrue(enabledCharge > disabledCharge,
                    "dynamic body energy should give large tough mobs more sculk charge than vanilla");
        } finally {
            EmergentConfig.get().dynamicExperience = dynamicExperience;
            vanillaRavager.discard();
            if (dynamicRavager != null) {
                dynamicRavager.discard();
            }
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

    private static int sculkChargeFromDeath(
            GameTestHelper context,
            SculkCatalystBlockEntity catalyst,
            LivingEntity entity,
            BlockPos mobPos) {
        boolean handled = catalyst.getListener().handleGameEvent(
                context.getLevel(),
                GameEvent.ENTITY_DIE,
                GameEvent.Context.of(entity),
                Vec3.atBottomCenterOf(context.absolutePos(mobPos.above())));
        context.assertTrue(handled, "sculk catalyst should handle nearby living-entity death events");
        return catalyst.getListener()
                .getSculkSpreader()
                .getCursors()
                .stream()
                .mapToInt(cursor -> cursor.getCharge())
                .sum();
    }
}
