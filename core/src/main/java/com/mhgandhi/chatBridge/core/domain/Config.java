package com.mhgandhi.chatBridge.core.domain;

public interface Config {
    String getString(String pKey, String pFallback);
    boolean getBoolean(String pKey, boolean pFallback);
}
