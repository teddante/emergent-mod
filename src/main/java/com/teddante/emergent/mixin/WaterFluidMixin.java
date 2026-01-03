package com.teddante.emergent.mixin;

import com.teddante.emergent.ErosionPhysics;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Fluid.class)
public class WaterFluidMixin {

    /**
     * @author Antigravity
     * @reason Enable random ticks for water to simulate erosion.
     */
    @Inject(method = "hasRandomTicks", at = @At("HEAD"), cancellable = true)
    protected void hasRandomTicks(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof WaterFluid) {
            cir.setReturnValue(true);
        }
    }

    /**
     * @author Antigravity
     * @reason Add erosion logic to water ticks.
     */
    @Inject(method = "onRandomTick", at = @At("HEAD"))
    protected void onRandomTick(ServerWorld world, BlockPos pos, FluidState state, Random random, CallbackInfo ci) {
        // Optimization: Only erode if the water is moving (Flowing or Falling).
        if ((Object) this instanceof WaterFluid) {
            if (!state.isStill()) {
                ErosionPhysics.attemptErosion(world, pos, state);
            }
        }
    }
}
