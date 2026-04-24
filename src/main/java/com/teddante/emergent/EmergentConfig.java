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

    public static EmergentConfig get() {
        return INSTANCE;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);

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
}

