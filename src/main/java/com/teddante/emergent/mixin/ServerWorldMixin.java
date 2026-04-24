package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

    /**
     * @author Antigravity
     * @reason Implement rain accumulation.
     */
    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void accumulateRain(BlockPos pos, CallbackInfo ci) {
        @SuppressWarnings("resource")
        ServerLevel serverWorld = (ServerLevel) (Object) this;

        if (EmergentConfig.get().rainAccumulation && serverWorld.isRaining()) {
            BlockPos topPos = serverWorld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
            Biome biome = serverWorld.getBiome(topPos).value();

            // Only accumulate in biomes where it actually rains (not freezes)
            if (biome.getPrecipitationAt(topPos, serverWorld.getSeaLevel()) == Biome.Precipitation.RAIN) {
                BlockState state = serverWorld.getBlockState(topPos);

                // If air, 10% chance to start a puddle
                if (state.isAir()) {
                    if (serverWorld.getRandom().nextDouble() < 0.1) {
                        // Start with level 1 water
                        serverWorld.setBlock(topPos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 3);
                    }
                }
                // If it's already water, 50% chance to increase level
                else if (state.is(Blocks.WATER)) {
                    if (serverWorld.getRandom().nextDouble() < 0.5) {
                        int currentLevel = state.getValue(LiquidBlock.LEVEL);
                        if (currentLevel > 0) { // If not already a source block
                            serverWorld.setBlock(topPos, state.setValue(LiquidBlock.LEVEL, currentLevel - 1), 3);
                        }
                    }
                }
            }
        }
    }
}
