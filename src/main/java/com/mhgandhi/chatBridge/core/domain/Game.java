package com.mhgandhi.chatBridge.core.domain;

import java.io.File;
import java.util.UUID;

public interface Game {
    Player getPlayer(UUID uuid);

    void kick(UUID pUUId);

    Player getOfflinePlayer(UUID uuid);

    void disableCB();

    void saveWebhookCredentials(long idToSave, String tokenToSave);

    File getDataFolder();
}
