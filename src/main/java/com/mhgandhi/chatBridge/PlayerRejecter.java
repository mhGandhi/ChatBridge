package com.mhgandhi.chatBridge;

import com.mhgandhi.chatBridge.storage.Database;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PlayerRejecter implements Listener {
    private final IdentityManager imgr;
    private final JavaPlugin plugin;

    public PlayerRejecter(JavaPlugin plugin, IdentityManager pI) {
        this.plugin = plugin;
        this.imgr = pI;

        plugin.getLogger().fine("REJECTING UNCLAIMED PLAYERS");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {//todo fix msg display
        UUID uuid = e.getUniqueId();

        try{
            boolean allowed = !imgr.claimsOnMc(uuid).isEmpty();
            if (!allowed) {
                e.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        ChatBridge.getFormatter().whitelistReject()
                );
                plugin.getLogger().fine("Rejecting Player ["+e.getName()+"|"+uuid+"] from joining because the uuid is not claimed");
            }
        } catch (Exception ex) {
            e.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatBridge.getFormatter().loginUnavailableReject()
            );
            plugin.getLogger().fine("Rejecting Player ["+e.getName()+"|"+uuid+"] there was an db exception.");
        }
    }
}
