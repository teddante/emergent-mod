package com.teddante.emergent;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.List;

public class VolatileExplosionUtils {

    public static final TagKey<net.minecraft.item.Item> VOLATILE_EXPLOSIVES = TagKey.of(RegistryKeys.ITEM,
            Identifier.of("emergent", "volatile_explosives"));

    /**
     * Calculates the explosion power based on a list of item stacks.
     * Uses a logarithmic scale to prevent excessive destruction.
     * Base Power: 0
     * +1 per stack of Gunpowder/Fire Charge (approx)
     * +4 per stack of TNT
     * Cap at 10.
     */
    public static float calculateExplosionPower(List<ItemStack> explosiveItems) {
        int tntCount = 0;
        int weakCount = 0;

        for (ItemStack stack : explosiveItems) {
            if (stack.isEmpty())
                continue;

            // Check based on tag or rudimentary ID string match
            if (stack.getItem().toString().contains("tnt")) {
                tntCount += stack.getCount();
            } else {
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

        // Let it rip, but keep a server-crash safety cap (Power 50 is ~3 chunks radius)
        return Math.min(power, 50.0f);
    }

    public static boolean isVolatile(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        if (stack.isIn(VOLATILE_EXPLOSIVES))
            return true;

        // Fallback: Check ID string directly in case tags fail (e.g. during dev/init
        // issues)
        String id = stack.getItem().toString();
        return id.contains("tnt") || id.contains("gunpowder") || id.contains("fire_charge");
    }
}
