package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class ErosionPhysics {

    private static final Map<Block, Block> DEGRADATION_MAP = new HashMap<>();
    // Matches the old stochastic roll's expected wear while making erosion cumulative and repeatable.
    private static final double EXPECTED_RANDOM_ROLL_SCALE = 400.0;

    static {
        DEGRADATION_MAP.put(Blocks.STONE, Blocks.COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        DEGRADATION_MAP.put(Blocks.ANDESITE, Blocks.COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.DIORITE, Blocks.COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.GRANITE, Blocks.COBBLESTONE);

        DEGRADATION_MAP.put(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL);

        DEGRADATION_MAP.put(Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        DEGRADATION_MAP.put(Blocks.SANDSTONE, Blocks.SAND);
        DEGRADATION_MAP.put(Blocks.RED_SANDSTONE, Blocks.RED_SAND);
    }

    /**
     * Fallback for vanilla water when finite flow is off. Vanilla normalizes the
     * flow vector, so this uses the vector as direction and the local water column
     * plus neighboring height drop as the impulse estimate.
     */
    public static void attemptErosion(ServerLevel world, BlockPos fluidPos, FluidState fluidState) {
        Vec3 flow = fluidState.getFlow(world, fluidPos);
        Direction direction = dominantHorizontalDirection(flow);
        if (direction == null) {
            return;
        }

        double heightDrop = Math.max(0.0, fluidState.getOwnHeight() - world.getFluidState(fluidPos.relative(direction)).getOwnHeight());
        double impulse = fluidState.getAmount() * Math.max(0.25, heightDrop);
        attemptDirectionalErosion(world, fluidPos, direction, impulse);
    }

    /**
     * Finite-flow hook. This is intentionally based on water actually moved by the
     * cellular flow step, not on vanilla's normalized visual flow vector.
     */
    public static void attemptFlowErosion(
            ServerLevel world,
            BlockPos fluidPos,
            FluidState fluidState,
            Direction direction,
            int movedAmount) {
        if (movedAmount <= 0) {
            return;
        }

        double gravityFactor = direction == Direction.DOWN ? 1.75 : 1.0;
        double sourcePressure = fluidState.isSource() ? 1.25 : 1.0;
        double impulse = EnvironmentalExposure.hydraulicWearFromMovedWater(movedAmount, gravityFactor, sourcePressure);
        attemptDirectionalErosion(world, fluidPos, direction, impulse);
    }

    private static void attemptDirectionalErosion(ServerLevel world, BlockPos fluidPos, Direction direction, double impulse) {
        if (impulse < 0.2) {
            return;
        }

        BlockPos impactPos = findImpactTarget(world, fluidPos, direction, direction == Direction.DOWN ? 6 : 3);
        if (impactPos != null) {
            attemptBlockBreak(world, fluidPos, impactPos, world.getBlockState(impactPos), impulse);
        }

        if (direction.getAxis().isHorizontal()) {
            BlockPos bedPos = fluidPos.below();
            attemptBlockBreak(world, fluidPos, bedPos, world.getBlockState(bedPos), impulse * 0.35);

            BlockPos destinationBed = fluidPos.relative(direction).below();
            if (!destinationBed.equals(bedPos)) {
                attemptBlockBreak(world, fluidPos.relative(direction), destinationBed, world.getBlockState(destinationBed), impulse * 0.25);
            }
        }
    }

    private static BlockPos findImpactTarget(ServerLevel world, BlockPos fluidPos, Direction direction, int maxSteps) {
        BlockPos.MutableBlockPos cursor = fluidPos.mutable();
        for (int i = 0; i < maxSteps; i++) {
            cursor.move(direction);
            BlockState state = world.getBlockState(cursor);
            if (state.isAir() || WaterPhysics.isWater(state.getFluidState().getType())) {
                continue;
            }

            if (state.getFluidState().isEmpty()) {
                return cursor.immutable();
            }
        }

        return null;
    }

    private static Direction dominantHorizontalDirection(Vec3 flow) {
        double x = flow.x;
        double z = flow.z;
        if ((x * x) + (z * z) < 0.001) {
            return null;
        }

        if (Math.abs(x) > Math.abs(z)) {
            return x > 0.0 ? Direction.EAST : Direction.WEST;
        }

        return z > 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void attemptBlockBreak(ServerLevel world, BlockPos fluidPos, BlockPos pos, BlockState state, double energy) {
        if (energy <= 0.0 || !canErode(state)) {
            clearWear(world, pos);
            return;
        }

        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0.0f) {
            clearWear(world, pos);
            return;
        }

        EnvironmentalExposure.addMoisture(world, pos, state, EnvironmentalExposure.surfaceMoistureFromHydraulicWear(energy));
        double threshold = erosionThreshold(world, pos, state, hardness);
        double accumulatedWear = addWear(world, pos, state, energy);
        if (accumulatedWear >= threshold) {
            clearWear(world, pos);
            if (erodeBlock(world, pos, state)) {
                EnvironmentalExposure.addSuspendedSediment(
                        world,
                        fluidPos,
                        world.getBlockState(fluidPos),
                        MaterialPhysicsProfiles.sedimentKilogramsFromErodedBlock(state, energy));
            }
        }
    }

    private static double erosionThreshold(ServerLevel world, BlockPos pos, BlockState state, float hardness) {
        double resistance = Math.max(0.1, hardness * hardness);
        if (state.is(MaterialReactionTags.WASHES_AWAY_IN_WATER)) {
            resistance *= 0.35;
        }
        if (state.is(MaterialReactionTags.BRITTLE)) {
            resistance *= 0.45;
        }
        if (state.is(MaterialReactionTags.ERODES_IN_WATER)) {
            resistance *= 0.8;
        }

        return resistance * EXPECTED_RANDOM_ROLL_SCALE * thresholdVariance(world, pos, state);
    }

    private static double addWear(ServerLevel world, BlockPos pos, BlockState state, double energy) {
        return EnvironmentalExposure.addHydraulicWear(world, pos, state, energy);
    }

    private static void clearWear(ServerLevel world, BlockPos pos) {
        EnvironmentalExposure.clearHydraulicWear(world, pos);
    }

    private static double thresholdVariance(ServerLevel world, BlockPos pos, BlockState state) {
        long hash = world.getSeed();
        hash ^= pos.asLong() * 0x9E3779B97F4A7C15L;
        hash ^= (long) Block.getId(state) * 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;

        double unit = (hash >>> 11) * 0x1.0p-53;
        return 0.85 + (unit * 0.3);
    }

    private static boolean canErode(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
            return false;
        }

        return state.is(MaterialReactionTags.ERODES_IN_WATER)
                || state.is(MaterialReactionTags.WASHES_AWAY_IN_WATER)
                || state.is(MaterialReactionTags.BRITTLE)
                || DEGRADATION_MAP.containsKey(state.getBlock());
    }

    public static boolean tryDepositSediment(ServerLevel world, BlockPos fluidPos, Fluid fluid, int fluidAmount) {
        if (!WaterPhysics.isWater(fluid) || fluidAmount > WaterPhysics.settledThinLayerAmount(fluid)) {
            return false;
        }

        BlockState fluidState = world.getBlockState(fluidPos);
        if (!WaterPhysics.isWater(fluidState.getFluidState().getType())
                || !EnvironmentalExposure.canDepositSediment(world, fluidPos, fluidState)) {
            return false;
        }

        BlockPos bedPos = fluidPos.below();
        BlockState bedState = world.getBlockState(bedPos);
        if (bedState.isAir() || !bedState.getFluidState().isEmpty()) {
            return false;
        }

        double sediment = EnvironmentalExposure.consumeSuspendedSediment(world, fluidPos, fluidState);
        world.setBlockAndUpdate(fluidPos, MaterialPhysicsProfiles.sedimentDepositState(sediment));
        world.playSound(null, fluidPos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.35f, 0.9f);
        return true;
    }

    private static boolean erodeBlock(ServerLevel world, BlockPos pos, BlockState state) {
        Block convertedBlock = DEGRADATION_MAP.get(state.getBlock());
        if (convertedBlock == null && state.is(MaterialReactionTags.ERODES_IN_WATER)) {
            convertedBlock = fallbackDegradation(state);
        }

        if (convertedBlock != null) {
            Emergent.LOGGER.debug("Erosion at {} [{}]: {} -> {}",
                    pos.toShortString(),
                    String.format("%.2f", state.getDestroySpeed(world, pos)),
                    state.getBlock().getName().getString(),
                    convertedBlock.getName().getString());
            world.setBlockAndUpdate(pos, convertedBlock.defaultBlockState());
            world.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.5f, 0.8f);
            return true;
        } else if (state.is(MaterialReactionTags.WASHES_AWAY_IN_WATER)) {
            Emergent.LOGGER.debug("Erosion washing at {} [{}]: {} -> AIR",
                    pos.toShortString(),
                    String.format("%.2f", state.getDestroySpeed(world, pos)),
                    state.getBlock().getName().getString());
            world.destroyBlock(pos, false);
            return true;
        } else if (state.is(MaterialReactionTags.BRITTLE)) {
            Emergent.LOGGER.debug("Erosion shattering at {} [{}]: {} -> AIR",
                    pos.toShortString(),
                    String.format("%.2f", state.getDestroySpeed(world, pos)),
                    state.getBlock().getName().getString());
            world.destroyBlock(pos, false);
            world.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5f, 0.9f);
            return true;
        }

        return false;
    }

    private static Block fallbackDegradation(BlockState state) {
        if (state.is(BlockTags.GRASS_BLOCKS)) {
            return Blocks.DIRT;
        }
        if (state.is(BlockTags.DIRT)) {
            return Blocks.COARSE_DIRT;
        }
        if (state.is(BlockTags.MUD)) {
            return Blocks.CLAY;
        }

        return Blocks.GRAVEL;
    }

}
