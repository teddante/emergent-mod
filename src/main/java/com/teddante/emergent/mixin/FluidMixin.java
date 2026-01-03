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
public abstract class FluidMixin {

    /**
     * @author Antigravity
     * @reason Enable random ticks for water to simulate erosion and evaporation.
     */
    @Inject(method = "hasRandomTicks", at = @At("HEAD"), cancellable = true)
    protected void hasRandomTicks(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof WaterFluid) {
            cir.setReturnValue(true);
        }
    }

    /**
     * @author Antigravity
     * @reason Add erosion and evaporation logic to water ticks.
     */
    @Inject(method = "onRandomTick", at = @At("HEAD"))
    protected void onRandomTick(ServerWorld world, BlockPos pos, FluidState state, Random random, CallbackInfo ci) {
        if (!((Object) this instanceof WaterFluid))
            return;

        // 1. Evaporation Logic
        handleEvaporation(world, pos, state, random);

        // 2. Erosion Logic
        // Optimization: Only erode if the water is moving (Flowing or Falling).
        if (!state.isStill()) {
            ErosionPhysics.attemptErosion(world, pos, state);
        }
    }

    private void handleEvaporation(ServerWorld world, BlockPos pos, FluidState state, Random random) {
        // Only evaporate if exposed to air or in a very hot biome
        boolean exposed = world.isAir(pos.up());
        float temperature = world.getBiome(pos).value().getTemperature();

        double chance = 0.05; // Base chance (5%)

        if (temperature > 1.0) { // Hot biomes (Desert, Badlands, Nether)
            chance = 1.0; // Rapid evaporation
        } else if (temperature > 0.8) { // Warm biomes (Savanna)
            chance = 0.2;
        } else if (temperature < 0.2) { // Cold biomes
            chance = 0.01;
        }

        if (exposed || temperature > 1.5) {
            if (random.nextDouble() < chance) {
                int newLevel = state.getLevel() - 1;
                if (newLevel <= 0) {
                    world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                } else {
                    // Update to a flowing state with lower level
                    world.setBlockState(pos,
                            ((WaterFluid) (Object) this).getFlowing(newLevel,
                                    (Boolean) state.get(net.minecraft.fluid.FlowableFluid.FALLING))
                                    .getBlockState());
                }
            }
        }
    }
}
