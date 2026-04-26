package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    @Inject(method = "filterCompatibleEnchantments", at = @At("HEAD"), cancellable = true)
    private static void emergent$keepExclusiveEnchantingOptions(
            List<EnchantmentInstance> possibleEntries,
            EnchantmentInstance pickedEntry,
            CallbackInfo ci) {
        if (EmergentConfig.get().unrestrictedEnchantments) {
            ci.cancel();
        }
    }

    @Inject(method = "isEnchantmentCompatible", at = @At("HEAD"), cancellable = true)
    private static void emergent$acceptExclusiveEnchantments(
            Collection<Holder<Enchantment>> existing,
            Holder<Enchantment> candidate,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmergentConfig.get().unrestrictedEnchantments) {
            cir.setReturnValue(true);
        }
    }
}
