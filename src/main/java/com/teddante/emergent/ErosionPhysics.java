package com.teddante.emergent;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
        DEGRADATION_MAP.put(Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL); // Skip mossy for deepslate, go straight to rubble

        // Crumbling to Gravity Blocks
        DEGRADATION_MAP.put(Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        DEGRADATION_MAP.put(Blocks.SANDSTONE, Blocks.SAND);
        DEGRADATION_MAP.put(Blocks.RED_SANDSTONE, Blocks.RED_SAND);

        // Washing Away (Gravity Blocks -> Air/Water) handled via general check
    }

    public static void attemptErosion(ServerWorld world, BlockPos fluidPos, FluidState fluidState) {
        // 1. Energy Calculation (Hydraulic Action)
        // E = Mass * Velocity^2
        float level = fluidState.getLevel();
        boolean isFalling = false;

        // Check if the fluid has the FALLING property (it should if it's
        // Water/FlowableFluid)
        if (fluidState.getEntries().containsKey(FlowableFluid.FALLING)) {
            try {
                isFalling = fluidState.get(FlowableFluid.FALLING);
            } catch (IllegalArgumentException e) {
                // Ignore if property missing despite check
            }
        }

        // Falling water has high kinetic energy (Waterfall)
        // Flowing water has low kinetic energy (River)
        float energy = level * (isFalling ? 5.0f : 1.0f);

        // 2. Target Selection
        BlockPos targetPos;
        if (isFalling) {
            // Waterfalls erode vertically (Formation of plunge pools)
            targetPos = fluidPos.down();
        } else {
            // Rivers erode horizontally (Bank erosion)
            Direction dir = Direction.Type.HORIZONTAL.random(world.random);
            targetPos = fluidPos.offset(dir);

            // If horizontal is blocked/empty, check down (River bed erosion)
            if (world.isAir(targetPos) || !world.getBlockState(targetPos).getFluidState().isEmpty()) {
                targetPos = fluidPos.down();
            }
        }

        BlockState targetState = world.getBlockState(targetPos);

        if (targetState.isAir() || !targetState.getFluidState().isEmpty())
            return;
        if (targetState.isOf(Blocks.BEDROCK) || targetState.isOf(Blocks.OBSIDIAN))
            return; // Default Immunity
        if (targetState.isIn(BlockTags.FEATURES_CANNOT_REPLACE))
            return; // Respect tags

        // 3. Resistance Calculation
        // R = Hardness^2 (Exponential resistance)
        float hardness = targetState.getHardness(world, targetPos);
        if (hardness < 0)
            return; // Unbreakable

        float resistance = hardness * hardness;

        // Prevent Divide by Zero for instant-break blocks, give them a baseline
        // resistance
        if (resistance < 0.1f)
            resistance = 0.1f;

        // 4. Probability Equation
        // P = (E / R) * k
        // k is our tuning constant to ensure this takes minutes/hours, not seconds.
        // E ranges from 1 to 40 (level 8 * 5)
        // R ranges from 0.25 (dirt) to 2500 (obsidian)

        // Example: Waterfall (E=40) vs Stone (R=2.25) -> 40/2.25 = ~17.
        // Example: River (E=4) vs Stone (R=2.25) -> 4/2.25 = ~1.7.
        // We want Stone to take a while. Let's try k = 0.005.
        // Waterfall vs Stone: 17 * 0.005 = 0.085 (8.5% chance per random tick).
        // River vs Stone: 1.7 * 0.005 = 0.0085 (0.8% chance per random tick).

        double probability = (energy / resistance) * 0.005; // 0.5% base chance (Realistic)

        if (world.random.nextDouble() < probability) {
            erodeBlock(world, targetPos, targetState);
        }
    }

    private static void erodeBlock(ServerWorld world, BlockPos pos, BlockState state) {
        // 5. Degradation Logic
        Block convertedBlock = DEGRADATION_MAP.get(state.getBlock());

        if (convertedBlock != null) {
            // Weathering: Downgrade the block
            Emergent.LOGGER.info("Erosion (Weathering) at {} [{}]: {} -> {}", pos.toShortString(),
                    String.format("%.2f", state.getHardness(world, pos)), state.getBlock().getName().getString(),
                    convertedBlock.getName().getString());
            world.setBlockState(pos, convertedBlock.getDefaultState());
            world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 0.5f, 0.8f);
        } else {
            // Washing: If it's weak or gravity-affected, wash it away
            float hardness = state.getHardness(world, pos);
            // BlockTags.GRAVEL doesn't exist in vanilla 1.21 typically, checking Block
            // directly
            if (hardness < 1.0f || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL)
                    || state.isIn(BlockTags.DIRT)) {
                Emergent.LOGGER.info("Erosion (Washing) at {} [{}]: {} -> AIR", pos.toShortString(),
                        String.format("%.2f", hardness), state.getBlock().getName().getString());
                world.breakBlock(pos, false); // Break block naturally
            }
        }
    }
}
