package com.teddante.emergent.mixin;

import net.minecraft.fluid.WaterFluid;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {

    @org.spongepowered.asm.mixin.Overwrite
    protected boolean isInfinite(ServerWorld world) {
        return false;
    }
}
