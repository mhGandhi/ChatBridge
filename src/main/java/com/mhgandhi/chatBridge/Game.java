package com.mhgandhi.chatBridge;

import java.util.UUID;

public interface Game {
    Player getPlayer(UUID uuid);

    void kick(UUID pUUId);

    Player getOfflinePlayer(UUID uuid);

    void disableCB();

    void saveWebhookCredentials(long idToSave, String tokenToSave);
}
