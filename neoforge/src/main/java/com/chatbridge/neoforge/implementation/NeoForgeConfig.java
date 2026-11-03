package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.Config;

public class NeoForgeConfig implements Config {

    @Override
    public String getString(String pKey, String pFallback) {
        return "";
    }

    @Override
    public boolean getBoolean(String pKey, boolean pFallback) {
        return false;
    }
}