package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

public final class StructuralStressPhysics {
    private StructuralStressPhysics() {
    }

    public static boolean tryResolve(ServerLevel world, BlockPos pos, BlockState state) {
        double threshold = MaterialPhysicsProfiles.structuralStressThreshold(state);
        if (threshold <= 0.0 || EnvironmentalExposure.structuralStress(world, pos, state) < threshold) {
            return false;
        }

        BlockState fracturedState = MaterialPhysicsProfiles.thermalFractureState(state);
        if (fracturedState != null) {
            EnvironmentalExposure.clearStructuralStress(world, pos);
            world.setBlockAndUpdate(pos, fracturedState);
            world.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.45f, 0.9f);
            return true;
        }

        if (state.is(MaterialReactionTags.BRITTLE)) {
            EnvironmentalExposure.clearStructuralStress(world, pos);
            world.destroyBlock(pos, true);
            world.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.55f, 1.1f);
            return true;
        }

        return false;
    }
}
