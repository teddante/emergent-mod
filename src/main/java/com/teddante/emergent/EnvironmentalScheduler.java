package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;

/**
 * Batches slow environmental surface samples so rain, drying, and climate
 * exchange do not all execute inside vanilla's precipitation loop.
 */
public final class EnvironmentalScheduler {
    private static final int SURFACE_WEATHER_STAGGER_TICKS = 8;
    private static final int SURFACE_WEATHER_JOBS_PER_TICK = 96;
    private static final Map<ServerLevel, LevelQueue> SURFACE_WEATHER = new WeakHashMap<>();

    private EnvironmentalScheduler() {
    }

    public static void enqueueSurfaceWeatherSample(ServerLevel world, BlockPos samplePos) {
        LevelQueue queue = SURFACE_WEATHER.computeIfAbsent(world, ignored -> new LevelQueue());
        long key = samplePos.asLong();
        PendingSample pending = queue.pending.get(key);
        if (pending != null) {
            pending.samples++;
            EmergentProfiler.count(world, "weather_merged_samples", 1);
            return;
        }

        long dueTick = world.getGameTime() + deterministicDelay(world, samplePos, SURFACE_WEATHER_STAGGER_TICKS);
        pending = new PendingSample(key, dueTick);
        queue.pending.put(key, pending);
        queue.dueSamples.add(pending);
        EmergentProfiler.count(world, "weather_queued_jobs", 1);
    }

    public static void tickWorld(ServerLevel world) {
        LevelQueue queue = SURFACE_WEATHER.get(world);
        if (queue == null) {
            return;
        }

        long gameTime = world.getGameTime();
        int processed = 0;
        int processedSamples = 0;
        long startedAt = EmergentProfiler.start();
        while (processed < SURFACE_WEATHER_JOBS_PER_TICK && !queue.dueSamples.isEmpty()) {
            PendingSample pending = queue.dueSamples.peek();
            if (pending.dueTick > gameTime) {
                break;
            }

            queue.dueSamples.poll();
            queue.pending.remove(pending.posLong);
            SurfaceWeatherPhysics.processWeatherSample(world, BlockPos.of(pending.posLong), pending.samples);
            processedSamples += pending.samples;
            processed++;
        }
        EmergentProfiler.record(world, EmergentProfiler.WEATHER, startedAt);
        EmergentProfiler.count(world, "weather_processed_jobs", processed);
        EmergentProfiler.count(world, "weather_processed_samples", processedSamples);

        if (queue.pending.isEmpty()) {
            SURFACE_WEATHER.remove(world);
        } else {
            EmergentProfiler.count(world, "weather_pending_jobs", queue.pending.size());
        }
    }

    public static int deterministicDelay(ServerLevel world, BlockPos pos, int intervalTicks) {
        if (intervalTicks <= 1) {
            return 0;
        }

        long hash = world.getSeed();
        hash ^= pos.asLong() * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 33;
        hash *= 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 29;
        return Math.floorMod(hash, intervalTicks);
    }

    public static double probabilityOverSamples(double singleSampleChance, int samples) {
        if (singleSampleChance <= 0.0 || samples <= 0) {
            return 0.0;
        }
        if (singleSampleChance >= 1.0) {
            return 1.0;
        }

        return 1.0 - Math.pow(1.0 - singleSampleChance, samples);
    }

    public static int queuedSurfaceWeatherSamplesForTests(ServerLevel world) {
        LevelQueue queue = SURFACE_WEATHER.get(world);
        return queue == null ? 0 : queue.pending.size();
    }

    public static int pendingSurfaceWeatherSampleWeightForTests(ServerLevel world, BlockPos pos) {
        LevelQueue queue = SURFACE_WEATHER.get(world);
        if (queue == null) {
            return 0;
        }

        PendingSample pending = queue.pending.get(pos.asLong());
        return pending == null ? 0 : pending.samples;
    }

    public static void tickWorldForTests(ServerLevel world) {
        tickWorld(world);
    }

    private static final class LevelQueue {
        private final Map<Long, PendingSample> pending = new HashMap<>();
        private final PriorityQueue<PendingSample> dueSamples = new PriorityQueue<>(
                (left, right) -> Long.compare(left.dueTick, right.dueTick));
    }

    private static final class PendingSample {
        private final long posLong;
        private final long dueTick;
        private int samples = 1;

        private PendingSample(long posLong, long dueTick) {
            this.posLong = posLong;
            this.dueTick = dueTick;
        }
    }
}
