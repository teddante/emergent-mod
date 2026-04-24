package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Water physics mixin.
 * Disables source conversion for volume conservation.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {

    /**
     * @author Emergent Mod
     * @reason Disable water source conversion for volume conservation.
     */
    @Overwrite
    protected boolean canConvertToSource(ServerLevel world) {
        return !EmergentConfig.get().finiteWaterFlow && world.getGameRules().get(GameRules.WATER_SOURCE_CONVERSION);
    }
}
