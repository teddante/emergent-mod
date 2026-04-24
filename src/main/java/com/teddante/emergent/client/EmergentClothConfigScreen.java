package com.teddante.emergent.client;

import com.teddante.emergent.EmergentConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EmergentClothConfigScreen {
    private EmergentClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        EmergentConfig config = EmergentConfig.get();
        final EmergentConfig.Preset[] selectedPreset = {EmergentConfig.Preset.CUSTOM};
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("emergent.config.title"))
                .setSavingRunnable(() -> {
                    config.applyPreset(selectedPreset[0]);
                    EmergentConfig.save();
                });

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory presets = builder.getOrCreateCategory(Component.translatable("emergent.config.category.presets"));
        ConfigCategory features = builder.getOrCreateCategory(Component.translatable("emergent.config.category.features"));

        presets.addEntry(entries.startEnumSelector(
                        Component.translatable("emergent.config.preset"),
                        EmergentConfig.Preset.class,
                        EmergentConfig.Preset.CUSTOM)
                .setDefaultValue(EmergentConfig.Preset.CUSTOM)
                .setEnumNameProvider(preset -> Component.translatable(((EmergentConfig.Preset) preset).translationKey()))
                .setTooltip(Component.translatable("emergent.config.preset.tooltip"))
                .setSaveConsumer(value -> selectedPreset[0] = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.volatile_containers"),
                        config.volatileContainers)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.volatile_containers.tooltip"))
                .setSaveConsumer(value -> config.volatileContainers = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.volatile_dropped_items"),
                        config.volatileDroppedItems)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.volatile_dropped_items.tooltip"))
                .setSaveConsumer(value -> config.volatileDroppedItems = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.volatile_inventories"),
                        config.volatileInventories)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.volatile_inventories.tooltip"))
                .setSaveConsumer(value -> config.volatileInventories = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.reactive_creepers"),
                        config.reactiveCreepers)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.reactive_creepers.tooltip"))
                .setSaveConsumer(value -> config.reactiveCreepers = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.infinite_fire_spread"),
                        config.infiniteFireSpread)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.infinite_fire_spread.tooltip"))
                .setSaveConsumer(value -> config.infiniteFireSpread = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.burning_entity_fire_spread"),
                        config.burningEntityFireSpread)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.burning_entity_fire_spread.tooltip"))
                .setSaveConsumer(value -> config.burningEntityFireSpread = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.universal_warden_summoning"),
                        config.universalWardenSummoning)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.universal_warden_summoning.tooltip"))
                .setSaveConsumer(value -> config.universalWardenSummoning = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.finite_water_flow"),
                        config.finiteWaterFlow)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.finite_water_flow.tooltip"))
                .setSaveConsumer(value -> config.finiteWaterFlow = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.rain_accumulation"),
                        config.rainAccumulation)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.rain_accumulation.tooltip"))
                .setSaveConsumer(value -> config.rainAccumulation = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.hydraulic_erosion"),
                        config.hydraulicErosion)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.hydraulic_erosion.tooltip"))
                .setSaveConsumer(value -> config.hydraulicErosion = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.auto_planting"),
                        config.autoPlanting)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.auto_planting.tooltip"))
                .setSaveConsumer(value -> config.autoPlanting = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.material_reactions"),
                        config.materialReactions)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.material_reactions.tooltip"))
                .setSaveConsumer(value -> config.materialReactions = value)
                .build());

        return builder.build();
    }
}
