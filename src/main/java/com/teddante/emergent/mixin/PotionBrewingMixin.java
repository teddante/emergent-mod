package com.teddante.emergent.mixin;

import com.teddante.emergent.BoundlessBrewing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "hasMix", at = @At("RETURN"), cancellable = true)
    private void emergent$allowBoundlessPotionMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && BoundlessBrewing.hasRecipe(input, ingredient)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mix", at = @At("RETURN"), cancellable = true)
    private void emergent$craftBoundlessPotion(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue() == input && BoundlessBrewing.hasRecipe(input, ingredient)) {
            cir.setReturnValue(BoundlessBrewing.craft(ingredient, input));
        }
    }
}
