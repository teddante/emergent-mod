package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class TrafficWearPhysics {
    private static final Map<ServerLevel, Map<Long, WearEntry>> TRAFFIC_WEAR = new WeakHashMap<>();
    private static final double FOOTSTEP_TO_PATH_SCALE = 42.0;

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
                double impulse = horizontalMovement * Math.max(0.05, contactArea) * bodyHeight;
                changedAny |= applyTraffic(world, pos, state, impulse);
            }
        }

        return changedAny;
    }

    public static boolean applyTraffic(ServerLevel world, BlockPos pos, BlockState state, double impulse) {
        if (impulse <= 0.0 || !canCompact(state) || !world.getBlockState(pos.above()).isAir()) {
            clearWear(world, pos);
            return false;
        }

        double threshold = compactionThreshold(world, pos, state);
        double accumulatedWear = addWear(world, pos, state, impulse);
        if (accumulatedWear < threshold) {
            return false;
        }

        clearWear(world, pos);
        BlockState compacted = compactedState(state);
        if (compacted == null) {
            return false;
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

    private static double compactionThreshold(ServerLevel world, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(world, pos);
        double resistance = Math.max(0.35, hardness <= 0.0f ? 0.6 : hardness);
        if (state.is(Blocks.MUD) || state.getBlock() instanceof FarmlandBlock) {
            resistance *= 0.55;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK)) {
            resistance *= 0.75;
        }

        return resistance * FOOTSTEP_TO_PATH_SCALE * thresholdVariance(world, pos, state);
    }

    private static double addWear(ServerLevel world, BlockPos pos, BlockState state, double impulse) {
        Map<Long, WearEntry> levelWear = TRAFFIC_WEAR.computeIfAbsent(world, ignored -> new HashMap<>());
        long key = pos.asLong();
        WearEntry entry = levelWear.get(key);
        if (entry == null || !entry.state().equals(state)) {
            entry = new WearEntry(state, 0.0);
        }

        entry = new WearEntry(state, entry.wear() + impulse);
        levelWear.put(key, entry);
        return entry.wear();
    }

    private static void clearWear(ServerLevel world, BlockPos pos) {
        Map<Long, WearEntry> levelWear = TRAFFIC_WEAR.get(world);
        if (levelWear != null) {
            levelWear.remove(pos.asLong());
        }
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

    private record WearEntry(BlockState state, double wear) {
    }
}
