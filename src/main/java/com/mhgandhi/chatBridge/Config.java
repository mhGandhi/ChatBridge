package com.mhgandhi.chatBridge;

public interface Config {
    String getString(String pKey, String pFallback);
    boolean getBoolean(String pKey, boolean pFallback);
}
