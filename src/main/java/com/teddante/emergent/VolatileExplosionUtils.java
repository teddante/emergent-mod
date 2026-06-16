package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class VolatileExplosionUtils {

    public static final TagKey<Item> VOLATILE_EXPLOSIVES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:volatile_explosives"));
    public static final TagKey<Item> HIGH_EXPLOSIVES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:high_explosives"));
    public static final TagKey<Item> LOW_EXPLOSIVES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:low_explosives"));

    private VolatileExplosionUtils() {
    }

    /**
     * Calculates the explosion power based on a list of item stacks.
     * Uses cube-root scaling so large caches grow dramatically without scaling linearly.
     * Base Power: 0
     * +0.25 equivalent per stack in LOW_EXPLOSIVES (Gunpowder/Fire Charge)
     * +1.0 equivalent per stack in HIGH_EXPLOSIVES (TNT/Crystals)
     * Intentionally uncapped: enormous stockpiles should remain dangerous.
     */
    public static float calculateExplosionPower(List<ItemStack> explosiveItems) {
        double tntCount = 0.0;
        double weakCount = 0.0;

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

    public static boolean containsVolatileItems(ItemStack stack) {
        if (isVolatile(stack)) {
            return true;
        }

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItemCopyStream().anyMatch(VolatileExplosionUtils::containsVolatileItems);
    }

    public static void collectVolatileItems(ItemStack stack, List<ItemStack> volatiles) {
        if (isVolatile(stack)) {
            volatiles.add(stack);
        }

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.nonEmptyItemCopyStream().forEach(nested -> collectVolatileItems(nested, volatiles));
        }
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
            Container container,
            BlockPos pos) {

        List<ItemStack> volatiles = new ArrayList<>();
        List<ItemStack> stacksToClear = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (containsVolatileItems(stack)) {
                collectVolatileItems(stack, volatiles);
                stacksToClear.add(stack);
            }
        }

        if (volatiles.isEmpty()) {
            return false;
        }

        float power = calculateExplosionPower(volatiles);
        if (power <= 0) {
            return false;
        }

        // Pressure scaling: confined detonations punch harder.
        if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            float confinement = PressurePhysics.confinementFactor(serverLevel, pos.getX() + 0.5,
                    pos.getY() + 0.5, pos.getZ() + 0.5);
            power = PressurePhysics.pressureScaledPower(power, confinement);
        }

        // Clear items BEFORE exploding to prevent recursion
        for (ItemStack stack : stacksToClear) {
            stack.setCount(0);
        }
        container.setChanged();

        // Create the explosion
        world.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power,
                Level.ExplosionInteraction.TNT);

        Emergent.LOGGER.debug("Volatile container explosion triggered at {} with power {}", pos, power);
        return true;
    }
}
