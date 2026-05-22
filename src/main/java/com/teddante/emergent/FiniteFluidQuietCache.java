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
        Map<Long, CacheEntry> cache = CACHES.get(world);
        if (cache == null) {
            return null;
        }

        CacheEntry entry = cache.get(pos.asLong());
        if (entry == null || entry.fluid() != fluid || entry.amount() != amount) {
            return null;
        }

        return entry.reason();
    }

    public static void remember(ServerLevel world, BlockPos pos, Fluid fluid, int amount, String reason) {
        Map<Long, CacheEntry> cache = CACHES.computeIfAbsent(
                world,
                ignored -> new LinkedHashMap<>(MAX_ENTRIES, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry> eldest) {
                        return size() > MAX_ENTRIES;
                    }
                });
        cache.put(pos.asLong(), new CacheEntry(fluid, amount, reason));
    }

    public static void invalidateNeighborhood(ServerLevel world, BlockPos pos) {
        Map<Long, CacheEntry> cache = CACHES.get(world);
        if (cache == null || cache.isEmpty()) {
            return;
        }

        cache.remove(pos.asLong());
        for (Direction direction : Direction.values()) {
            cache.remove(pos.relative(direction).asLong());
        }
    }

    private record CacheEntry(Fluid fluid, int amount, String reason) {
    }
}
