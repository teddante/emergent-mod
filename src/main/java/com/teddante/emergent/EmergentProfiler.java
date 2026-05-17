package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class EmergentProfiler {
    public static final String WEATHER = "surface_weather";
    public static final String FINITE_FLUIDS = "finite_fluids";
    public static final String FINITE_WATER = "finite_water";
    public static final String FINITE_LAVA = "finite_lava";
    public static final String FIRE_REACTIONS = "fire_reactions";
    public static final String TRAFFIC = "traffic_wear";

    private static final boolean ENABLED = Boolean.getBoolean("emergent.profiler");
    private static final long SLOW_TICK_MILLIS = Long.getLong("emergent.profiler.slowMs", 25L);
    private static final long SLOW_TICK_NANOS = SLOW_TICK_MILLIS * 1_000_000L;
    private static final int TOP_HEATED_BLOCKS = 4;
    private static final int TOP_HOT_CHUNKS = 8;
    private static final Map<ServerLevel, TickStats> STATS = new WeakHashMap<>();

    private EmergentProfiler() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static long slowTickMillis() {
        return SLOW_TICK_MILLIS;
    }

    public static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void record(ServerLevel world, String category, long startedAtNanos) {
        if (!ENABLED || startedAtNanos <= 0L) {
            return;
        }

        recordNanos(world, category, System.nanoTime() - startedAtNanos);
    }

    public static void recordNanos(ServerLevel world, String category, long nanos) {
        if (!ENABLED || nanos <= 0L) {
            return;
        }

        TickStats stats = statsFor(world);
        CategoryStats categoryStats = stats.categories.computeIfAbsent(category, ignored -> new CategoryStats());
        categoryStats.calls++;
        categoryStats.nanos += nanos;
        stats.totalNanos += nanos;
    }

    public static void count(ServerLevel world, String counter, int amount) {
        if (!ENABLED || amount <= 0) {
            return;
        }

        statsFor(world).counters.merge(counter, (long) amount, Long::sum);
    }

    public static void recordChunk(ServerLevel world, String category, BlockPos pos) {
        if (!ENABLED) {
            return;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String key = category + "@" + chunkX + "," + chunkZ;
        statsFor(world).chunks.merge(key, 1L, Long::sum);
    }

    public static void recordHeat(ServerLevel world, BlockState state, double heat) {
        if (!ENABLED || heat <= 0.0) {
            return;
        }

        statsFor(world).heatByBlock.merge(state.getBlock().toString(), heat, Double::sum);
    }

    public static void startLevelTick(ServerLevel world) {
        if (!ENABLED) {
            return;
        }

        STATS.put(world, new TickStats(world.getGameTime()));
    }

    public static void endLevelTick(ServerLevel world) {
        if (!ENABLED) {
            return;
        }

        TickStats stats = STATS.get(world);
        if (stats == null || stats.totalNanos < SLOW_TICK_NANOS) {
            return;
        }

        Emergent.LOGGER.warn(
                "Emergent profiler: {} ms in {} at tick {} ({})",
                formatMillis(stats.totalNanos),
                world.dimension().identifier(),
                stats.gameTime,
                stats.describe());
    }

    private static TickStats statsFor(ServerLevel world) {
        return STATS.computeIfAbsent(world, ignored -> new TickStats(world.getGameTime()));
    }

    private static String formatMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static final class TickStats {
        private final long gameTime;
        private long totalNanos;
        private final Map<String, CategoryStats> categories = new HashMap<>();
        private final Map<String, Long> counters = new HashMap<>();
        private final Map<String, Long> chunks = new HashMap<>();
        private final Map<String, Double> heatByBlock = new HashMap<>();

        private TickStats(long gameTime) {
            this.gameTime = gameTime;
        }

        private String describe() {
            StringBuilder builder = new StringBuilder();
            builder.append("categories=");
            categories.entrySet().stream()
                    .sorted((left, right) -> Long.compare(right.getValue().nanos, left.getValue().nanos))
                    .forEach(entry -> builder
                            .append(entry.getKey())
                            .append(":")
                            .append(formatMillis(entry.getValue().nanos))
                            .append("ms/")
                            .append(entry.getValue().calls)
                            .append(" "));

            if (!counters.isEmpty()) {
                builder.append("counters=");
                counters.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> builder.append(entry.getKey()).append(":").append(entry.getValue()).append(" "));
            }

            if (!chunks.isEmpty()) {
                builder.append("chunks=");
                chunks.entrySet().stream()
                        .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                        .limit(TOP_HOT_CHUNKS)
                        .forEach(entry -> builder.append(entry.getKey()).append(":").append(entry.getValue()).append(" "));
            }

            if (!heatByBlock.isEmpty()) {
                builder.append("heat=");
                heatByBlock.entrySet().stream()
                        .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                        .limit(TOP_HEATED_BLOCKS)
                        .forEach(entry -> builder
                                .append(entry.getKey())
                                .append(":")
                                .append(String.format(java.util.Locale.ROOT, "%.3f", entry.getValue()))
                                .append(" "));
            }

            return builder.toString().trim();
        }
    }

    private static final class CategoryStats {
        private long calls;
        private long nanos;
    }
}
