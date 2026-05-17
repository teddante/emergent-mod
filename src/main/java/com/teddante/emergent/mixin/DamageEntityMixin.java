package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.DamageEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DamageEntity.class)
public abstract class DamageEntityMixin {
    @Redirect(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean emergent$damageFromStoredExperienceEnergy(
            Entity target,
            ServerLevel hurtLevel,
            DamageSource damageSource,
            float damage,
            ServerLevel serverLevel,
            int enchantmentLevel,
            EnchantedItemInUse item,
            Entity entity,
            Vec3 position) {
        float adjustedDamage = EmergentConfig.get().boundlessEnchanting
                ? ExperienceEnergy.damageEntityDamageFromStoredEnergy(
                        item.itemStack(),
                        enchantmentLevel,
                        (DamageEntity) (Object) this,
                        damage)
                : damage;
        return target.hurtServer(hurtLevel, damageSource, adjustedDamage);
    }
}
