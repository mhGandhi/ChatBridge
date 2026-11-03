package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.Player;

import java.util.UUID;

public class NeoForgePlayer implements Player {
    @Override
    public String getName() {
        return "";
    }

    @Override
    public UUID getUniqueId() {
        return null;
    }

    @Override
    public boolean hasPlayedBefore() {
        return false;
    }
}
