package com.teddante.emergent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BoundlessBrewing {
    private static final int MAX_AMPLIFIER = 255;
    private static final int MAX_DURATION = Integer.MAX_VALUE;
    private static final double REDSTONE_DURATION_MULTIPLIER = 1.5;

    private BoundlessBrewing() {
    }

    public static boolean hasRecipe(ItemStack input, ItemStack ingredient) {
        if (!EmergentConfig.get().boundlessBrewing || !isPotionContainer(input)) {
            return false;
        }

        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!contents.hasEffects()) {
            return false;
        }

        if (ingredient.is(Items.GLOWSTONE_DUST)) {
            for (MobEffectInstance effect : contents.getAllEffects()) {
                if (effect.getAmplifier() < MAX_AMPLIFIER) {
                    return true;
                }
            }
            return false;
        }

        if (ingredient.is(Items.REDSTONE)) {
            for (MobEffectInstance effect : contents.getAllEffects()) {
                if (!effect.getEffect().value().isInstantenous()
                        && !effect.isInfiniteDuration()
                        && effect.getDuration() < MAX_DURATION) {
                    return true;
                }
            }
        }

        return false;
    }

    public static ItemStack craft(ItemStack ingredient, ItemStack input) {
        if (!hasRecipe(input, ingredient)) {
            return input;
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean amplify = ingredient.is(Items.GLOWSTONE_DUST);

        for (MobEffectInstance effect : contents.getAllEffects()) {
            effects.add(amplify ? amplify(effect) : extend(effect));
        }

        ItemStack output = new ItemStack(input.getItem());
        output.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), contents.customColor(), effects, contents.customName()));
        return output;
    }

    private static MobEffectInstance amplify(MobEffectInstance effect) {
        return new MobEffectInstance(
                effect.getEffect(),
                effect.getDuration(),
                Math.min(effect.getAmplifier() + 1, MAX_AMPLIFIER),
                effect.isAmbient(),
                effect.isVisible(),
                effect.showIcon());
    }

    private static MobEffectInstance extend(MobEffectInstance effect) {
        if (effect.getEffect().value().isInstantenous() || effect.isInfiniteDuration()) {
            return new MobEffectInstance(effect);
        }

        int duration = (int) Math.min(Math.round(effect.getDuration() * REDSTONE_DURATION_MULTIPLIER), MAX_DURATION);
        return new MobEffectInstance(
                effect.getEffect(),
                duration,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.isVisible(),
                effect.showIcon());
    }

    private static boolean isPotionContainer(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }
}
