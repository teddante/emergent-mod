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
                        Component.translatable("emergent.config.wetness_fire_dampening"),
                        config.wetnessFireDampening)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.wetness_fire_dampening.tooltip"))
                .setSaveConsumer(value -> config.wetnessFireDampening = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.passenger_momentum_transfer"),
                        config.passengerMomentumTransfer)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.passenger_momentum_transfer.tooltip"))
                .setSaveConsumer(value -> config.passengerMomentumTransfer = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.kinetic_impacts"),
                        config.kineticImpacts)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.kinetic_impacts.tooltip"))
                .setSaveConsumer(value -> config.kineticImpacts = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.ballistic_inertia"),
                        config.ballisticInertia)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.ballistic_inertia.tooltip"))
                .setSaveConsumer(value -> config.ballisticInertia = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.universal_warden_summoning"),
                        config.universalWardenSummoning)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.universal_warden_summoning.tooltip"))
                .setSaveConsumer(value -> config.universalWardenSummoning = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.dynamic_experience"),
                        config.dynamicExperience)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.dynamic_experience.tooltip"))
                .setSaveConsumer(value -> config.dynamicExperience = value)
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

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.boundless_enchanting"),
                        config.boundlessEnchanting)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.boundless_enchanting.tooltip"))
                .setSaveConsumer(value -> config.boundlessEnchanting = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.unrestricted_enchantments"),
                        config.unrestrictedEnchantments)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.unrestricted_enchantments.tooltip"))
                .setSaveConsumer(value -> config.unrestrictedEnchantments = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.boundless_brewing"),
                        config.boundlessBrewing)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.boundless_brewing.tooltip"))
                .setSaveConsumer(value -> config.boundlessBrewing = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.smoke_and_fumes"),
                        config.smokeAndFumes)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.smoke_and_fumes.tooltip"))
                .setSaveConsumer(value -> config.smokeAndFumes = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.pressure_explosions"),
                        config.pressureExplosions)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.pressure_explosions.tooltip"))
                .setSaveConsumer(value -> config.pressureExplosions = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.structural_stress"),
                        config.structuralStress)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.structural_stress.tooltip"))
                .setSaveConsumer(value -> config.structuralStress = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.fire_ecology"),
                        config.fireEcology)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.fire_ecology.tooltip"))
                .setSaveConsumer(value -> config.fireEcology = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.water_lava_steam"),
                        config.waterLavaSteam)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.water_lava_steam.tooltip"))
                .setSaveConsumer(value -> config.waterLavaSteam = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.chemistry_reactions"),
                        config.chemistryReactions)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.chemistry_reactions.tooltip"))
                .setSaveConsumer(value -> config.chemistryReactions = value)
                .build());

        features.addEntry(entries.startBooleanToggle(
                        Component.translatable("emergent.config.creature_panic"),
                        config.creaturePanic)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("emergent.config.creature_panic.tooltip"))
                .setSaveConsumer(value -> config.creaturePanic = value)
                .build());

        return builder.build();
    }
}
