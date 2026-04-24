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

    public boolean volatileContainers = true;
    public boolean volatileDroppedItems = true;
    public boolean volatileInventories = true;
    public boolean reactiveCreepers = true;
    public boolean infiniteFireSpread = true;
    public boolean burningEntityFireSpread = true;
    public boolean universalWardenSummoning = true;
    public boolean finiteWaterFlow = true;
    public boolean rainAccumulation = true;
    public boolean hydraulicErosion = true;
    public boolean autoPlanting = true;
    public boolean materialReactions = true;

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
                universalWardenSummoning = false;
                finiteWaterFlow = false;
                rainAccumulation = false;
                hydraulicErosion = false;
                autoPlanting = true;
                materialReactions = true;
            }
            case REALISTIC -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = true;
                reactiveCreepers = true;
                infiniteFireSpread = false;
                burningEntityFireSpread = true;
                universalWardenSummoning = false;
                finiteWaterFlow = true;
                rainAccumulation = true;
                hydraulicErosion = true;
                autoPlanting = true;
                materialReactions = true;
            }
            case CHAOTIC -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = true;
                reactiveCreepers = true;
                infiniteFireSpread = true;
                burningEntityFireSpread = true;
                universalWardenSummoning = true;
                finiteWaterFlow = false;
                rainAccumulation = true;
                hydraulicErosion = true;
                autoPlanting = true;
                materialReactions = true;
            }
            case HARDCORE_PHYSICS -> {
                volatileContainers = true;
                volatileDroppedItems = true;
                volatileInventories = true;
                reactiveCreepers = true;
                infiniteFireSpread = true;
                burningEntityFireSpread = true;
                universalWardenSummoning = true;
                finiteWaterFlow = true;
                rainAccumulation = true;
                hydraulicErosion = true;
                autoPlanting = true;
                materialReactions = true;
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
