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
import com.mhgandhi.chatBridge.platform.paper.implementation.PaperPlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class MinecraftGateway extends ChatGateway implements Listener {
    private static MinecraftGateway instance = null;

    private final PlainTextComponentSerializer serializer;
    private final JavaPlugin plugin;

    private final boolean sendConnectionReminder;

    public MinecraftGateway(Logger pLogger, EventRouter pRouter, IdentityManager identityManager, boolean pSendConnectionReminder, JavaPlugin pPlugin) {
        super(pLogger, pRouter, identityManager);
        if(instance!=null){//todo mby throw sth?
            logger.severe("There may only be one instance of MinecraftGateway; overwriting existing");
        }
        instance = this;

        plugin = pPlugin;

        sendConnectionReminder = pSendConnectionReminder;

        serializer = PlainTextComponentSerializer.plainText();

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
    protected void onJoin(GJoinEvent e) {
        //msg already sent by mc
    }

    @Override
    protected void onLeave(GLeaveEvent e) {
        //msg already sent by mc
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

    @Override
    protected void onDied(GDeathEvent gde) {
        //msg already sent by mc
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////RECEIVE
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if (e.isCancelled()) return;

        String body = serializer.serialize(e.message());
        ServerEvent ev = new GMessageEvent(identityManager.resolve(e.getPlayer().getUniqueId()),new GatewayMessage(body),this);

        callEvent(ev);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        identityManager.upsertMc(new PaperPlayer(e.getPlayer()));

        if(sendConnectionReminder && !identityManager.isLinkedMc(Identity.get(new PaperPlayer(e.getPlayer())))){
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                e.getPlayer().sendMessage(Objects.requireNonNull(Application.getFormatter().mcConnectionReminder()));
            }, 20L * 3); // 3s delay
        }

        Identity player = identityManager.resolve(e.getPlayer().getUniqueId());
        ServerEvent ev = new GJoinEvent(player, this);

        callEvent(ev);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Identity player = identityManager.resolve(e.getPlayer().getUniqueId());
        ServerEvent ev = new GLeaveEvent(player, this);

        callEvent(ev);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){//todo killed by etc
        Identity player = identityManager.resolve(e.getPlayer().getUniqueId());
        Identity killer = null;

        try{
            var k = e.getEntity().getKiller();
            if(k!=null)
                killer =  identityManager.resolve(k.getUniqueId());
        }catch(Exception ignored){}

        String dm;
        if(e.deathMessage()==null){
            dm = null;
        }else{
            dm = serializer.serialize(Objects.requireNonNull(e.deathMessage()));
        }

        callEvent(new GDeathEvent(player, killer, dm, this));
    }
}
