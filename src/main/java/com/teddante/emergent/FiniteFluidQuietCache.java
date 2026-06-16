package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class FiniteFluidQuietCache {
    private static final int MAX_ENTRIES = 32_768;
    private static final Map<ServerLevel, Map<Long, CacheEntry>> CACHES = new WeakHashMap<>();

    private FiniteFluidQuietCache() {
    }

    public static String reason(ServerLevel world, BlockPos pos, Fluid fluid, int amount) {
        return reason(world, pos, fluid, amount, 0);
    }

    public static String reason(ServerLevel world, BlockPos pos, Fluid fluid, int amount, int environmentSignature) {
        Map<Long, CacheEntry> cache = CACHES.get(world);
        if (cache == null) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_no_cache_misses", 1);
            return null;
        }

        CacheEntry entry = cache.get(pos.asLong());
        if (entry == null) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_entry_misses", 1);
            return null;
        }
        if (entry.fluid() != fluid) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_fluid_misses", 1);
            return null;
        }
        if (entry.amount() != amount) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_amount_misses", 1);
            return null;
        }
        if (entry.environmentSignature() != environmentSignature) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_signature_misses", 1);
            return null;
        }

        return entry.reason();
    }

    public static void remember(ServerLevel world, BlockPos pos, Fluid fluid, int amount, String reason) {
        remember(world, pos, fluid, amount, reason, 0);
    }

    public static void remember(ServerLevel world, BlockPos pos, Fluid fluid, int amount, String reason, int environmentSignature) {
        Map<Long, CacheEntry> cache = CACHES.computeIfAbsent(
                world,
                ignored -> new LinkedHashMap<>(MAX_ENTRIES, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry> eldest) {
                        boolean shouldRemove = size() > MAX_ENTRIES;
                        if (shouldRemove) {
                            EmergentProfiler.count(world, "finite_fluid_quiet_cache_evictions", 1);
                        }
                        return shouldRemove;
                    }
                });
        cache.put(pos.asLong(), new CacheEntry(fluid, amount, reason, environmentSignature));
    }

    public static void invalidateNeighborhood(ServerLevel world, BlockPos pos) {
        invalidateNeighborhood(world, pos, "unknown");
    }

    public static void invalidateNeighborhood(ServerLevel world, BlockPos pos, String reason) {
        Map<Long, CacheEntry> cache = CACHES.get(world);
        if (cache == null || cache.isEmpty()) {
            return;
        }

        int removed = 0;
        if (cache.remove(pos.asLong()) != null) {
            removed++;
        }
        for (Direction direction : Direction.values()) {
            if (cache.remove(pos.relative(direction).asLong()) != null) {
                removed++;
            }
        }

        if (removed > 0) {
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_invalidations", 1);
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_invalidated_entries", removed);
            EmergentProfiler.count(world, "finite_fluid_quiet_cache_invalidation_" + reason, 1);
        }
    }

    private record CacheEntry(Fluid fluid, int amount, String reason, int environmentSignature) {
    }
}
