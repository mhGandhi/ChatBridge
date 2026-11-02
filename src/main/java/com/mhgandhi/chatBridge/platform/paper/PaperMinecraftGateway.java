package com.mhgandhi.chatBridge.platform.paper;

import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.core.Identity;
import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.events.*;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GDeathEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GMessageEvent;
import com.mhgandhi.chatBridge.core.gateway.ChatGateway;
import com.mhgandhi.chatBridge.core.gateway.GatewayMessage;
import com.mhgandhi.chatBridge.core.gateway.MinecraftGateway;
import com.mhgandhi.chatBridge.platform.paper.implementation.PaperPlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.print.Paper;
import java.util.Objects;
import java.util.logging.Logger;

public class PaperMinecraftGateway extends MinecraftGateway implements Listener {



    private final JavaPlugin plugin;


    public PaperMinecraftGateway(Logger pLogger, EventRouter pRouter, IdentityManager identityManager, boolean pSendConnectionReminder, boolean pWhitelist, JavaPlugin pPlugin) {
        super(pLogger, pRouter, identityManager, pSendConnectionReminder, pWhitelist);

        plugin = pPlugin;

        for(OfflinePlayer p : Bukkit.getServer().getOfflinePlayers()){
            identityManager.upsertMc(new PaperPlayer(p));
        }
    }
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////SEND
    private void sendMessage(Identity author, String content){
        Component msg = Application.getFormatter().formatMcMsg(author, content);
        if(Bukkit.isPrimaryThread()){
            Bukkit.getServer().broadcast(msg);
        }else{
            Bukkit.getScheduler().runTask(plugin, ()->Bukkit.getServer().broadcast(msg) );
        }
    }

    @Override
    protected void onLinkCreated(LinkCreatedEvent e) {
        //todo
    }

    @Override
    protected void onLinkDestroyed(LinkDestroyedEvent e) {
        //todo
    }

    @Override
    protected void onMessage(GMessageEvent e) {
        sendMessage(e.getSender(), e.getMessage().getContent());
    }

    @Override
    protected void onPluginDisable(ServerDisableEvent e) {
        sendMessage(Identity.server, Application.getFormatter().mcPluginDisabled());
    }

    @Override
    protected void onPluginEnable(ServerEnableEvent e) {
        sendMessage(Identity.server, Application.getFormatter().mcPluginEnabled());
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////RECEIVE
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if (e.isCancelled()) return;

        String body = serializer.serialize(e.message());
        chatMessage(new PaperPlayer(e.getPlayer()), body);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        playerJoined(
                new PaperPlayer(e.getPlayer()),
                ()->{
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        e.getPlayer().sendMessage(Objects.requireNonNull(Application.getFormatter().mcConnectionReminder()));
                    }, 20L * 3); // 3s delay
                }
        );
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        playerLeft(new PaperPlayer(e.getPlayer()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){//todo killed by etc
        Player killer = null;
        try{
            killer = e.getEntity().getKiller();
        }catch(Exception ignored){}

        playerDied(new PaperPlayer(e.getPlayer()), killer==null? null : new PaperPlayer(killer), e.deathMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        try{
            boolean allowed = allowPlayer(e.getUniqueId());
            if (!allowed) {
                e.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        Application.getFormatter().whitelistReject()
                );
                plugin.getLogger().fine("Rejecting Player ["+e.getName()+"|"+e.getUniqueId()+"] from joining because the uuid is not claimed");
            }
        } catch (Exception ex) {
            e.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Application.getFormatter().loginUnavailableReject()
            );
            plugin.getLogger().fine("Rejecting Player ["+e.getName()+"|"+e.getUniqueId()+"] from joining because there was an db exception.");
        }
    }
}
