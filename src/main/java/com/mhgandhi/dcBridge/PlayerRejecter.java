package com.mhgandhi.dcBridge;

import com.mhgandhi.dcBridge.storage.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerRejecter implements Listener {
    private final Database db;
    private final JavaPlugin plugin;

    public PlayerRejecter(JavaPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;

        plugin.getLogger().fine("REJECTING UNCLAIMED PLAYERS");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String uuid = e.getUniqueId().toString();

        try {
            // Database uses a single Connection — guard it if other threads (e.g. JDA) also hit it. todo digga was labert der
            boolean allowed;
            synchronized (db) {
                allowed = db.whitelistAllow(uuid);
            }

            if (!allowed) {
                e.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        DcBridge.getFormatter().whitelistReject()
                );
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Whitelist] DB error for " + uuid + ": " + ex.getMessage());
            e.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    DcBridge.getFormatter().notYetOnlineReject()
            );
        }
    }
}
