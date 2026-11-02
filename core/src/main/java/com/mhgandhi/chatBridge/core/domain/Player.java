package com.mhgandhi.chatBridge.core.domain;

import java.util.UUID;

public interface Player {
    String getName();
    UUID getUniqueId();
    boolean hasPlayedBefore();
}
