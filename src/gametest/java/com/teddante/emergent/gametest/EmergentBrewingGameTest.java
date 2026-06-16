package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public class EmergentBrewingGameTest {
    @GameTest(maxTicks = 20)
    public void redstoneCanRepeatedlyExtendPotionDuration(GameTestHelper context) {
        ItemStack input = PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS);
        MobEffectInstance before = firstEffect(input);

        boolean previous = EmergentConfig.get().boundlessBrewing;
        try {
            EmergentConfig.get().boundlessBrewing = true;
            context.assertTrue(PotionBrewing.EMPTY.hasMix(input, new ItemStack(Items.REDSTONE)),
                    "boundless brewing should expose a redstone mix for already-extended finite-duration potions");

            ItemStack output = PotionBrewing.EMPTY.mix(new ItemStack(Items.REDSTONE), input);
            MobEffectInstance after = firstEffect(output);

            context.assertTrue(output != input,
                    "boundless redstone brewing should return a new potion stack through the real brewing path");
            context.assertTrue(after.getDuration() > before.getDuration(),
                    "redstone should repeatedly extend non-instant finite potion duration");
            context.assertTrue(after.getAmplifier() == before.getAmplifier(),
                    "redstone duration extension should not alter potion strength");
        } finally {
            EmergentConfig.get().boundlessBrewing = previous;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void glowstoneCanRepeatedlyRaisePotionAmplifier(GameTestHelper context) {
        ItemStack input = PotionContents.createItemStack(Items.POTION, Potions.STRONG_SWIFTNESS);
        MobEffectInstance before = firstEffect(input);

        boolean previous = EmergentConfig.get().boundlessBrewing;
        try {
            EmergentConfig.get().boundlessBrewing = true;
            context.assertTrue(PotionBrewing.EMPTY.hasMix(input, new ItemStack(Items.GLOWSTONE_DUST)),
                    "boundless brewing should expose a glowstone mix for already-strengthened potions");

            ItemStack output = PotionBrewing.EMPTY.mix(new ItemStack(Items.GLOWSTONE_DUST), input);
            MobEffectInstance after = firstEffect(output);

            context.assertTrue(output != input,
                    "boundless glowstone brewing should return a new potion stack through the real brewing path");
            context.assertTrue(after.getAmplifier() == before.getAmplifier() + 1,
                    "glowstone should repeatedly raise potion amplifier by one step");
            context.assertTrue(after.getDuration() == before.getDuration(),
                    "glowstone strength increase should not alter potion duration");
        } finally {
            EmergentConfig.get().boundlessBrewing = previous;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void disabledBoundlessBrewingKeepsVanillaNoMixResult(GameTestHelper context) {
        ItemStack input = PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS);

        boolean previous = EmergentConfig.get().boundlessBrewing;
        try {
            EmergentConfig.get().boundlessBrewing = false;
            context.assertFalse(PotionBrewing.EMPTY.hasMix(input, new ItemStack(Items.REDSTONE)),
                    "when boundless brewing is disabled, an already-extended potion should keep vanilla no-mix behavior");
            context.assertTrue(PotionBrewing.EMPTY.mix(new ItemStack(Items.REDSTONE), input) == input,
                    "when boundless brewing is disabled, the real brewing path should return the unchanged input stack");
        } finally {
            EmergentConfig.get().boundlessBrewing = previous;
        }

        context.succeed();
    }

    private static MobEffectInstance firstEffect(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance effect : contents.getAllEffects()) {
            return effect;
        }
        throw new AssertionError("expected potion stack to contain an effect");
    }
}
