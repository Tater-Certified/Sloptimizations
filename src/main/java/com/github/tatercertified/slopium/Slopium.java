package com.github.tatercertified.slopium;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class Slopium implements ModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        if (RamChunkStore.isSupported()) {
            LOGGER.info("Slopium RAM chunk store enabled for {}", System.getProperty("os.name", "unknown"));
        } else {
            LOGGER.info("Slopium RAM chunk store disabled for unsupported OS {}", System.getProperty("os.name", "unknown"));
        }

        TerrainBackendSelector.TerrainBackendState terrainBackend = TerrainBackendSelector.state();
        LOGGER.info("Slopium terrain backend: {} ({})", terrainBackend.backend(), terrainBackend.reason());
    }
}
