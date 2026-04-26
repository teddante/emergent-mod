package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    private static final int EMERGENT_MAX_ENCHANTMENT_LEVEL = 255;

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
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
    private int emergent$allowHigherAnvilLevels(Enchantment enchantment) {
        return EmergentConfig.get().boundlessEnchanting ? EMERGENT_MAX_ENCHANTMENT_LEVEL : enchantment.getMaxLevel();
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
