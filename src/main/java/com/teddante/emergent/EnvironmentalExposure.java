package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class EnvironmentalExposure {
    public static final double BLOCK_VOLUME_CUBIC_METERS = 1.0;
    public static final double LITERS_PER_CUBIC_METER = 1_000.0;
    public static final int FULL_FLUID_BLOCK_AMOUNT = 8;
    public static final double FLUID_AMOUNT_CUBIC_METERS = BLOCK_VOLUME_CUBIC_METERS / FULL_FLUID_BLOCK_AMOUNT;
    public static final double FLUID_AMOUNT_LITERS = LITERS_PER_CUBIC_METER / FULL_FLUID_BLOCK_AMOUNT;
    private static final double MAX_MOISTURE = 1.0;
    private static final double MAX_STANDING_WATER_MOISTURE = 0.95;
    private static final double MAX_CONTACT_SURFACE_MOISTURE = 0.75;
    private static final double ACTIVE_SURFACE_DEPTH_METERS = 0.05;
    private static final double ACTIVE_SURFACE_PORE_FRACTION = 0.35;
    private static final double RAIN_SAMPLE_DEPTH_METERS = 0.001;
    private static final double MOISTURE_DECAY_PER_TICK = 0.001;
    private static final double HEAT_DECAY_PER_TICK = 0.02;
    private static final double COLD_DECAY_PER_TICK = 0.01;
    private static final double HEAT_DRYING_RATE = 0.08;
    private static final double WATER_COOLING_RATE = 1.5;
    private static final double COLD_COOLING_RATE = 1.25;
    private static final double SNOW_SAMPLE_COLD = 0.18;
    private static final double SNOW_SAMPLE_MOISTURE = 0.08;
    private static final double HYDRAULIC_WEAR_PER_CUBIC_METER = FULL_FLUID_BLOCK_AMOUNT;
    private static final double HYDRAULIC_MOISTURE_PER_WEAR_UNIT = 1.0 / 32.0;
    private static final double HOT_BIOME_DRYING_PER_SAMPLE = 0.025;
    private static final double SKY_EXPOSURE_DRYING_PER_SAMPLE = 0.006;
    private static final double LOCAL_HEAT_DRYING_PER_SAMPLE = 0.08;
    private static final double LOCAL_HEAT_EXPOSURE_PER_SAMPLE = 0.12;
    private static final double COLD_BIOME_EXPOSURE_PER_SAMPLE = 0.04;
    private static final double SEDIMENT_DEPOSIT_THRESHOLD_KG = 45.0;
    private static final Map<ServerLevel, Map<Long, ExposureEntry>> EXPOSURES = new WeakHashMap<>();

    private EnvironmentalExposure() {
    }

    public static double fluidAmountCubicMeters(int fluidAmount) {
        return Math.max(0, fluidAmount) * FLUID_AMOUNT_CUBIC_METERS;
    }

    public static double fluidAmountLiters(int fluidAmount) {
        return fluidAmountCubicMeters(fluidAmount) * LITERS_PER_CUBIC_METER;
    }

    public static double standingWaterMoisture(int waterAmount) {
        return Math.min(MAX_STANDING_WATER_MOISTURE, fluidAmountCubicMeters(waterAmount) / BLOCK_VOLUME_CUBIC_METERS);
    }

    public static double contactSurfaceMoisture(int waterAmount) {
        return Math.min(MAX_CONTACT_SURFACE_MOISTURE, fluidAmountCubicMeters(waterAmount) / BLOCK_VOLUME_CUBIC_METERS * 0.8);
    }

    public static double rainfallSurfaceMoisture(BlockState state, double rainfallDepthMeters) {
        double activePoreVolume = ACTIVE_SURFACE_DEPTH_METERS * ACTIVE_SURFACE_PORE_FRACTION;
        if (activePoreVolume <= 0.0) {
            return 0.0;
        }

        double rainfallVolume = Math.max(0.0, rainfallDepthMeters) * BLOCK_VOLUME_CUBIC_METERS;
        return Math.min(0.25, rainfallVolume / activePoreVolume * MaterialPhysicsProfiles.surfaceWaterAbsorption(state));
    }

    public static double addRainfall(ServerLevel world, BlockPos pos, BlockState state) {
        double moisture = addMoisture(world, pos, state, rainfallSurfaceMoisture(state, RAIN_SAMPLE_DEPTH_METERS));
        washAshResidue(world, pos, state, RAIN_SAMPLE_DEPTH_METERS * 25.0);
        return moisture;
    }

    public static double addSnowfall(ServerLevel world, BlockPos pos, BlockState state) {
        addMoisture(world, pos, state, SNOW_SAMPLE_MOISTURE * MaterialPhysicsProfiles.surfaceWaterAbsorption(state));
        return addCold(world, pos, state, SNOW_SAMPLE_COLD);
    }

    public static double hydraulicWearFromMovedWater(int movedAmount, double gravityFactor, double pressureFactor) {
        return fluidAmountCubicMeters(movedAmount) * HYDRAULIC_WEAR_PER_CUBIC_METER * gravityFactor * pressureFactor;
    }

    public static double surfaceMoistureFromHydraulicWear(double hydraulicWear) {
        return Math.min(0.35, Math.max(0.0, hydraulicWear) * HYDRAULIC_MOISTURE_PER_WEAR_UNIT);
    }

    public static double trafficWearFromContact(
            double horizontalDistanceMeters,
            double contactAreaSquareMeters,
            double bodyHeightMeters) {
        if (horizontalDistanceMeters <= 0.0 || contactAreaSquareMeters <= 0.0 || bodyHeightMeters <= 0.0) {
            return 0.0;
        }

        return horizontalDistanceMeters * Math.max(0.05, contactAreaSquareMeters) * Math.max(0.35, bodyHeightMeters);
    }

    public static double addHeat(ServerLevel world, BlockPos pos, BlockState state, double heat) {
        if (heat <= 0.0) {
            return heat(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withHeat(entry.heat() + heat)
                .withCold(Math.max(0.0, entry.cold() - heat))
                .withMoisture(Math.max(0.0, entry.moisture() - heat * HEAT_DRYING_RATE))
                .withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.heat();
    }

    public static double addCold(ServerLevel world, BlockPos pos, BlockState state, double cold) {
        if (cold <= 0.0) {
            return cold(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        double removedHeat = Math.min(entry.heat(), cold * COLD_COOLING_RATE);
        entry = entry.withCold(entry.cold() + cold)
                .withHeat(entry.heat() - removedHeat)
                .withLastTick(world.getGameTime());
        double thermalStress = MaterialPhysicsProfiles.thermalShockStress(state, removedHeat);
        entry = entry.withStructuralStress(entry.structuralStress() + thermalStress);
        put(world, pos, entry);
        if (thermalStress > 0.0) {
            ThermalPhysics.tryResolveThermalStress(world, pos, state);
        }
        return entry.cold();
    }

    public static double addMoisture(ServerLevel world, BlockPos pos, BlockState state, double moisture) {
        if (moisture <= 0.0) {
            return moisture(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        double removedHeat = Math.min(entry.heat(), moisture * WATER_COOLING_RATE);
        entry = entry.withMoisture(Math.min(MAX_MOISTURE, entry.moisture() + moisture))
                .withHeat(entry.heat() - removedHeat)
                .withLastTick(world.getGameTime());
        double thermalStress = MaterialPhysicsProfiles.thermalShockStress(state, removedHeat);
        entry = entry.withStructuralStress(entry.structuralStress() + thermalStress);
        put(world, pos, entry);
        if (thermalStress > 0.0) {
            ThermalPhysics.tryResolveThermalStress(world, pos, state);
        }
        return entry.moisture();
    }

    public static double removeMoisture(ServerLevel world, BlockPos pos, BlockState state, double moisture) {
        if (moisture <= 0.0) {
            return moisture(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withMoisture(Math.max(0.0, entry.moisture() - moisture)).withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.moisture();
    }

    public static void applyAmbientSurfaceExchange(
            ServerLevel world,
            BlockPos pos,
            BlockState state,
            float biomeBaseTemperature,
            boolean skyExposed,
            int localHeat) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            clear(world, pos);
            return;
        }

        double materialDrying = MaterialPhysicsProfiles.dryingExposure(state);
        double hotBiomeDrying = Math.max(0.0, biomeBaseTemperature - 0.8) * HOT_BIOME_DRYING_PER_SAMPLE;
        double coldBiomeExposure = Math.max(0.0, 0.15 - biomeBaseTemperature) * COLD_BIOME_EXPOSURE_PER_SAMPLE;
        double skyDrying = skyExposed ? SKY_EXPOSURE_DRYING_PER_SAMPLE : 0.0;
        double heatDrying = Math.max(0, localHeat) * LOCAL_HEAT_DRYING_PER_SAMPLE;
        removeMoisture(world, pos, state, (hotBiomeDrying + skyDrying + heatDrying) * materialDrying);

        if (localHeat > 0) {
            addHeat(world, pos, state, localHeat * LOCAL_HEAT_EXPOSURE_PER_SAMPLE);
        }
        if (coldBiomeExposure > 0.0) {
            addCold(world, pos, state, coldBiomeExposure);
        }
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

    public static double addStructuralStress(ServerLevel world, BlockPos pos, BlockState state, double stress) {
        if (stress <= 0.0) {
            return structuralStress(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withStructuralStress(entry.structuralStress() + stress).withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.structuralStress();
    }

    public static double addSuspendedSediment(ServerLevel world, BlockPos pos, BlockState state, double sedimentKilograms) {
        if (sedimentKilograms <= 0.0) {
            return suspendedSediment(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withSuspendedSedimentKilograms(entry.suspendedSedimentKilograms() + sedimentKilograms)
                .withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.suspendedSedimentKilograms();
    }

    public static double addAshResidue(ServerLevel world, BlockPos pos, BlockState state, double ashKilograms) {
        if (ashKilograms <= 0.0) {
            return ashResidue(world, pos, state);
        }

        ExposureEntry entry = entryFor(world, pos, state);
        entry = entry.withAshResidueKilograms(entry.ashResidueKilograms() + ashKilograms).withLastTick(world.getGameTime());
        put(world, pos, entry);
        return entry.ashResidueKilograms();
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

    public static double heat(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.heat();
    }

    public static double cold(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.cold();
    }

    public static double structuralStress(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.structuralStress();
    }

    public static double suspendedSediment(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.suspendedSedimentKilograms();
    }

    public static double ashResidue(ServerLevel world, BlockPos pos, BlockState state) {
        ExposureEntry entry = currentEntry(world, pos, state);
        return entry == null ? 0.0 : entry.ashResidueKilograms();
    }

    public static double ashGrowthBonus(ServerLevel world, BlockPos pos, BlockState state) {
        return Math.min(0.12, ashResidue(world, pos, state) * 0.015);
    }

    public static boolean canDepositSediment(ServerLevel world, BlockPos pos, BlockState state) {
        return suspendedSediment(world, pos, state) >= SEDIMENT_DEPOSIT_THRESHOLD_KG;
    }

    public static double consumeSuspendedSediment(ServerLevel world, BlockPos pos, BlockState state) {
        double sediment = suspendedSediment(world, pos, state);
        update(world, pos, entry -> entry.withSuspendedSedimentKilograms(0.0));
        return sediment;
    }

    public static void consumeAshResidue(ServerLevel world, BlockPos pos, BlockState state, double ashKilograms) {
        update(world, pos, entry -> entry.withAshResidueKilograms(Math.max(0.0, entry.ashResidueKilograms() - ashKilograms)));
    }

    public static void washAshResidue(ServerLevel world, BlockPos pos, BlockState state, double washAmount) {
        consumeAshResidue(world, pos, state, washAmount);
    }

    public static void clearHeat(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withHeat(0.0));
    }

    public static void clearCold(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withCold(0.0));
    }

    public static void clearHydraulicWear(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withHydraulicWear(0.0));
    }

    public static void clearTrafficWear(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withTrafficWear(0.0));
    }

    public static void clearStructuralStress(ServerLevel world, BlockPos pos) {
        update(world, pos, entry -> entry.withStructuralStress(0.0));
    }

    public static void clear(ServerLevel world, BlockPos pos) {
        Map<Long, ExposureEntry> levelExposure = EXPOSURES.get(world);
        if (levelExposure != null) {
            levelExposure.remove(pos.asLong());
        }
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
        return entry == null ? new ExposureEntry(state, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, world.getGameTime()) : entry;
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
                .withCold(Math.max(0.0, entry.cold() - elapsedTicks * COLD_DECAY_PER_TICK))
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
            double cold,
            double moisture,
            double hydraulicWear,
            double trafficWear,
            double structuralStress,
            double suspendedSedimentKilograms,
            double ashResidueKilograms,
            long lastTick) {
        boolean isEmpty() {
            return heat <= 0.0
                    && cold <= 0.0
                    && moisture <= 0.0
                    && hydraulicWear <= 0.0
                    && trafficWear <= 0.0
                    && structuralStress <= 0.0
                    && suspendedSedimentKilograms <= 0.0
                    && ashResidueKilograms <= 0.0;
        }

        ExposureEntry withHeat(double heat) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withCold(double cold) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withMoisture(double moisture) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withHydraulicWear(double hydraulicWear) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withTrafficWear(double trafficWear) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withStructuralStress(double structuralStress) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withSuspendedSedimentKilograms(double suspendedSedimentKilograms) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withAshResidueKilograms(double ashResidueKilograms) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }

        ExposureEntry withLastTick(long lastTick) {
            return new ExposureEntry(state, heat, cold, moisture, hydraulicWear, trafficWear, structuralStress, suspendedSedimentKilograms, ashResidueKilograms, lastTick);
        }
    }
}
