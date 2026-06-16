package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ExplodeEffect;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExplodeEffect.class)
public abstract class ExplodeEffectMixin {
    @Redirect(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V"))
    private void emergent$scaleConstantExplosionRadiusByStoredExperienceEnergy(
            ServerLevel explosionLevel,
            Entity source,
            DamageSource damageSource,
            ExplosionDamageCalculator damageCalculator,
            double x,
            double y,
            double z,
            float radius,
            boolean fire,
            Level.ExplosionInteraction interactionType,
            ParticleOptions smallExplosionParticles,
            ParticleOptions largeExplosionParticles,
            WeightedList<ExplosionParticleInfo> blockParticles,
            Holder<SoundEvent> explosionSound,
            ServerLevel serverLevel,
            int enchantmentLevel,
            EnchantedItemInUse item,
            Entity entity,
            Vec3 position) {
        float adjustedRadius = EmergentConfig.get().boundlessEnchanting
                ? ExperienceEnergy.explosionRadiusFromStoredEnergy(
                        item.itemStack(),
                        enchantmentLevel,
                        (ExplodeEffect) (Object) this,
                        radius)
                : radius;
        explosionLevel.explode(
                source,
                damageSource,
                damageCalculator,
                x,
                y,
                z,
                adjustedRadius,
                fire,
                interactionType,
                smallExplosionParticles,
                largeExplosionParticles,
                blockParticles,
                explosionSound);
    }
}
