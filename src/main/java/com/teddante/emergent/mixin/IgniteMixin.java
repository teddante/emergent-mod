package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.Ignite;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Ignite.class)
public abstract class IgniteMixin {
    @Redirect(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V"))
    private void emergent$scaleConstantIgniteByStoredExperienceEnergy(
            Entity target,
            float seconds,
            ServerLevel serverLevel,
            int enchantmentLevel,
            EnchantedItemInUse item,
            Entity entity,
            Vec3 position) {
        float adjustedSeconds = EmergentConfig.get().boundlessEnchanting
                ? ExperienceEnergy.igniteDurationFromStoredEnergy(item.itemStack(), enchantmentLevel, seconds)
                : seconds;
        target.igniteForSeconds(adjustedSeconds);
    }
}
