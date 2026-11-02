package com.mhgandhi.chatBridge.paper.implementation;

import com.mhgandhi.chatBridge.core.domain.Player;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PaperPlayer implements Player {
    private final OfflinePlayer player;

    public PaperPlayer(@NotNull OfflinePlayer player) {
        this.player = player;
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public boolean hasPlayedBefore() {
        return player.hasPlayedBefore();
    }
}
