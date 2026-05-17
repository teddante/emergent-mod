package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class EnvironmentalExposure {
    private static final double MAX_MOISTURE = 1.0;
    private static final double MOISTURE_DECAY_PER_TICK = 0.001;
    private static final double HEAT_DECAY_PER_TICK = 0.02;
    private static final double HEAT_DRYING_RATE = 0.08;
    private static final double WATER_COOLING_RATE = 1.5;
    private static final Map<ServerLevel, Map<Long, ExposureEntry>> EXPOSURES = new WeakHashMap<>();

    private EnvironmentalExposure() {
    }

    public static double addHeat(ServerLevel world, BlockPos pos, BlockState state, double heat) {
        if (heat <= 0.0) {
            return heat(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withHeat(entry.heat() + heat)
                .withMoisture(Math.max(0.0, entry.moisture() - heat * HEAT_DRYING_RATE))
                .withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.heat();
    }

    public static double addMoisture(ServerLevel world, BlockPos pos, BlockState state, double moisture) {
        if (moisture <= 0.0) {
            return moisture(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withMoisture(Math.min(MAX_MOISTURE, entry.moisture() + moisture))
                .withHeat(Math.max(0.0, entry.heat() - moisture * WATER_COOLING_RATE))
                .withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.moisture();
    }

    public static double addHydraulicWear(ServerLevel world, BlockPos pos, BlockState state, double wear) {
        if (wear <= 0.0) {
            return hydraulicWear(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withHydraulicWear(entry.hydraulicWear() + wear).withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.hydraulicWear();
    }

    public static double addTrafficWear(ServerLevel world, BlockPos pos, BlockState state, double wear) {
        if (wear <= 0.0) {
            return trafficWear(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withTrafficWear(entry.trafficWear() + wear).withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.trafficWear();
    }

    public static double moisture(Level world, BlockPos pos) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return 0.0;
        }

        return moisture(serverWorld, pos, serverWorld.getBlockState(pos));
    }

    public static double moisture(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.moisture();
    }

    public static void clearHeat(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withHeat(0.0));
    }

    public static void clearHydraulicWear(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withHydraulicWear(0.0));
    }

    public static void clearTrafficWear(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withTrafficWear(0.0));
    }

    public static void clear(ServerLevel world, BlockPos pos) {
        Map<Long, ExposureEntry> levelExposure = EXPOSURES.get(world);
        if (levelExposure != null) {
            levelExposure.remove(pos.asLong());
        }
    }

    private static double heat(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.heat();
    }

    private static double hydraulicWear(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.hydraulicWear();
    }

    private static double trafficWear(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.trafficWear();
    }

    private static ExposureEntry entryFor(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? new ExposureEntry(state, 0.0, 0.0, 0.0, 0.0, world.getGameTime()) : entry;
    }

    private static ExposureEntry currentEntry(ServerLevel world, BlockPos pos, BlockState state) {
        Map<Long, ExposureEntry> levelExposure = EXPOSURES.get(world);
        if (levelExposure == null) {
            return null;
        }

        long key = pos.asLong();
        ExposureEntry entry = levelExposure.get(key);
        if (entry == null) {
            return null;
        }

        if (!entry.state().equals(state)) {
            levelExposure.remove(key);
            return null;
        }

        ExposureEntry aged = age(world, entry);
        if (!aged.equals(entry)) {
            if (aged.isEmpty()) {
                levelExposure.remove(key);
                return null;
            }
            levelExposure.put(key, aged);
        }

        return aged;
    }

    private static ExposureEntry age(ServerLevel world, ExposureEntry entry) {
        long elapsedTicks = Math.max(0L, world.getGameTime() - entry.lastTick());
        if (elapsedTicks <= 0L) {
            return entry;
        }

        return entry.withHeat(Math.max(0.0, entry.heat() - elapsedTicks * HEAT_DECAY_PER_TICK))
                .withMoisture(Math.max(0.0, entry.moisture() - elapsedTicks * MOISTURE_DECAY_PER_TICK))
                .withLastTick(world.getGameTime());
    }

    private static void put(ServerLevel world, BlockPos pos, ExposureEntry entry) {
        if (entry.isEmpty()) {
            clear(world, pos);
            return;
        }

        EXPOSURES.computeIfAbsent(world, ignored -> new HashMap<>()).put(pos.asLong(), entry);
    }

    private static void update(ServerLevel world, BlockPos pos, EntryUpdater updater) {
        Map<Long, ExposureEntry> levelExposure = EXPOSURES.get(world);
        if (levelExposure == null) {
            return;
        }

        long key = pos.asLong();
        ExposureEntry entry = levelExposure.get(key);
        if (entry == null) {
            return;
        }

        ExposureEntry updated = updater.update(age(world, entry)).withLastTick(world.getGameTime());
        if (updated.isEmpty()) {
            levelExposure.remove(key);
        } else {
            levelExposure.put(key, updated);
        }
    }

    private interface EntryUpdater {
        ExposureEntry update(ExposureEntry entry);
    }

    private record ExposureEntry(
            BlockState state,
            double heat,
            double moisture,
            double hydraulicWear,
            double trafficWear,
            long lastTick) {
        boolean isEmpty() {
            return heat <= 0.0 && moisture <= 0.0 && hydraulicWear <= 0.0 && trafficWear <= 0.0;
        }

        ExposureEntry withHeat(double heat) {
            return new ExposureEntry(state, heat, moisture, hydraulicWear, trafficWear, lastTick);
        }

        ExposureEntry withMoisture(double moisture) {
            return new ExposureEntry(state, heat, moisture, hydraulicWear, trafficWear, lastTick);
        }

        ExposureEntry withHydraulicWear(double hydraulicWear) {
            return new ExposureEntry(state, heat, moisture, hydraulicWear, trafficWear, lastTick);
        }

        ExposureEntry withTrafficWear(double trafficWear) {
            return new ExposureEntry(state, heat, moisture, hydraulicWear, trafficWear, lastTick);
        }

        ExposureEntry withLastTick(long lastTick) {
            return new ExposureEntry(state, heat, moisture, hydraulicWear, trafficWear, lastTick);
        }
    }
}
