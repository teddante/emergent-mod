package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ExperienceEnergy;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Unique
    private final Map<Holder<Enchantment>, Integer> emergent$incomingAnvilEnchantments = new HashMap<>();

    @Inject(method = "createResult", at = @At("HEAD"))
    private void emergent$clearIncomingEnchantments(CallbackInfo ci) {
        emergent$incomingAnvilEnchantments.clear();
    }

    @Redirect(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object emergent$ignoreRepairCostPenalty(ItemStack stack, DataComponentType<?> component, Object fallback) {
        if (EmergentConfig.get().boundlessEnchanting && component == DataComponents.REPAIR_COST) {
            return fallback;
        }

        return stack.getOrDefault(component, fallback);
    }

    @Redirect(
            method = "onTake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"))
    private void emergent$spendAnvilCostAsRawExperienceEnergy(Player player, int amount) {
        if (EmergentConfig.get().boundlessEnchanting && amount < 0) {
            ExperienceEnergy.spendWholeLevelCostAsRawEnergy(player, -amount);
            return;
        }

        player.giveExperienceLevels(amount);
    }

    @Redirect(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/Object2IntMap$Entry;getIntValue()I",
                    remap = false))
    private int emergent$captureIncomingEnchantmentLevel(Object2IntMap.Entry<Holder<Enchantment>> entry) {
        int level = entry.getIntValue();
        if (EmergentConfig.get().boundlessEnchanting) {
            emergent$incomingAnvilEnchantments.put(entry.getKey(), level);
        }
        return level;
    }

    @Redirect(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/ItemEnchantments$Mutable;set(Lnet/minecraft/core/Holder;I)V"))
    private void emergent$setEnergyMergedAnvilLevel(
            ItemEnchantments.Mutable enchantments,
            Holder<Enchantment> enchantment,
            int vanillaLevel) {
        int mergedLevel = vanillaLevel;
        if (EmergentConfig.get().boundlessEnchanting) {
            int currentLevel = enchantments.getLevel(enchantment);
            int incomingLevel = emergent$incomingAnvilEnchantments.getOrDefault(enchantment, 0);
            if (currentLevel > 0 && incomingLevel > 0) {
                mergedLevel = ExperienceEnergy.mergedEnchantmentLevelFromEnergy(
                        currentLevel,
                        incomingLevel,
                        enchantment.value().getAnvilCost());
            }
        }

        enchantments.set(enchantment, mergedLevel);
    }

    @Redirect(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
    private int emergent$allowHigherAnvilLevels(Enchantment enchantment) {
        return EmergentConfig.get().boundlessEnchanting ? ExperienceEnergy.MAX_ENCHANTMENT_LEVEL : enchantment.getMaxLevel();
    }

    @Redirect(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"))
    private boolean emergent$allowExclusiveEnchantments(Holder<Enchantment> first, Holder<Enchantment> second) {
        return EmergentConfig.get().unrestrictedEnchantments || Enchantment.areCompatible(first, second);
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
    private int emergent$removeTooExpensiveLimit(int original) {
        return EmergentConfig.get().boundlessEnchanting ? Integer.MAX_VALUE : original;
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 39))
    private int emergent$removeRenameCostCap(int original) {
        return EmergentConfig.get().boundlessEnchanting ? Integer.MAX_VALUE : original;
    }
}
