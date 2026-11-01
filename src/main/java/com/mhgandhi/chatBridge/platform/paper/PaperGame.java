package com.mhgandhi.chatBridge.platform.paper;

import com.mhgandhi.chatBridge.Game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PaperGame implements Game {
    private final JavaPlugin plugin;

    public PaperGame(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public com.mhgandhi.chatBridge.Player getPlayer(UUID uuid) {
        return new PaperPlayer(Bukkit.getPlayer(uuid));
    }

    @Override
    public void kick(UUID pUUID) {
        Player p = Bukkit.getServer().getPlayer(pUUID);
        if(p!=null){
            Bukkit.getScheduler().runTask(plugin, () ->
                    p.kick( PluginEntryPoint.getFormatter().disconnectKick() )
            );
        }
    }

    @Override
    public com.mhgandhi.chatBridge.Player getOfflinePlayer(UUID uuid) {
        return new PaperPlayer(Bukkit.getOfflinePlayer(uuid));
    }
}
