package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class SmokeSystem {

    public static final class SmokeSource {
        public final ServerLevel level;
        public final double x, y, z;
        public final float intensity;
        public long expiresAt;

        public SmokeSource(ServerLevel level, double x, double y, double z, float intensity, long expiresAt) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.intensity = intensity;
            this.expiresAt = expiresAt;
        }
    }

    private static final Deque<SmokeSource> TRANSIENT_SOURCES = new ArrayDeque<>();
    private static final int MAX_TRANSIENT = 256;

    private SmokeSystem() {
    }

    public static void emitExplosionSmoke(ServerLevel level, double x, double y, double z, float power) {
        if (!EmergentConfig.get().smokeAndFumes)
            return;
        float intensity = Math.min(3.0f, 0.5f + power * 0.25f);
        long expires = level.getGameTime() + (long) (80 + power * 20);
        addTransient(new SmokeSource(level, x, y, z, intensity, expires));
        spawnSmokeParticles(level, x, y, z, intensity);
    }

    public static void emitTNTSmoke(ServerLevel level, double x, double y, double z) {
        emitExplosionSmoke(level, x, y, z, 4.0f);
    }

    private static void addTransient(SmokeSource source) {
        synchronized (TRANSIENT_SOURCES) {
            TRANSIENT_SOURCES.addLast(source);
            while (TRANSIENT_SOURCES.size() > MAX_TRANSIENT) {
                TRANSIENT_SOURCES.removeFirst();
            }
        }
    }

    public static void pruneExpired(long gameTime) {
        synchronized (TRANSIENT_SOURCES) {
            while (!TRANSIENT_SOURCES.isEmpty() && TRANSIENT_SOURCES.peekFirst().expiresAt < gameTime) {
                TRANSIENT_SOURCES.removeFirst();
            }
        }
    }

    /**
     * Check if an entity is inside a smoke zone from fire/lava blocks, burning
     * entities, or recent explosions. Returns the strongest intensity found, or 0.
     */
    public static float getSmokeIntensityAt(ServerLevel level, Entity entity) {
        if (!EmergentConfig.get().smokeAndFumes)
            return 0f;

        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();

        // Transient sources (explosions) — rise and linger, so check a larger vertical
        // box.
        float max = 0f;
        synchronized (TRANSIENT_SOURCES) {
            long now = level.getGameTime();
            for (SmokeSource src : TRANSIENT_SOURCES) {
                if (src.level != level)
                    continue;
                if (src.expiresAt < now)
                    continue;
                double dx = ex - src.x;
                double dz = ez - src.z;
                double horizontalSq = dx * dx + dz * dz;
                double verticalOffset = ey - src.y;
                // Smoke rises — allow a tall cylinder above the source.
                if (horizontalSq < 25 && verticalOffset > -2 && verticalOffset < 10) {
                    float falloff = (float) Math.max(0, 1.0 - Math.sqrt(horizontalSq) / 5.0);
                    max = Math.max(max, src.intensity * falloff);
                }
            }
        }

        // Block sources: count fire/lava in a small AABB around the entity.
        int fireCount = 0;
        int lavaCount = 0;
        int ex0 = (int) Math.floor(ex - 2);
        int ey0 = (int) Math.floor(ey - 1);
        int ez0 = (int) Math.floor(ez - 2);
        int ex1 = (int) Math.floor(ex + 2);
        int ey1 = (int) Math.floor(ey + 2);
        int ez1 = (int) Math.floor(ez + 2);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = ex0; x <= ex1; x++) {
            for (int y = ey0; y <= ey1; y++) {
                for (int z = ez0; z <= ez1; z++) {
                    m.set(x, y, z);
                    BlockState state = level.getBlockState(m);
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.SOUL_CAMPFIRE)) {
                        fireCount++;
                    } else if (!state.getFluidState().isEmpty() && state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                        lavaCount++;
                    }
                }
            }
        }

        if (fireCount > 0 || lavaCount > 0) {
            float local = Math.min(3.0f, fireCount * 0.25f + lavaCount * 0.5f);
            if (!isOutdoorsAt(level, entity.blockPosition())) {
                local *= 1.5f;
            } else {
                // Outdoors, smoke dissipates faster
                local *= 0.5f;
            }
            max = Math.max(max, local);
        }

        return max;
    }

    private static boolean isOutdoorsAt(ServerLevel level, BlockPos pos) {
        return level.canSeeSky(pos);
    }

    /**
     * Apply smoke effects to a living entity: blindness, cough damage, slow
     * suffocation in heavy smoke. Entities with fire resistance are mostly immune.
     */
    public static void applySmokeEffects(ServerLevel level, LivingEntity entity, float intensity) {
        if (intensity <= 0.2f)
            return;

        // Fire resistance = mostly immune (dust masks of a sort).
        if (entity.hasEffect(MobEffects.FIRE_RESISTANCE) && intensity < 2.0f)
            return;

        // Blindness in any notable smoke.
        if (intensity > 0.5f) {
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, true));
        }

        // Suffocation in heavy smoke (once per ~second).
        if (intensity > 1.5f && level.getGameTime() % 20 == 0) {
            DamageSource suffocate = level.damageSources().inWall();
            entity.hurtServer(level, suffocate, Math.min(2.0f, intensity * 0.5f));
        }

        // Nausea in very heavy smoke.
        if (intensity > 2.2f) {
            entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, true, false, true));
        }
    }

    private static void spawnSmokeParticles(ServerLevel level, double x, double y, double z, float intensity) {
        int count = (int) (8 + intensity * 8);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, count, 1.2, 0.5, 1.2, 0.05);
    }

    /**
     * Periodic ambient smoke emission for fire/lava. Called cheaply from entity
     * proximity checks.
     */
    public static void ambientSmokeParticles(ServerLevel level, BlockPos pos, boolean fromLava) {
        if (level.getRandom().nextInt(fromLava ? 20 : 10) != 0)
            return;
        level.sendParticles(fromLava ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
    }

    public static List<SmokeSource> snapshotSources() {
        synchronized (TRANSIENT_SOURCES) {
            return new java.util.ArrayList<>(TRANSIENT_SOURCES);
        }
    }

    public static void clear() {
        synchronized (TRANSIENT_SOURCES) {
            TRANSIENT_SOURCES.clear();
        }
    }
}
