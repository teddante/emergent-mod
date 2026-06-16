package com.teddante.emergent;

import net.minecraft.util.Mth;

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
}
