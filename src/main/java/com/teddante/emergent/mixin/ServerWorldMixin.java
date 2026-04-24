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
            BlockPos surfacePos = topPos.below();
            Biome biome = serverWorld.getBiome(topPos).value();

            // Only accumulate in biomes where it actually rains (not freezes)
            if (biome.getPrecipitationAt(surfacePos, serverWorld.getSeaLevel()) == Biome.Precipitation.RAIN) {
                BlockState surfaceState = serverWorld.getBlockState(surfacePos);
                BlockState state = serverWorld.getBlockState(topPos);

                // If the exposed surface is already water, deepen that water instead of
                // stacking a new water block above it.
                if (surfaceState.is(Blocks.WATER)) {
                    if (serverWorld.getRandom().nextDouble() < 0.5) {
                        int currentLevel = surfaceState.getValue(LiquidBlock.LEVEL);
                        if (currentLevel > 0) { // If not already a source block
                            serverWorld.setBlock(surfacePos, surfaceState.setValue(LiquidBlock.LEVEL, currentLevel - 1), 3);
                        }
                    }
                } else if (state.isAir()) {
                    if (serverWorld.getRandom().nextDouble() < 0.1) {
                        // Start with level 1 water
                        serverWorld.setBlock(topPos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 3);
                    }
                }
            }
        }
    }
}
