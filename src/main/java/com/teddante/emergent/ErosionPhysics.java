package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class ErosionPhysics {

    // The "Degradation Chain" - Defining how materials weather down
    private static final Map<Block, Block> DEGRADATION_MAP = new HashMap<>();

    static {
        // Natural Stone Weathering
        DEGRADATION_MAP.put(Blocks.STONE, Blocks.COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        DEGRADATION_MAP.put(Blocks.ANDESITE, Blocks.COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.DIORITE, Blocks.COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.GRANITE, Blocks.COBBLESTONE);

        // Cracking/Loosening
        DEGRADATION_MAP.put(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        DEGRADATION_MAP.put(Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL);

        // Crumbling to Gravity Blocks
        DEGRADATION_MAP.put(Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        DEGRADATION_MAP.put(Blocks.SANDSTONE, Blocks.SAND);
        DEGRADATION_MAP.put(Blocks.RED_SANDSTONE, Blocks.RED_SAND);
    }

    public static void attemptErosion(ServerLevel world, BlockPos fluidPos, FluidState fluidState) {
        // 1. Get 3D Velocity Vector (The "Push" of the water)
        Vec3 velocity = fluidState.getFlow(world, fluidPos);
        double speedSq = velocity.lengthSqr();

        if (speedSq < 0.001)
            return; // Too still to erode

        // 2. Define Total Energy
        float level = fluidState.getAmount();
        // Mass (level) * velocity^2 = Kinetic Energy
        float energyMultiplier = 25.0f; // Tuning constant for "Oomph"
        float totalEnergy = (float) (level * speedSq * energyMultiplier);

        // 3. 3D Momentum Raycast
        // We cast a ray from the center of the water block along the velocity vector.
        Vec3 start = Vec3.atCenterOf(fluidPos);
        Vec3 direction = velocity.normalize();

        // Raycast steps (0.5 blocks per step)
        int maxSteps = 10; // Up to 5 blocks distance
        BlockPos targetPos = null;
        BlockState targetState = null;

        for (int i = 1; i <= maxSteps; i++) {
            Vec3 current = start.add(direction.scale(i * 0.5));
            BlockPos checkPos = BlockPos.containing(current.x, current.y, current.z);

            // Skip the source block itself
            if (checkPos.equals(fluidPos))
                continue;

            BlockState state = world.getBlockState(checkPos);

            // If we hit a solid block (not air, not water), that's our impact target!
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                targetPos = checkPos;
                targetState = state;
                break;
            }

            // If we hit air/water, momentum is conserved, keep going.
        }

        if (targetPos != null && targetState != null) {
            attemptBlockBreak(world, targetPos, targetState, totalEnergy);
        }
    }

    private static void attemptBlockBreak(ServerLevel world, BlockPos pos, BlockState state, float energy) {
        // Common Checks
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN))
            return;
        if (state.is(BlockTags.FEATURES_CANNOT_REPLACE))
            return;

        if (!state.is(MaterialReactionTags.ERODES_IN_WATER)
                && !state.is(MaterialReactionTags.WASHES_AWAY_IN_WATER)
                && !state.is(MaterialReactionTags.BRITTLE)
                && !DEGRADATION_MAP.containsKey(state.getBlock())) {
            return;
        }

        // Resistance R = Hardness^2
        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0)
            return; // Unbreakable

        float resistance = hardness * hardness;
        if (state.is(MaterialReactionTags.BRITTLE)) {
            resistance *= 0.5f;
        }
        if (resistance < 0.1f)
            resistance = 0.1f;

        // Probability P = (E / R) * k
        double probability = (energy / resistance) * 0.005; // 0.5% Base Chance

        if (world.getRandom().nextDouble() < probability) {
            erodeBlock(world, pos, state);
        }
    }

    private static void erodeBlock(ServerLevel world, BlockPos pos, BlockState state) {
        Block convertedBlock = DEGRADATION_MAP.get(state.getBlock());

        if (convertedBlock != null) {
            Emergent.LOGGER.info("Erosion (Weathering) at {} [{}]: {} -> {}",
                    pos.toShortString(),
                    String.format("%.2f", state.getDestroySpeed(world, pos)),
                    state.getBlock().getName().getString(),
                    convertedBlock.getName().getString());
            world.setBlockAndUpdate(pos, convertedBlock.defaultBlockState());
            world.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.5f, 0.8f);
        } else {
            float hardness = state.getDestroySpeed(world, pos);
            if (state.is(MaterialReactionTags.WASHES_AWAY_IN_WATER)) {
                Emergent.LOGGER.info("Erosion (Washing) at {} [{}]: {} -> AIR",
                        pos.toShortString(),
                        String.format("%.2f", hardness),
                        state.getBlock().getName().getString());
                world.destroyBlock(pos, false);
            }
        }
    }
}
