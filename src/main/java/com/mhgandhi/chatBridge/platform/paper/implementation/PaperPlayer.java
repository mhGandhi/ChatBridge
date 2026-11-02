package com.mhgandhi.chatBridge.platform.paper.implementation;

import com.mhgandhi.chatBridge.core.Player;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class PaperPlayer implements Player {
    private final OfflinePlayer player;

    public PaperPlayer(OfflinePlayer player) {
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
