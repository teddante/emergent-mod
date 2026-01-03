package com.teddante.emergent.mixin;

import net.minecraft.fluid.WaterFluid;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Water physics mixin.
 * Disables infinite water regeneration for volume conservation.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {

    /**
     * @author Emergent Mod
     * @reason Disable infinite water regeneration for volume conservation.
     */
    @Overwrite
    protected boolean isInfinite(ServerWorld world) {
        return false;
    }
}
