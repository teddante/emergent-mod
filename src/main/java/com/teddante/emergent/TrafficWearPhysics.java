package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TrafficWearPhysics {
    private static final double FOOTSTEP_TO_PATH_SCALE = 42.0;
    private static final double VEGETATION_TRAMPLE_SCALE = 6.0;
    private static final IntegerProperty[] AGE_PROPERTIES = {
            BlockStateProperties.AGE_1,
            BlockStateProperties.AGE_2,
            BlockStateProperties.AGE_3,
            BlockStateProperties.AGE_4,
            BlockStateProperties.AGE_5,
            BlockStateProperties.AGE_7,
            BlockStateProperties.AGE_15,
            BlockStateProperties.AGE_25
    };

    private TrafficWearPhysics() {
    }

    public static boolean applyEntityTraffic(ServerLevel world, Entity entity) {
        if (entity.isPassenger() || !entity.onGround()) {
            return false;
        }

        Vec3 movement = entity.getKnownMovement();
        double horizontalMovement = Math.max(movement.horizontalDistance(), entity.getKnownSpeed().horizontalDistance());
        if (horizontalMovement < 0.015) {
            return false;
        }

        double bodyHeight = Math.max(0.35, entity.getBbHeight());
        return applyContactPatchTraffic(world, entity.getBoundingBox(), bodyHeight, horizontalMovement);
    }

    public static boolean applyContactPatchTraffic(ServerLevel world, AABB contactBox, double bodyHeight, double horizontalMovement) {
        if (horizontalMovement <= 0.0 || bodyHeight <= 0.0) {
            return false;
        }

        AABB patch = contactBox.deflate(1.0E-5, 0.0, 1.0E-5);
        int minX = (int) Math.floor(patch.minX);
        int maxX = (int) Math.floor(patch.maxX);
        int minZ = (int) Math.floor(patch.minZ);
        int maxZ = (int) Math.floor(patch.maxZ);
        int y = (int) Math.floor(contactBox.minY - 1.0E-5);
        boolean changedAny = false;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double overlapX = Math.max(0.0, Math.min(patch.maxX, x + 1.0) - Math.max(patch.minX, x));
                double overlapZ = Math.max(0.0, Math.min(patch.maxZ, z + 1.0) - Math.max(patch.minZ, z));
                double contactArea = overlapX * overlapZ;
                if (contactArea <= 1.0E-6) {
                    continue;
                }

                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                double impulse = EnvironmentalExposure.trafficWearFromContact(horizontalMovement, contactArea, bodyHeight);
                changedAny |= applyTraffic(world, pos, state, impulse);
            }
        }

        return changedAny;
    }

    public static boolean applyTraffic(ServerLevel world, BlockPos pos, BlockState state, double impulse) {
        if (impulse <= 0.0) {
            return false;
        }

        BlockPos coverPos = pos.above();
        BlockState coverState = world.getBlockState(coverPos);
        boolean coverCanBeTrampled = canTrampleVegetation(coverState);
        boolean changedCover = coverCanBeTrampled && applyVegetationTrampling(world, coverPos, coverState, pos, state, impulse);
        if (!coverState.isAir() && !coverCanBeTrampled) {
            clearWear(world, pos);
            return changedCover;
        }

        if (!canCompact(state)) {
            clearWear(world, pos);
            return changedCover;
        }

        double threshold = compactionThreshold(world, pos, state);
        double accumulatedWear = addWear(world, pos, state, impulse);
        if (accumulatedWear < threshold) {
            return changedCover;
        }

        clearWear(world, pos);
        BlockState compacted = compactedState(state);
        if (compacted == null) {
            return changedCover;
        }

        world.setBlock(pos, compacted, 3);
        world.playSound(null, pos, SoundEvents.GRASS_STEP, SoundSource.BLOCKS, 0.45f, 0.8f);
        return true;
    }

    private static boolean canCompact(BlockState state) {
        return state.is(MaterialReactionTags.COMPACTS_UNDER_TRAFFIC) && compactedState(state) != null;
    }

    private static BlockState compactedState(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }

        if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK)) {
            return Blocks.DIRT.defaultBlockState();
        }

        if (state.is(Blocks.MUD)) {
            return Blocks.PACKED_MUD.defaultBlockState();
        }

        if (state.getBlock() instanceof FarmlandBlock) {
            return Blocks.DIRT.defaultBlockState();
        }

        return null;
    }

    private static boolean applyVegetationTrampling(
            ServerLevel world,
            BlockPos vegetationPos,
            BlockState vegetationState,
            BlockPos supportPos,
            BlockState supportState,
            double impulse) {
        double threshold = vegetationTrampleThreshold(world, vegetationPos, vegetationState, supportPos, supportState);
        double accumulatedWear = addWear(world, vegetationPos, vegetationState, impulse);
        if (accumulatedWear < threshold) {
            return false;
        }

        clearWear(world, vegetationPos);
        if (tryRegressAge(world, vegetationPos, vegetationState)) {
            world.playSound(null, vegetationPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.35f, 0.9f);
            return true;
        }

        if (vegetationState.getBlock() instanceof CropBlock || vegetationState.is(BlockTags.CROPS)) {
            world.destroyBlock(vegetationPos, true);
        } else {
            world.removeBlock(vegetationPos, false);
        }
        world.playSound(null, vegetationPos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.35f, 0.8f);
        return true;
    }

    private static double vegetationTrampleThreshold(
            ServerLevel world,
            BlockPos vegetationPos,
            BlockState vegetationState,
            BlockPos supportPos,
            BlockState supportState) {
        double maturityFactor = 1.0;
        IntegerProperty ageProperty = ageProperty(vegetationState);
        if (ageProperty != null) {
            int age = vegetationState.getValue(ageProperty);
            int maxAge = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
            maturityFactor = 0.8 + (maxAge <= 0 ? 0.0 : age / (double) maxAge) * 0.35;
        }

        double threshold = MaterialPhysicsProfiles.vegetationStressThreshold(vegetationState)
                * VEGETATION_TRAMPLE_SCALE
                * maturityFactor
                * thresholdVariance(world, vegetationPos, vegetationState);
        double supportMoisture = EnvironmentalExposure.moisture(world, supportPos, supportState);
        if (supportMoisture > 0.0 && canBeSoftenedByMoisture(supportState)) {
            threshold *= 1.0 - Math.min(0.35, supportMoisture * 0.3);
        }
        return threshold;
    }

    private static double compactionThreshold(ServerLevel world, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(world, pos);
        double resistance = Math.max(0.35, hardness <= 0.0f ? 0.6 : hardness);
        if (state.is(Blocks.MUD) || state.getBlock() instanceof FarmlandBlock) {
            resistance *= 0.55;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK)) {
            resistance *= 0.75;
        }

        double moisture = EnvironmentalExposure.moisture(world, pos, state);
        if (moisture > 0.0 && canBeSoftenedByMoisture(state)) {
            resistance *= 1.0 - Math.min(0.4, moisture * 0.35);
        }

        return resistance * FOOTSTEP_TO_PATH_SCALE * thresholdVariance(world, pos, state);
    }

    private static double addWear(ServerLevel world, BlockPos pos, BlockState state, double impulse) {
        return EnvironmentalExposure.addTrafficWear(world, pos, state, impulse);
    }

    private static void clearWear(ServerLevel world, BlockPos pos) {
        EnvironmentalExposure.clearTrafficWear(world, pos);
    }

    private static boolean canBeSoftenedByMoisture(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.PALE_MOSS_BLOCK)
                || state.is(Blocks.MUD)
                || state.getBlock() instanceof FarmlandBlock;
    }

    private static boolean canTrampleVegetation(BlockState state) {
        return state.is(BlockTags.CROPS)
                || state.is(BlockTags.FLOWERS)
                || state.is(MaterialReactionTags.RAIN_GROWS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.LEAF_LITTER);
    }

    private static boolean tryRegressAge(ServerLevel world, BlockPos pos, BlockState state) {
        IntegerProperty property = ageProperty(state);
        if (property == null) {
            return false;
        }

        int age = state.getValue(property);
        if (age <= 0) {
            return false;
        }

        world.setBlock(pos, state.setValue(property, age - 1), 3);
        return true;
    }

    private static IntegerProperty ageProperty(BlockState state) {
        for (IntegerProperty property : AGE_PROPERTIES) {
            if (state.hasProperty(property)) {
                return property;
            }
        }

        return null;
    }

    private static double thresholdVariance(ServerLevel world, BlockPos pos, BlockState state) {
        long hash = world.getSeed();
        hash ^= pos.asLong() * 0xD6E8FEB86659FD93L;
        hash ^= (long) Block.getId(state) * 0xA0761D6478BD642FL;
        hash ^= hash >>> 32;
        hash *= 0xE7037ED1A0B428DBL;
        hash ^= hash >>> 29;

        double unit = (hash >>> 11) * 0x1.0p-53;
        return 0.9 + (unit * 0.2);
    }

}
