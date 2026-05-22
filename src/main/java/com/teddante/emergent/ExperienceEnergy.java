package com.teddante.emergent;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.TargetedConditionalEffect;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ChangeItemDamage;
import net.minecraft.world.item.enchantment.effects.DamageEntity;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.Ignite;

/**
 * Shared XP-as-energy helpers.
 *
 * Emergent treats raw XP points as the conserved gameplay quantity. Vanilla
 * levels remain the player-facing storage/display curve.
 */
public final class ExperienceEnergy {
    public static final int MAX_ENCHANTMENT_LEVEL = 255;
    private static final double HEALTH_ENERGY_PER_POINT = 0.25;
    private static final double MASS_ENERGY_SCALE = 1.75;
    private static final double ARMOR_RESILIENCE_SCALE = 0.65;

    private ExperienceEnergy() {
    }

    public record LevelProgress(int level, float progress) {
    }

    public static int livingDeathEnergyPoints(
            double maxHealth,
            double estimatedMass,
            double armor,
            double armorToughness) {
        if (maxHealth <= 0.0 || estimatedMass <= 0.0) {
            return 0;
        }

        double vitalEnergy = maxHealth * HEALTH_ENERGY_PER_POINT;
        double bodyEnergy = Math.sqrt(estimatedMass) * MASS_ENERGY_SCALE;
        double resilienceEnergy = Math.log1p(Math.max(0.0, armor) + Math.max(0.0, armorToughness) * 1.5)
                * ARMOR_RESILIENCE_SCALE;
        return Math.max(1, Mth.floor(vitalEnergy + bodyEnergy + resilienceEnergy));
    }

    public static int pointsForLevel(int level) {
        if (level <= 0) {
            return 0;
        }

        long total;
        if (level <= 16) {
            total = (long) level * level + (long) level * 6L;
        } else if (level <= 31) {
            total = ((long) level * level * 5L - (long) level * 81L + 720L) / 2L;
        } else {
            total = ((long) level * level * 9L - (long) level * 325L + 4440L) / 2L;
        }
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public static int pointsNeededForNextLevel(int level) {
        if (level <= 0) {
            return 7;
        }
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    public static int levelForPoints(int points) {
        int remaining = Math.max(0, points);
        int level = 0;
        while (remaining >= pointsNeededForNextLevel(level)) {
            remaining -= pointsNeededForNextLevel(level);
            level++;
        }
        return level;
    }

    public static int rawPointsForWholeLevelCost(int currentLevel, int levelCost) {
        if (currentLevel <= 0 || levelCost <= 0) {
            return 0;
        }

        int targetLevel = Math.max(0, currentLevel - levelCost);
        return pointsForLevel(currentLevel) - pointsForLevel(targetLevel);
    }

    public static int wholeLevelsAffordableFromRawPoints(int currentLevel, int rawPoints) {
        if (currentLevel <= 0 || rawPoints <= 0) {
            return 0;
        }

        int affordableLevels = 0;
        int remainingPoints = rawPoints;
        for (int level = currentLevel; level > 0; level--) {
            int nextLevelCost = pointsForLevel(level) - pointsForLevel(level - 1);
            if (remainingPoints < nextLevelCost) {
                break;
            }

            remainingPoints -= nextLevelCost;
            affordableLevels++;
        }

        return affordableLevels;
    }

    public static int rawPointsAtLevelProgress(int level, float progress) {
        int basePoints = pointsForLevel(level);
        float clampedProgress = Mth.clamp(progress, 0.0F, 1.0F);
        long progressPoints = Mth.floor(clampedProgress * pointsNeededForNextLevel(level));
        long total = (long) basePoints + progressPoints;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public static LevelProgress levelProgressForRawPoints(int points) {
        int safePoints = Math.max(0, points);
        int level = levelForPoints(safePoints);
        int levelFloor = pointsForLevel(level);
        int remainder = safePoints - levelFloor;
        float progress = remainder <= 0 ? 0.0F : (float) remainder / pointsNeededForNextLevel(level);
        return new LevelProgress(level, progress);
    }

    public static LevelProgress progressAfterWholeLevelCost(int currentLevel, float currentProgress, int levelCost) {
        int currentRawPoints = rawPointsAtLevelProgress(currentLevel, currentProgress);
        int rawCost = rawPointsForWholeLevelCost(currentLevel, levelCost);
        return levelProgressForRawPoints(Math.max(0, currentRawPoints - rawCost));
    }

    public static int enchantmentLevelBudget(ItemStack stack) {
        return enchantmentLevelBudget(EnchantmentHelper.getEnchantmentsForCrafting(stack));
    }

    public static int enchantmentLevelBudget(ItemEnchantments enchantments) {
        return enchantmentApplicationLevelCost(enchantments, false);
    }

    public static int enchantmentLevelBudget(int enchantmentLevel, int anvilCost) {
        long budget = (long) Math.max(0, enchantmentLevel) * Math.max(0, anvilCost);
        return budget >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) budget;
    }

    public static int enchantmentApplicationLevelCost(ItemStack stack) {
        return enchantmentApplicationLevelCost(
                EnchantmentHelper.getEnchantmentsForCrafting(stack),
                stack.has(DataComponents.STORED_ENCHANTMENTS));
    }

    public static int enchantmentApplicationLevelCost(ItemEnchantments enchantments, boolean storedBookCost) {
        long total = 0L;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            int level = Math.max(0, entry.getIntValue());
            int fee = Math.max(0, entry.getKey().value().getAnvilCost());
            if (storedBookCost) {
                fee = Math.max(1, fee / 2);
            }
            total += (long) fee * level;
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return (int) total;
    }

    public static int enchantmentEnergyBudgetPoints(ItemStack stack, int currentLevel) {
        return enchantmentEnergyBudgetPoints(EnchantmentHelper.getEnchantmentsForCrafting(stack), currentLevel);
    }

    public static int enchantmentEnergyBudgetPoints(ItemEnchantments enchantments, int currentLevel) {
        return rawPointsForWholeLevelCost(currentLevel, enchantmentLevelBudget(enchantments));
    }

    public static int enchantmentApplicationEnergyCostPoints(ItemStack stack, int currentLevel) {
        return rawPointsForWholeLevelCost(currentLevel, enchantmentApplicationLevelCost(stack));
    }

    public static int mergedEnchantmentLevelFromEnergy(int existingLevel, int incomingLevel, int anvilCost) {
        int safeExisting = Math.max(0, existingLevel);
        int safeIncoming = Math.max(0, incomingLevel);
        int safeAnvilCost = Math.max(0, anvilCost);
        if (safeAnvilCost <= 0) {
            return Math.min(MAX_ENCHANTMENT_LEVEL, Math.max(safeExisting, safeIncoming));
        }

        long combinedBudget = (long) enchantmentLevelBudget(safeExisting, safeAnvilCost)
                + enchantmentLevelBudget(safeIncoming, safeAnvilCost);
        long mergedLevel = combinedBudget / safeAnvilCost;
        return (int) Math.min(MAX_ENCHANTMENT_LEVEL, mergedLevel);
    }

    public static double enchantmentOutputEnergyRatio(int enchantmentLevel, int vanillaMaxLevel, int anvilCost) {
        int safeLevel = Math.max(0, enchantmentLevel);
        int safeVanillaMax = Math.max(1, vanillaMaxLevel);
        int safeAnvilCost = Math.max(1, anvilCost);
        int baselineBudget = enchantmentLevelBudget(safeVanillaMax, safeAnvilCost);
        if (baselineBudget <= 0) {
            return 1.0;
        }

        return Math.max(0.0, (double) enchantmentLevelBudget(safeLevel, safeAnvilCost) / baselineBudget);
    }

    public static int repairDurabilityFromStoredEnergy(ItemStack itemStack, int vanillaDurability) {
        if (vanillaDurability <= 0) {
            return 0;
        }

        double strongestEnergyRatio = 1.0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(itemStack).entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = Math.max(0, entry.getIntValue());
            int vanillaMaxLevel = Math.max(1, enchantment.getMaxLevel());
            if (level <= vanillaMaxLevel || !enchantment.effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)) {
                continue;
            }

            strongestEnergyRatio = Math.max(
                    strongestEnergyRatio,
                    enchantmentOutputEnergyRatio(level, vanillaMaxLevel, enchantment.getAnvilCost()));
        }

        long repairedDurability = Mth.floor(vanillaDurability * strongestEnergyRatio);
        return repairedDurability >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) repairedDurability;
    }

    public static float igniteDurationFromStoredEnergy(ItemStack itemStack, int enchantmentLevel, float vanillaSeconds) {
        if (itemStack.isEmpty() || enchantmentLevel <= 0 || vanillaSeconds <= 0.0F) {
            return vanillaSeconds;
        }

        double strongestEnergyRatio = 1.0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(itemStack).entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = Math.max(0, entry.getIntValue());
            int vanillaMaxLevel = Math.max(1, enchantment.getMaxLevel());
            if (level != enchantmentLevel || level <= vanillaMaxLevel || !hasConstantIgniteOutput(enchantment, level, vanillaMaxLevel, vanillaSeconds)) {
                continue;
            }

            strongestEnergyRatio = Math.max(
                    strongestEnergyRatio,
                    enchantmentOutputEnergyRatio(level, vanillaMaxLevel, enchantment.getAnvilCost()));
        }

        double adjustedSeconds = vanillaSeconds * strongestEnergyRatio;
        return adjustedSeconds >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) adjustedSeconds;
    }

    public static float damageEntityDamageFromStoredEnergy(
            ItemStack itemStack,
            int enchantmentLevel,
            DamageEntity sourceEffect,
            float vanillaDamage) {
        if (itemStack.isEmpty() || enchantmentLevel <= 0 || vanillaDamage <= 0.0F) {
            return vanillaDamage;
        }

        double strongestEnergyRatio = 1.0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(itemStack).entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = Math.max(0, entry.getIntValue());
            int vanillaMaxLevel = Math.max(1, enchantment.getMaxLevel());
            if (level != enchantmentLevel || level <= vanillaMaxLevel
                    || !hasConstantDamageEntityOutput(enchantment, sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                continue;
            }

            strongestEnergyRatio = Math.max(
                    strongestEnergyRatio,
                    enchantmentOutputEnergyRatio(level, vanillaMaxLevel, enchantment.getAnvilCost()));
        }

        double adjustedDamage = vanillaDamage * strongestEnergyRatio;
        return adjustedDamage >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) adjustedDamage;
    }

    public static int itemDamageCostFromStoredEnergy(
            ItemStack itemStack,
            int enchantmentLevel,
            ChangeItemDamage sourceEffect,
            int vanillaDamage) {
        if (itemStack.isEmpty() || enchantmentLevel <= 0 || vanillaDamage <= 0) {
            return vanillaDamage;
        }

        double strongestEnergyRatio = 1.0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(itemStack).entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = Math.max(0, entry.getIntValue());
            int vanillaMaxLevel = Math.max(1, enchantment.getMaxLevel());
            if (level != enchantmentLevel || level <= vanillaMaxLevel
                    || !hasConstantItemDamageOutput(enchantment, sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                continue;
            }

            strongestEnergyRatio = Math.max(
                    strongestEnergyRatio,
                    enchantmentOutputEnergyRatio(level, vanillaMaxLevel, enchantment.getAnvilCost()));
        }

        long adjustedDamage = Mth.floor(vanillaDamage * strongestEnergyRatio);
        return adjustedDamage >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) adjustedDamage;
    }

    private static boolean hasConstantIgniteOutput(Enchantment enchantment, int level, int vanillaMaxLevel, float vanillaSeconds) {
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED)) {
            if (isMatchingConstantIgnite(effect.effect(), level, vanillaMaxLevel, vanillaSeconds)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK)) {
            if (isMatchingConstantIgnite(effect.effect(), level, vanillaMaxLevel, vanillaSeconds)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.HIT_BLOCK)) {
            if (isMatchingConstantIgnite(effect.effect(), level, vanillaMaxLevel, vanillaSeconds)) {
                return true;
            }
        }
        for (TargetedConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (isMatchingConstantIgnite(effect.effect(), level, vanillaMaxLevel, vanillaSeconds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMatchingConstantIgnite(
            EnchantmentEntityEffect effect,
            int level,
            int vanillaMaxLevel,
            float vanillaSeconds) {
        if (!(effect instanceof Ignite ignite)) {
            return false;
        }

        float levelSeconds = ignite.duration().calculate(level);
        float vanillaMaxSeconds = ignite.duration().calculate(vanillaMaxLevel);
        return Math.abs(levelSeconds - vanillaSeconds) < 0.001F
                && levelSeconds <= vanillaMaxSeconds + 0.001F;
    }

    private static boolean hasConstantDamageEntityOutput(
            Enchantment enchantment,
            DamageEntity sourceEffect,
            int level,
            int vanillaMaxLevel,
            float vanillaDamage) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (isMatchingConstantDamageEntity(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK)) {
            if (isMatchingConstantDamageEntity(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED)) {
            if (isMatchingConstantDamageEntity(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.HIT_BLOCK)) {
            if (isMatchingConstantDamageEntity(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMatchingConstantDamageEntity(
            EnchantmentEntityEffect effect,
            DamageEntity sourceEffect,
            int level,
            int vanillaMaxLevel,
            float vanillaDamage) {
        if (effect instanceof AllOf.EntityEffects allOf) {
            for (EnchantmentEntityEffect childEffect : allOf.effects()) {
                if (isMatchingConstantDamageEntity(childEffect, sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                    return true;
                }
            }
            return false;
        }
        if (!(effect instanceof DamageEntity damageEntity) || !damageEntity.equals(sourceEffect)) {
            return false;
        }

        float minDamage = damageEntity.minDamage().calculate(level);
        float maxDamage = damageEntity.maxDamage().calculate(level);
        float vanillaMaxMinDamage = damageEntity.minDamage().calculate(vanillaMaxLevel);
        float vanillaMaxMaxDamage = damageEntity.maxDamage().calculate(vanillaMaxLevel);
        return Math.abs(minDamage - vanillaMaxMinDamage) < 0.001F
                && Math.abs(maxDamage - vanillaMaxMaxDamage) < 0.001F
                && vanillaDamage + 0.001F >= Math.min(minDamage, maxDamage)
                && vanillaDamage <= Math.max(minDamage, maxDamage) + 0.001F;
    }

    private static boolean hasConstantItemDamageOutput(
            Enchantment enchantment,
            ChangeItemDamage sourceEffect,
            int level,
            int vanillaMaxLevel,
            int vanillaDamage) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (isMatchingConstantItemDamage(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK)) {
            if (isMatchingConstantItemDamage(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED)) {
            if (isMatchingConstantItemDamage(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.HIT_BLOCK)) {
            if (isMatchingConstantItemDamage(effect.effect(), sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMatchingConstantItemDamage(
            EnchantmentEntityEffect effect,
            ChangeItemDamage sourceEffect,
            int level,
            int vanillaMaxLevel,
            int vanillaDamage) {
        if (effect instanceof AllOf.EntityEffects allOf) {
            for (EnchantmentEntityEffect childEffect : allOf.effects()) {
                if (isMatchingConstantItemDamage(childEffect, sourceEffect, level, vanillaMaxLevel, vanillaDamage)) {
                    return true;
                }
            }
            return false;
        }
        if (!(effect instanceof ChangeItemDamage itemDamage) || !itemDamage.equals(sourceEffect)) {
            return false;
        }

        int levelCost = Mth.floor(itemDamage.amount().calculate(level));
        int vanillaMaxCost = Mth.floor(itemDamage.amount().calculate(vanillaMaxLevel));
        return levelCost == vanillaMaxCost && vanillaDamage == levelCost;
    }

    public static void spendWholeLevelCostAsRawEnergy(Player player, int levelCost) {
        spendWholeLevelCostAsRawEnergy(player, ItemStack.EMPTY, levelCost, false);
    }

    public static void spendWholeLevelCostAsRawEnergy(
            Player player,
            ItemStack enchantedItem,
            int levelCost,
            boolean rerollEnchantmentSeed) {
        if (player.hasInfiniteMaterials()) {
            if (rerollEnchantmentSeed) {
                player.onEnchantmentPerformed(enchantedItem, 0);
            }
            return;
        }

        int remainingRawPoints = Math.max(
                0,
                rawPointsAtLevelProgress(player.experienceLevel, player.experienceProgress)
                        - rawPointsForWholeLevelCost(player.experienceLevel, levelCost));
        LevelProgress remainingProgress = levelProgressForRawPoints(remainingRawPoints);
        player.experienceLevel = remainingProgress.level();
        player.experienceProgress = remainingProgress.progress();
        player.totalExperience = remainingRawPoints;
        if (rerollEnchantmentSeed) {
            player.onEnchantmentPerformed(enchantedItem, 0);
        }
    }
}
