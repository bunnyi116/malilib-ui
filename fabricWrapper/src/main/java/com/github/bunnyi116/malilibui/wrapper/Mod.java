package com.github.bunnyi116.malilibui.wrapper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class Mod implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("malilib-ui")) {
            throw new RuntimeException(
                    "You are using malilib-ui-wrapper to load malilib-ui, but the main malilib-ui module failed to load normally.\n" +
                            "Priority solution: Update malilib-ui-wrapper itself and its mandatory prerequisite MaLiLib to the latest compatible versions.\n" +
                            "Notes:\n" +
                            "1. The core malilib-ui module is embedded inside this malilib-ui-wrapper.jar file.\n" +
                            "2. Make sure the Malilib version matches the Minecraft version you are playing.\n" +
                            "3. Only one embedded version JAR should be loaded for your Minecraft version; the others are ignored by the loader."
            );
        }
    }
}
