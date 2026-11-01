package com.mhgandhi.chatBridge;

import java.util.UUID;

public interface Player {
    String getName();
    UUID getUniqueId();
    boolean hasPlayedBefore();
}
