package com.mhgandhi.chatBridge.paper.implementation;

import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.core.domain.Game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

public class PaperGame implements Game {
    private final JavaPlugin plugin;

    public PaperGame(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public com.mhgandhi.chatBridge.core.domain.Player getPlayer(UUID uuid) {
        var player = Bukkit.getPlayer(uuid);
        return player == null ? null : new PaperPlayer(player);
    }

    @Override
    public void kick(UUID pUUID) {
        Player p = Bukkit.getServer().getPlayer(pUUID);
        if(p!=null){
            Bukkit.getScheduler().runTask(plugin, () ->
                    p.kick( Application.getFormatter().disconnectKick() )
            );
        }
    }

    @Override
    public com.mhgandhi.chatBridge.core.domain.Player getOfflinePlayer(UUID uuid) {
        return new PaperPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    @Override
    public void disableCB() {
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }

    @Override
    public void saveWebhookCredentials(long idToSave, String tokenToSave) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.getConfig().set("discord.webhook_id", Long.toString(idToSave));
                plugin.getConfig().set("discord.webhook_token", tokenToSave);
                plugin.saveConfig();
                plugin.getLogger().info("Saved webhook credentials to config.yml.");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save webhook credentials to config.yml: " + e.getMessage());
            }
        });
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }
}
