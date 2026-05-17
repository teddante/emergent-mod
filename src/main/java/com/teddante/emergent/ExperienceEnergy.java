package com.teddante.emergent;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Shared XP-as-energy helpers.
 *
 * Emergent treats raw XP points as the conserved gameplay quantity. Vanilla
 * levels remain the player-facing storage/display curve.
 */
public final class ExperienceEnergy {
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
