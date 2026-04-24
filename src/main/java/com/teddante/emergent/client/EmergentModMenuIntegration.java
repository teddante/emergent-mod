package com.teddante.emergent.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EmergentModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> FabricLoader.getInstance().isModLoaded("cloth-config")
                ? EmergentClothConfigScreen.create(parent)
                : new AlertScreen(
                        () -> Minecraft.getInstance().setScreen(parent),
                        Component.translatable("emergent.config.title"),
                        Component.translatable("emergent.config.missing_cloth_config"));
    }
}
