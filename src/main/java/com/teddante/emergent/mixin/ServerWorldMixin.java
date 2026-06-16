package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalScheduler;
import com.teddante.emergent.EnvironmentalExposure;
import com.teddante.emergent.FireEcology;
import com.teddante.emergent.FiniteFluidQuietCache;
import com.teddante.emergent.SmokeSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {
    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void emergent$queueSurfaceWeather(BlockPos pos, CallbackInfo ci) {
        @SuppressWarnings("resource")
        ServerLevel serverWorld = (ServerLevel) (Object) this;

        SmokeSystem.pruneExpired(serverWorld.getGameTime());
        if (EmergentConfig.get().fireEcology && serverWorld.isRaining()) {
            FireEcology.tickRainOnAshes(serverWorld, serverWorld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos));
        }

        if (EmergentConfig.get().rainAccumulation) {
            EnvironmentalScheduler.enqueueSurfaceWeatherSample(serverWorld, pos);
        }
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void emergent$invalidateFiniteFluidQuietCacheOnBlockUpdate(
            BlockPos pos,
            BlockState oldState,
            BlockState currentState,
            int updateFlags,
            CallbackInfo ci) {
        @SuppressWarnings("resource")
        ServerLevel serverWorld = (ServerLevel) (Object) this;
        if (!oldState.equals(currentState)) {
            EnvironmentalExposure.clearForBlockUpdate(serverWorld, pos);
            return;
        }

        FiniteFluidQuietCache.invalidateNeighborhood(serverWorld, pos, "block_update");
    }

    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V", at = @At("HEAD"))
    private void emergent$invalidateFiniteFluidQuietCacheOnNeighborUpdate(
            BlockPos pos,
            Block sourceBlock,
            @Nullable Orientation orientation,
            CallbackInfo ci) {
        @SuppressWarnings("resource")
        ServerLevel serverWorld = (ServerLevel) (Object) this;
        FiniteFluidQuietCache.invalidateNeighborhood(serverWorld, pos, "neighbor_update");
    }
}
