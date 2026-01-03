package com.teddante.emergent;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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

    public static void attemptErosion(ServerWorld world, BlockPos fluidPos, FluidState fluidState) {
        // 1. Get 3D Velocity Vector (The "Push" of the water)
        Vec3d velocity = fluidState.getVelocity(world, fluidPos);
        double speedSq = velocity.lengthSquared();

        if (speedSq < 0.001)
            return; // Too still to erode

        // 2. Define Total Energy
        float level = fluidState.getLevel();
        // Mass (level) * velocity^2 = Kinetic Energy
        float energyMultiplier = 25.0f; // Tuning constant for "Oomph"
        float totalEnergy = (float) (level * speedSq * energyMultiplier);

        // 3. 3D Momentum Raycast
        // We cast a ray from the center of the water block along the velocity vector.
        Vec3d start = Vec3d.ofCenter(fluidPos);
        Vec3d direction = velocity.normalize();

        // Raycast steps (0.5 blocks per step)
        int maxSteps = 10; // Up to 5 blocks distance
        BlockPos targetPos = null;
        BlockState targetState = null;

        for (int i = 1; i <= maxSteps; i++) {
            Vec3d current = start.add(direction.multiply(i * 0.5));
            BlockPos checkPos = BlockPos.ofFloored(current.x, current.y, current.z);

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

    private static void attemptBlockBreak(ServerWorld world, BlockPos pos, BlockState state, float energy) {
        // Common Checks
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN))
            return;
        if (state.isIn(BlockTags.FEATURES_CANNOT_REPLACE))
            return;

        // Resistance R = Hardness^2
        float hardness = state.getHardness(world, pos);
        if (hardness < 0)
            return; // Unbreakable

        float resistance = hardness * hardness;
        if (resistance < 0.1f)
            resistance = 0.1f;

        // Probability P = (E / R) * k
        double probability = (energy / resistance) * 0.005; // 0.5% Base Chance

        if (world.random.nextDouble() < probability) {
            erodeBlock(world, pos, state);
        }
    }

    private static void erodeBlock(ServerWorld world, BlockPos pos, BlockState state) {
        Block convertedBlock = DEGRADATION_MAP.get(state.getBlock());

        if (convertedBlock != null) {
            Emergent.LOGGER.info("Erosion (Weathering) at {} [{}]: {} -> {}",
                    pos.toShortString(),
                    String.format("%.2f", state.getHardness(world, pos)),
                    state.getBlock().getName().getString(),
                    convertedBlock.getName().getString());
            world.setBlockState(pos, convertedBlock.getDefaultState());
            world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 0.5f, 0.8f);
        } else {
            float hardness = state.getHardness(world, pos);
            if (hardness < 1.0f || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL)
                    || state.isIn(BlockTags.DIRT)) {
                Emergent.LOGGER.info("Erosion (Washing) at {} [{}]: {} -> AIR",
                        pos.toShortString(),
                        String.format("%.2f", hardness),
                        state.getBlock().getName().getString());
                world.breakBlock(pos, false);
            }
        }
    }
}
