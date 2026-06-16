package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SteamPhysics {

    private SteamPhysics() {
    }

    /**
     * Called for a water block whose tick is running. Detects adjacent lava and
     * emits steam, damages entities, and pushes them away.
     */
    public static void tickForWater(ServerLevel level, BlockPos waterPos, FluidState waterFluid) {
        if (!EmergentConfig.get().waterLavaSteam)
            return;

        BlockPos lavaPos = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = waterPos.relative(dir);
            FluidState nf = level.getFluidState(neighbor);
            if (nf.is(FluidTags.LAVA)) {
                lavaPos = neighbor;
                break;
            }
        }

        if (lavaPos == null)
            return;

        // Spawn steam particles between the two fluids.
        double sx = (waterPos.getX() + lavaPos.getX()) * 0.5 + 0.5;
        double sy = (waterPos.getY() + lavaPos.getY()) * 0.5 + 0.5;
        double sz = (waterPos.getZ() + lavaPos.getZ()) * 0.5 + 0.5;

        level.sendParticles(ParticleTypes.LARGE_SMOKE, sx, sy, sz, 4, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.CLOUD, sx, sy + 0.8, sz, 2, 0.2, 0.4, 0.2, 0.05);

        if (level.getRandom().nextInt(8) == 0) {
            level.playSound(null, waterPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.2f);
        }

        // Damage and shove nearby entities — enclosed pockets of steam are nasty.
        AABB area = new AABB(sx - 2.0, sy - 1.0, sz - 2.0, sx + 2.0, sy + 3.0, sz + 2.0);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area);
        if (!victims.isEmpty()) {
            DamageSource steam = level.damageSources().hotFloor();
            for (LivingEntity e : victims) {
                if (!e.isAlive())
                    continue;
                // Push away from steam center, with extra lift in enclosed spaces.
                Vec3 away = e.position().subtract(sx, sy, sz);
                if (away.lengthSqr() < 0.01)
                    away = new Vec3(0, 1, 0);
                away = away.normalize().scale(0.35).add(0, 0.2, 0);
                e.setDeltaMovement(e.getDeltaMovement().add(away));
                e.hurtMarked = true;

                if (level.getGameTime() % 10 == 0) {
                    e.hurtServer(level, steam, 1.0f);
                }
            }
        }

        // Low chance: cool the water slightly (reduce level or clear if enclosed).
        if (level.getRandom().nextInt(40) == 0) {
            BlockState ws = level.getBlockState(waterPos);
            if (ws.is(Blocks.WATER)) {
                int lvl = waterFluid.getAmount();
                if (lvl <= 2) {
                    level.setBlock(waterPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
