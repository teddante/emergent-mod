package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Broad "threat response" — animals and villagers flee fire, lava, flooding,
 * and smoke zones. Not real pathfinding; applies velocity nudges away from the
 * nearest hazard to keep implementation cheap.
 */
public final class CreaturePanic {

    private CreaturePanic() {
    }

    public static void tick(LivingEntity entity) {
        if (!EmergentConfig.get().creaturePanic)
            return;
        if (!(entity.level() instanceof ServerLevel level))
            return;
        if (entity.tickCount % 10 != 0)
            return;
        if (!shouldPanic(entity))
            return;

        Vec3 hazard = findNearestHazard(level, entity);
        if (hazard == null)
            return;

        Vec3 away = entity.position().subtract(hazard);
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(level.getRandom().nextDouble() - 0.5, 0, level.getRandom().nextDouble() - 0.5);
        }
        away = away.normalize().scale(0.22);
        // Only push if the entity isn't already moving away faster than us.
        Vec3 current = entity.getDeltaMovement();
        Vec3 newMotion = new Vec3(
                current.x + away.x,
                current.y + (entity.onGround() && level.getRandom().nextInt(3) == 0 ? 0.3 : 0),
                current.z + away.z);
        entity.setDeltaMovement(newMotion);
        entity.hurtMarked = true;
    }

    private static boolean shouldPanic(LivingEntity entity) {
        if (entity instanceof WaterAnimal)
            return false;
        if (entity instanceof Monster)
            return false;
        return entity instanceof Animal || entity instanceof Villager;
    }

    private static Vec3 findNearestHazard(ServerLevel level, LivingEntity entity) {
        BlockPos origin = entity.blockPosition();
        int radius = 4;
        Vec3 nearest = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(m);
                    boolean hazard = state.is(Blocks.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.SOUL_FIRE)
                            || state.is(Blocks.MAGMA_BLOCK)
                            || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
                    if (!hazard)
                        continue;
                    double dsq = entity.distanceToSqr(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5);
                    if (dsq < bestSq) {
                        bestSq = dsq;
                        nearest = new Vec3(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5);
                    }
                }
            }
        }

        // Check recent explosion smoke sources as hazards too.
        if (EmergentConfig.get().smokeAndFumes) {
            for (SmokeSystem.SmokeSource src : SmokeSystem.snapshotSources()) {
                if (src.level != level)
                    continue;
                if (src.expiresAt < level.getGameTime())
                    continue;
                double dsq = entity.distanceToSqr(src.x, src.y, src.z);
                if (dsq < 64 && dsq < bestSq) {
                    bestSq = dsq;
                    nearest = new Vec3(src.x, src.y, src.z);
                }
            }
        }

        return nearest;
    }
}
