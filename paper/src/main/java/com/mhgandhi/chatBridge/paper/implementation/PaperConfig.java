package com.mhgandhi.chatBridge.paper.implementation;

import com.mhgandhi.chatBridge.core.domain.Config;
import org.bukkit.configuration.file.FileConfiguration;

public record PaperConfig(FileConfiguration config) implements Config {

    @Override
    public String getString(String pKey, String pFallback) {
        return config().getString(pKey, pFallback);
    }

    @Override
    public boolean getBoolean(String pKey, boolean pFallback) {
        return config().getBoolean(pKey, pFallback);
    }
}
