package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.Game;
import com.mhgandhi.chatBridge.core.domain.Player;


import java.io.File;
import java.util.UUID;

public class NeoForgeGame implements Game {

    @Override
    public Player getPlayer(UUID uuid) {
        return null;
    }

    @Override
    public void kick(UUID pUUId) {

    }

    @Override
    public Player getOfflinePlayer(UUID uuid) {
        return null;
    }

    @Override
    public void disableCB() {

    }

    @Override
    public void saveWebhookCredentials(long idToSave, String tokenToSave) {

    }

    @Override
    public File getDataFolder() {
        return null;
    }
}