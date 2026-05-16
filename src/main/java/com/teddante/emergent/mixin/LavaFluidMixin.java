package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Lava physics mixin.
 * Disables source conversion while finite fluid flow is enabled.
 */
@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin {

    /**
     * @author Emergent Mod
     * @reason Disable lava source conversion for volume conservation.
     */
    @Overwrite
    protected boolean canConvertToSource(ServerLevel world) {
        return !EmergentConfig.get().finiteWaterFlow && world.getGameRules().get(GameRules.LAVA_SOURCE_CONVERSION);
    }
}
