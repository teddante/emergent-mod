package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

        if (EmergentConfig.get().rainAccumulation) {
            EnvironmentalScheduler.enqueueSurfaceWeatherSample(serverWorld, pos);
        }
    }
}
