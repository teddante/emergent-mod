package com.teddante.emergent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class EmergentConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "emergent.json";

    private static EmergentConfig INSTANCE = new EmergentConfig();

    // Existing features
    public boolean volatileContainers = true;
    public boolean volatileDroppedItems = true;
    public boolean volatileInventories = true;
    public boolean reactiveCreepers = true;
    public boolean infiniteFireSpread = true;
    public boolean burningEntityFireSpread = true;
    public boolean wetnessFireDampening = true;
    public boolean passengerMomentumTransfer = true;
    public boolean kineticImpacts = true;
    public boolean ballisticInertia = true;
    public boolean universalWardenSummoning = true;
    public boolean dynamicExperience = true;
    public boolean finiteWaterFlow = true;
    public boolean rainAccumulation = true;
    public boolean hydraulicErosion = true;
    public boolean autoPlanting = true;
    public boolean materialReactions = true;
    public boolean boundlessEnchanting = true;
    public boolean unrestrictedEnchantments = true;
    public boolean boundlessBrewing = true;

    public enum Preset {
        CUSTOM,
        VANILLA_PLUS,
        REALISTIC,
        CHAOTIC,
        HARDCORE_PHYSICS;

        public String translationKey() {
            return "emergent.config.preset." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // New features
    public boolean smokeAndFumes = true;
    public boolean pressureExplosions = true;
    public boolean structuralStress = true;
    public boolean fireEcology = true;
    public boolean waterLavaSteam = true;
    public boolean chemistryReactions = true;
    public boolean creaturePanic = true;

    public static EmergentConfig get() {
        return INSTANCE;
    }

    public static void save() {
        save(configPath());
    }

    public void applyPreset(Preset preset) {
        switch (preset) {
            case VANILLA_PLUS -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = false;
                reactiveCreepers = true;
                infiniteFireSpread = false;
                burningEntityFireSpread = false;
                wetnessFireDampening = true;
                passengerMomentumTransfer = false;
                kineticImpacts = false;
                ballisticInertia = false;
                universalWardenSummoning = false;
                dynamicExperience = false;
                finiteWaterFlow = false;
                rainAccumulation = false;
                hydraulicErosion = false;
                autoPlanting = true;
                materialReactions = true;
                boundlessEnchanting = true;
                unrestrictedEnchantments = true;
                boundlessBrewing = true;
                smokeAndFumes = false;
                pressureExplosions = false;
                structuralStress = false;
                fireEcology = true;
                waterLavaSteam = true;
                chemistryReactions = false;
                creaturePanic = true;
            }
            case REALISTIC -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = true;
                reactiveCreepers = true;
                infiniteFireSpread = false;
                burningEntityFireSpread = true;
                wetnessFireDampening = true;
                passengerMomentumTransfer = true;
                kineticImpacts = true;
                ballisticInertia = true;
                universalWardenSummoning = false;
                dynamicExperience = true;
                finiteWaterFlow = true;
                rainAccumulation = true;
                hydraulicErosion = true;
                autoPlanting = true;
                materialReactions = true;
                boundlessEnchanting = true;
                unrestrictedEnchantments = true;
                boundlessBrewing = true;
                smokeAndFumes = true;
                pressureExplosions = true;
                structuralStress = true;
                fireEcology = true;
                waterLavaSteam = true;
                chemistryReactions = true;
                creaturePanic = true;
            }
            case CHAOTIC -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = true;
                reactiveCreepers = true;
                infiniteFireSpread = true;
                burningEntityFireSpread = true;
                wetnessFireDampening = true;
                passengerMomentumTransfer = true;
                kineticImpacts = true;
                ballisticInertia = true;
                universalWardenSummoning = true;
                dynamicExperience = true;
                finiteWaterFlow = false;
                rainAccumulation = true;
                hydraulicErosion = true;
                autoPlanting = true;
                materialReactions = true;
                boundlessEnchanting = true;
                unrestrictedEnchantments = true;
                boundlessBrewing = true;
                smokeAndFumes = true;
                pressureExplosions = true;
                structuralStress = true;
                fireEcology = true;
                waterLavaSteam = true;
                chemistryReactions = true;
                creaturePanic = true;
            }
            case HARDCORE_PHYSICS -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = true;
                reactiveCreepers = true;
                infiniteFireSpread = true;
                burningEntityFireSpread = true;
                wetnessFireDampening = true;
                passengerMomentumTransfer = true;
                kineticImpacts = true;
                ballisticInertia = true;
                universalWardenSummoning = true;
                dynamicExperience = true;
                finiteWaterFlow = true;
                rainAccumulation = true;
                hydraulicErosion = true;
                autoPlanting = true;
                materialReactions = true;
                boundlessEnchanting = true;
                unrestrictedEnchantments = true;
                boundlessBrewing = true;
                smokeAndFumes = true;
                pressureExplosions = true;
                structuralStress = true;
                fireEcology = true;
                waterLavaSteam = true;
                chemistryReactions = true;
                creaturePanic = true;
            }
            case CUSTOM -> {
            }
        }
    }

    public static void load() {
        Path path = configPath();

        if (Files.notExists(path)) {
            INSTANCE = new EmergentConfig();
            save(path);
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            EmergentConfig loaded = GSON.fromJson(reader, EmergentConfig.class);
            INSTANCE = loaded == null ? new EmergentConfig() : loaded;
            save(path);
        } catch (IOException | JsonSyntaxException e) {
            Emergent.LOGGER.warn("Failed to load {}, using defaults.", path, e);
            INSTANCE = new EmergentConfig();
        }
    }

    private static void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            Emergent.LOGGER.warn("Failed to write {}", path, e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }
}
