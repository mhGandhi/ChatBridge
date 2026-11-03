package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.Player;

import java.util.UUID;

public record NeoForgePlayer(
        UUID uniqueId,
        String name,
        boolean playedBefore
) implements Player {

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasPlayedBefore() {
        return playedBefore;
    }
}