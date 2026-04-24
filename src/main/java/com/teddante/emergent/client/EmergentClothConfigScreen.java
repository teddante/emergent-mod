package com.teddante.emergent.client;

import com.teddante.emergent.EmergentConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EmergentClothConfigScreen {
    private EmergentClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        EmergentConfig config = EmergentConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("emergent.config.title"))
                .setSavingRunnable(EmergentConfig::save);

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory presets = builder.getOrCreateCategory(
                Component.translatable("emergent.config.category.presets"));

        presets.addEntry(entries.startTextDescription(
                Component.translatable("emergent.config.preset.description")).build());

        presets.addEntry(entries.startBooleanToggle(
                Component.translatable("emergent.config.preset.vanilla_plus"), false)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("emergent.config.preset.vanilla_plus.tooltip"))
                .setSaveConsumer(value -> applyPresetIfChecked(value, EmergentConfig.Preset.VANILLA_PLUS))
                .build());

        presets.addEntry(entries.startBooleanToggle(
                Component.translatable("emergent.config.preset.realistic"), false)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("emergent.config.preset.realistic.tooltip"))
                .setSaveConsumer(value -> applyPresetIfChecked(value, EmergentConfig.Preset.REALISTIC))
                .build());

        presets.addEntry(entries.startBooleanToggle(
                Component.translatable("emergent.config.preset.chaotic"), false)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("emergent.config.preset.chaotic.tooltip"))
                .setSaveConsumer(value -> applyPresetIfChecked(value, EmergentConfig.Preset.CHAOTIC))
                .build());

        presets.addEntry(entries.startBooleanToggle(
                Component.translatable("emergent.config.preset.hardcore"), false)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("emergent.config.preset.hardcore.tooltip"))
                .setSaveConsumer(value -> applyPresetIfChecked(value, EmergentConfig.Preset.HARDCORE))
                .build());

        ConfigCategory features = builder.getOrCreateCategory(
                Component.translatable("emergent.config.category.features"));

        addToggle(entries, features, "volatile_containers", config.volatileContainers,
                v -> config.volatileContainers = v);
        addToggle(entries, features, "volatile_dropped_items", config.volatileDroppedItems,
                v -> config.volatileDroppedItems = v);
        addToggle(entries, features, "volatile_inventories", config.volatileInventories,
                v -> config.volatileInventories = v);
        addToggle(entries, features, "reactive_creepers", config.reactiveCreepers,
                v -> config.reactiveCreepers = v);
        addToggle(entries, features, "infinite_fire_spread", config.infiniteFireSpread,
                v -> config.infiniteFireSpread = v);
        addToggle(entries, features, "burning_entity_fire_spread", config.burningEntityFireSpread,
                v -> config.burningEntityFireSpread = v);
        addToggle(entries, features, "universal_warden_summoning", config.universalWardenSummoning,
                v -> config.universalWardenSummoning = v);
        addToggle(entries, features, "finite_water_flow", config.finiteWaterFlow,
                v -> config.finiteWaterFlow = v);
        addToggle(entries, features, "rain_accumulation", config.rainAccumulation,
                v -> config.rainAccumulation = v);
        addToggle(entries, features, "hydraulic_erosion", config.hydraulicErosion,
                v -> config.hydraulicErosion = v);
        addToggle(entries, features, "auto_planting", config.autoPlanting,
                v -> config.autoPlanting = v);
        addToggle(entries, features, "smoke_and_fumes", config.smokeAndFumes,
                v -> config.smokeAndFumes = v);
        addToggle(entries, features, "pressure_explosions", config.pressureExplosions,
                v -> config.pressureExplosions = v);
        addToggle(entries, features, "structural_stress", config.structuralStress,
                v -> config.structuralStress = v);
        addToggle(entries, features, "fire_ecology", config.fireEcology,
                v -> config.fireEcology = v);
        addToggle(entries, features, "water_lava_steam", config.waterLavaSteam,
                v -> config.waterLavaSteam = v);
        addToggle(entries, features, "chemistry_reactions", config.chemistryReactions,
                v -> config.chemistryReactions = v);
        addToggle(entries, features, "creature_panic", config.creaturePanic,
                v -> config.creaturePanic = v);

        return builder.build();
    }

    private static void addToggle(ConfigEntryBuilder entries, ConfigCategory category, String key,
            boolean current, java.util.function.Consumer<Boolean> setter) {
        category.addEntry(entries.startBooleanToggle(
                Component.translatable("emergent.config." + key), current)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config." + key + ".tooltip"))
                .setSaveConsumer(setter::accept)
                .build());
    }

    private static void applyPresetIfChecked(boolean checked, EmergentConfig.Preset preset) {
        if (!checked) {
            return;
        }
        EmergentConfig.get().applyPreset(preset);
        EmergentConfig.save();
        // Re-open the screen so toggles reflect the applied preset.
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreen(create(null)));
        }
    }
}
