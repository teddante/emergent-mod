package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class FireWetness {
    private FireWetness() {
    }

    public static boolean shouldDampenIgnition(Level world, BlockPos pos, RandomSource random) {
        if (!EmergentConfig.get().wetnessFireDampening) {
            return false;
        }

        float wetness = getWetness(world, pos);
        return wetness > 0.0f && random.nextFloat() < wetness;
    }

    public static float getWetness(Level world, BlockPos pos) {
        float wetness = 0.0f;

        BlockState state = world.getBlockState(pos);
        if (WaterPhysics.isWater(state.getFluidState().getType())
                || state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && state.getValue(BlockStateProperties.WATERLOGGED)) {
            wetness = Math.max(wetness, 0.95f);
        }

        if (world.isRainingAt(pos)) {
            wetness = Math.max(wetness, 0.75f);
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (WaterPhysics.isWater(neighbor.getFluidState().getType())) {
                wetness = Math.max(wetness, direction == Direction.DOWN ? 0.65f : 0.45f);
            }
        }

        return wetness;
    }
}
