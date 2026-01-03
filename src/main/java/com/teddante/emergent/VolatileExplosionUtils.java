package com.teddante.emergent;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.List;

public class VolatileExplosionUtils {

    public static final TagKey<net.minecraft.item.Item> VOLATILE_EXPLOSIVES = TagKey.of(RegistryKeys.ITEM,
            Identifier.of("emergent", "volatile_explosives"));
    public static final TagKey<net.minecraft.item.Item> HIGH_EXPLOSIVES = TagKey.of(RegistryKeys.ITEM,
            Identifier.of("emergent", "high_explosives"));
    public static final TagKey<net.minecraft.item.Item> LOW_EXPLOSIVES = TagKey.of(RegistryKeys.ITEM,
            Identifier.of("emergent", "low_explosives"));

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

            if (stack.isIn(HIGH_EXPLOSIVES)) {
                tntCount += stack.getCount();
            } else if (stack.isIn(LOW_EXPLOSIVES)) {
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
        return stack.isIn(VOLATILE_EXPLOSIVES);
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
            net.minecraft.world.World world,
            net.minecraft.block.entity.LockableContainerBlockEntity container,
            net.minecraft.util.math.BlockPos pos) {

        List<ItemStack> volatiles = new java.util.ArrayList<>();
        for (int i = 0; i < container.size(); i++) {
            ItemStack stack = container.getStack(i);
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
        world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power,
                net.minecraft.world.World.ExplosionSourceType.TNT);

        Emergent.LOGGER.debug("Volatile container explosion triggered at {} with power {}", pos, power);
        return true;
    }
}
