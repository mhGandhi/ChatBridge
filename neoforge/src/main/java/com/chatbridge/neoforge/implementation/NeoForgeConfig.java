package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.Config;

public final class NeoForgeConfig implements Config {
    public static NeoForgeConfig load() {
        // Load the local config file.
    }

    @Override
    public String getString(String key, String fallback) {
        // Read a value.
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        // Read a value.
    }

    public void saveWebhookCredentials(long id, String token) {
        // Persist locally. Do not synchronize credentials to clients.
    }
}