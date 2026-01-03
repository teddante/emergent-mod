package com.teddante.emergent.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    /**
     * @author Antigravity
     * @reason Implement rain accumulation.
     */
    @Inject(method = "tickIceAndSnow", at = @At("TAIL"))
    private void accumulateRain(BlockPos pos, CallbackInfo ci) {
        @SuppressWarnings("resource")
        ServerWorld serverWorld = (ServerWorld) (Object) this;

        if (serverWorld.isRaining()) {
            BlockPos topPos = serverWorld.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos);
            Biome biome = serverWorld.getBiome(topPos).value();

            // Only accumulate in biomes where it actually rains (not freezes)
            if (biome.getPrecipitation(topPos, serverWorld.getSeaLevel()) == Biome.Precipitation.RAIN) {
                BlockState state = serverWorld.getBlockState(topPos);

                // If air, 10% chance to start a puddle
                if (state.isAir()) {
                    if (serverWorld.random.nextDouble() < 0.1) {
                        // Start with level 1 water
                        serverWorld.setBlockState(topPos, Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 7));
                    }
                }
                // If it's already water, 50% chance to increase level
                else if (state.getBlock() == Blocks.WATER) {
                    if (serverWorld.random.nextDouble() < 0.5) {
                        int currentLevel = state.get(FluidBlock.LEVEL);
                        if (currentLevel > 0) { // If not already a source block
                            serverWorld.setBlockState(topPos, state.with(FluidBlock.LEVEL, currentLevel - 1));
                        }
                    }
                }
            }
        }
    }
}
