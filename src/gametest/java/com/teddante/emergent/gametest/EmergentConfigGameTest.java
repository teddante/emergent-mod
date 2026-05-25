package com.teddante.emergent.gametest;

import com.teddante.emergent.BoundlessBrewing;
import com.teddante.emergent.EmergentConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

public class EmergentConfigGameTest {
    @GameTest(maxTicks = 20)
    public void boundlessBrewingToggleDisablesExtraPotionRecipes(GameTestHelper context) {
        boolean boundlessBrewing = EmergentConfig.get().boundlessBrewing;
        ItemStack potion = potionWithEffect();
        ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);

        try {
            EmergentConfig.get().boundlessBrewing = true;
            context.assertTrue(BoundlessBrewing.hasRecipe(potion, glowstone),
                    "enabled boundless brewing should allow repeated potion amplification");

            EmergentConfig.get().boundlessBrewing = false;
            context.assertFalse(BoundlessBrewing.hasRecipe(potion, glowstone),
                    "disabled boundless brewing should not expose extra potion recipes");
            context.assertTrue(BoundlessBrewing.craft(glowstone, potion) == potion,
                    "disabled boundless brewing should leave the input stack unchanged");
        } finally {
            EmergentConfig.get().boundlessBrewing = boundlessBrewing;
        }

        context.succeed();
    }

    private static ItemStack potionWithEffect() {
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(),
                Optional.empty(),
                List.of(new MobEffectInstance(MobEffects.SPEED, 200, 0)),
                Optional.empty()));
        return potion;
    }
}
