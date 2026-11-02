package com.mhgandhi.chatBridge.core;

public interface Config {
    String getString(String pKey, String pFallback);
    boolean getBoolean(String pKey, boolean pFallback);
}
