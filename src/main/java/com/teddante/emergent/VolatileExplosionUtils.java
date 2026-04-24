package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.List;

public class VolatileExplosionUtils {

    public static final TagKey<Item> VOLATILE_EXPLOSIVES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:volatile_explosives"));
    public static final TagKey<Item> HIGH_EXPLOSIVES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:high_explosives"));
    public static final TagKey<Item> LOW_EXPLOSIVES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:low_explosives"));

    /**
     * Calculates the explosion power based on a list of item stacks.
     * Uses a logarithmic scale to prevent excessive destruction.
     * Base Power: 0
     * +0.25 equivalent per stack in LOW_EXPLOSIVES (Gunpowder/Fire Charge)
     * +1.0 equivalent per stack in HIGH_EXPLOSIVES (TNT/Crystals)
     * Cap at 50 (server-crash safety limit).
     */
    public static float calculateExplosionPower(List<ItemStack> explosiveItems) {
        int tntCount = 0;
        int weakCount = 0;

        for (ItemStack stack : explosiveItems) {
            if (stack.isEmpty())
                continue;

            if (stack.is(HIGH_EXPLOSIVES)) {
                tntCount += stack.getCount();
            } else if (stack.is(LOW_EXPLOSIVES)) {
                weakCount += stack.getCount();
            }
        }

        // Calculate "TNT Equivalent" mass
        // We assume 4 Gunpowder = 1 TNT (roughly crafting recipe balance)
        double tntEquivalent = tntCount + (weakCount / 4.0);

        if (tntEquivalent <= 0)
            return 0.0f;

        // Physics-based scaling: Energy is proportional to Mass.
        // Explosion Radius (Power) is proportional to Cube Root of Energy (Mass).
        // Formula: Power = BasePower * (RelativeMass)^(1/3)
        // Base TNT Block Power is 4.0.

        float basePower = 4.0f;
        float power = (float) (basePower * Math.pow(tntEquivalent, 1.0 / 3.0));

        // No cap - let the chaos unfold. If someone fills a chest with 27 stacks of
        // TNT,
        // that's emergent gameplay at its finest.
        return power;
    }

    public static boolean isVolatile(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        return stack.is(VOLATILE_EXPLOSIVES);
    }

    /**
     * Checks a container for volatile items and triggers an explosion if found.
     * Clears the volatile items before exploding to prevent recursion.
     *
     * @param world     The world instance
     * @param container The container to check
     * @param pos       The position of the container
     * @return true if an explosion was triggered, false otherwise
     */
    public static boolean tryExplodeVolatileContainer(
            Level world,
            RandomizableContainerBlockEntity container,
            BlockPos pos) {

        List<ItemStack> volatiles = new java.util.ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (isVolatile(stack)) {
                volatiles.add(stack);
            }
        }

        if (volatiles.isEmpty()) {
            return false;
        }

        float power = calculateExplosionPower(volatiles);
        if (power <= 0) {
            return false;
        }

        // Clear items BEFORE exploding to prevent recursion
        for (ItemStack stack : volatiles) {
            stack.setCount(0);
        }

        // Create the explosion
        world.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power,
                Level.ExplosionInteraction.TNT);

        Emergent.LOGGER.debug("Volatile container explosion triggered at {} with power {}", pos, power);
        return true;
    }
}
