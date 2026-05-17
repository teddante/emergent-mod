package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalExposure;
import com.teddante.emergent.MaterialReactionTags;
import com.teddante.emergent.MaterialPhysicsProfiles;
import com.teddante.emergent.MaterialReactions;
import com.teddante.emergent.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {
    @Unique
    private static final double EMERGENT_RAIN_DEEPEN_CHANCE = 0.04;
    @Unique
    private static final double EMERGENT_RAIN_PUDDLE_CHANCE = 0.0125;
    @Unique
    private static final double EMERGENT_ABSORBENT_SURFACE_FACTOR = 0.25;

    /**
     * @author Antigravity
     * @reason Implement rain accumulation.
     */
    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void accumulateRain(BlockPos pos, CallbackInfo ci) {
        @SuppressWarnings("resource")
        ServerLevel serverWorld = (ServerLevel) (Object) this;

        if (!EmergentConfig.get().rainAccumulation) {
            return;
        }

        BlockPos topPos = serverWorld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        BlockPos surfacePos = topPos.below();
        Biome biome = serverWorld.getBiome(topPos).value();
        BlockState surfaceState = serverWorld.getBlockState(surfacePos);
        BlockState state = serverWorld.getBlockState(topPos);
        boolean skyExposed = serverWorld.canSeeSky(topPos);

        if (!serverWorld.isRaining()) {
            EnvironmentalExposure.applyAmbientSurfaceExchange(
                    serverWorld,
                    surfacePos,
                    surfaceState,
                    biome.getBaseTemperature(),
                    skyExposed,
                    ThermalPhysics.neighboringHeat(serverWorld, surfacePos));
            ThermalPhysics.tryMeltFrozenSurface(serverWorld, surfacePos, serverWorld.getBlockState(surfacePos));
            if (EmergentConfig.get().materialReactions) {
                emergent$tryClimateStressExposedBlock(serverWorld, topPos, surfacePos, state, surfaceState, biome.getBaseTemperature(), skyExposed);
            }
            return;
        }

        Biome.Precipitation precipitation = biome.getPrecipitationAt(surfacePos, serverWorld.getSeaLevel());
        if (precipitation == Biome.Precipitation.SNOW) {
            EnvironmentalExposure.addSnowfall(serverWorld, surfacePos, surfaceState);
            return;
        }
        if (precipitation != Biome.Precipitation.RAIN) {
            return;
        }

        EnvironmentalExposure.addRainfall(serverWorld, surfacePos, surfaceState);
        surfaceState = serverWorld.getBlockState(surfacePos);
        ThermalPhysics.tryMeltFrozenSurface(serverWorld, surfacePos, surfaceState);
        surfaceState = serverWorld.getBlockState(surfacePos);

        if (surfaceState.is(Blocks.WATER)) {
            emergent$tryDeepenRainWater(serverWorld, surfacePos, surfaceState);
        } else if (state.isAir() && emergent$canRainCollectOn(serverWorld, surfacePos, surfaceState)) {
            emergent$tryCreateRainPuddle(serverWorld, topPos, surfacePos, surfaceState);
        }

        if (EmergentConfig.get().materialReactions) {
            MaterialReactions.tryRainOxidize(serverWorld, surfacePos, surfaceState, serverWorld.getRandom());
            emergent$tryRainGrowExposedBlock(serverWorld, topPos, surfacePos, state, surfaceState);
        }
    }

    @Unique
    private void emergent$tryDeepenRainWater(ServerLevel world, BlockPos pos, BlockState state) {
        if (world.getRandom().nextDouble() >= EMERGENT_RAIN_DEEPEN_CHANCE) {
            return;
        }

        int currentLevel = state.getValue(LiquidBlock.LEVEL);
        if (currentLevel <= 0) {
            return;
        }

        world.setBlock(pos, state.setValue(LiquidBlock.LEVEL, currentLevel - 1), 3);
        world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
    }

    @Unique
    private void emergent$tryCreateRainPuddle(ServerLevel world, BlockPos pos, BlockPos surfacePos, BlockState surfaceState) {
        double absorption = MaterialPhysicsProfiles.surfaceWaterAbsorption(surfaceState);
        double saturation = EnvironmentalExposure.moisture(world, surfacePos, surfaceState);
        if (absorption > 0.5 && saturation < 0.65) {
            return;
        }

        double chance = EMERGENT_RAIN_PUDDLE_CHANCE * emergent$surfaceAbsorptionFactor(surfaceState) * Math.max(0.35, saturation);
        if (world.getRandom().nextDouble() >= chance) {
            return;
        }

        world.setBlock(pos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 3);
        world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
    }

    @Unique
    private boolean emergent$canRainCollectOn(ServerLevel world, BlockPos surfacePos, BlockState surfaceState) {
        if (surfaceState.isAir() || !surfaceState.getFluidState().isEmpty()) {
            return false;
        }
        if (surfaceState.is(BlockTags.LEAVES) || surfaceState.is(BlockTags.LOGS)) {
            return false;
        }

        return surfaceState.isFaceSturdy(world, surfacePos, Direction.UP);
    }

    @Unique
    private double emergent$surfaceAbsorptionFactor(BlockState surfaceState) {
        if (surfaceState.is(BlockTags.DIRT)
                || surfaceState.is(BlockTags.GRASS_BLOCKS)
                || surfaceState.is(BlockTags.MUD)
                || surfaceState.is(BlockTags.SAND)) {
            return EMERGENT_ABSORBENT_SURFACE_FACTOR;
        }

        return 1.0;
    }

    @Unique
    private void emergent$tryRainGrowExposedBlock(
            ServerLevel world,
            BlockPos topPos,
            BlockPos surfacePos,
            BlockState topState,
            BlockState surfaceState) {
        if (emergent$tryRainGrowAt(world, topPos, topState)) {
            return;
        }

        emergent$tryRainGrowAt(world, surfacePos, surfaceState);
    }

    @Unique
    private boolean emergent$tryRainGrowAt(ServerLevel world, BlockPos pos, BlockState state) {
        if (!state.is(MaterialReactionTags.RAIN_GROWS)) {
            return false;
        }

        MaterialReactions.tryRainGrow(world, pos, state, world.getRandom());
        return true;
    }

    @Unique
    private void emergent$tryClimateStressExposedBlock(
            ServerLevel world,
            BlockPos topPos,
            BlockPos surfacePos,
            BlockState topState,
            BlockState surfaceState,
            float biomeTemperature,
            boolean skyExposed) {
        if (emergent$tryClimateStressAt(world, topPos, topState, biomeTemperature, skyExposed)) {
            return;
        }

        emergent$tryClimateStressAt(world, surfacePos, surfaceState, biomeTemperature, skyExposed);
    }

    @Unique
    private boolean emergent$tryClimateStressAt(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            float biomeTemperature,
            boolean skyExposed) {
        return MaterialReactions.tryClimateStress(world, pos, state, biomeTemperature, skyExposed);
    }
}
