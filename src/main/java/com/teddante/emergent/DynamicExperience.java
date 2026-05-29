package com.teddante.emergent;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class DynamicExperience {
    private static final double HEALTH_ENERGY_PER_POINT = 0.25;
    private static final double MASS_ENERGY_SCALE = 1.75;
    private static final double ARMOR_RESILIENCE_SCALE = 0.65;

    private DynamicExperience() {
    }

    public static int baseExperienceReward(LivingEntity entity, int vanillaBaseReward) {
        if (!EmergentConfig.get().dynamicExperience) {
            return vanillaBaseReward;
        }
        if (entity instanceof Player || entity.isBaby()) {
            return vanillaBaseReward;
        }

        return baseExperienceFromMeasurements(
                entity.getMaxHealth(),
                ImpactPhysics.estimateMass(entity),
                entity.getArmorValue(),
                entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
    }

    public static int rewardAfterVanillaProcessing(LivingEntity entity, int vanillaReward) {
        if (!EmergentConfig.get().dynamicExperience) {
            return vanillaReward;
        }
        if (entity instanceof Player || entity.isBaby()) {
            return vanillaReward;
        }

        return Math.max(vanillaReward, baseExperienceReward(entity, vanillaReward));
    }

    public static int baseExperienceFromMeasurements(
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
}
