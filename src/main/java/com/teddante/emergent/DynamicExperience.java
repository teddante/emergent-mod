package com.teddante.emergent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class DynamicExperience {
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

    public static int baseRewardForVanillaProcessing(LivingEntity entity, int vanillaBaseReward) {
        if (!EmergentConfig.get().dynamicExperience) {
            return vanillaBaseReward;
        }
        if (entity instanceof Player || entity.isBaby()) {
            return vanillaBaseReward;
        }

        return Math.max(vanillaBaseReward, baseExperienceReward(entity, vanillaBaseReward));
    }

    public static int rewardAfterVanillaProcessing(LivingEntity entity, int vanillaReward) {
        return baseRewardForVanillaProcessing(entity, vanillaReward);
    }

    public static int baseExperienceFromMeasurements(
            double maxHealth,
            double estimatedMass,
            double armor,
            double armorToughness) {
        return ExperienceEnergy.livingDeathEnergyPoints(maxHealth, estimatedMass, armor, armorToughness);
    }
}
