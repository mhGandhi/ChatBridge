package com.mhgandhi.chatBridge.core;

import java.util.UUID;

public interface Player {
    String getName();
    UUID getUniqueId();
    boolean hasPlayedBefore();
}
