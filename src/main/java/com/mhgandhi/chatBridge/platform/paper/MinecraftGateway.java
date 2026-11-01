package com.mhgandhi.chatBridge.platform.paper;

import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.IdentityManager;
import com.mhgandhi.chatBridge.events.*;
import com.mhgandhi.chatBridge.events.gatewayspecific.GDeathEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GMessageEvent;
import com.mhgandhi.chatBridge.gateway.ChatGateway;
import com.mhgandhi.chatBridge.gateway.GatewayMessage;
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

public class MinecraftGateway extends ChatGateway implements Listener, CommandExecutor, TabCompleter {
    private static MinecraftGateway instance = null;

    private final PlainTextComponentSerializer serializer;

    private final boolean sendConnectionReminder;

    public MinecraftGateway(JavaPlugin pPlugin, IdentityManager identityManager, boolean pSendConnectionReminder) {
        super(pPlugin, identityManager);
        if(instance!=null){//todo mby throw sth?
            plugin.getLogger().severe("There may only be one instance of MinecraftGateway; overwriting existing");
        }
        instance = this;

        sendConnectionReminder = pSendConnectionReminder;

        serializer = PlainTextComponentSerializer.plainText();

        for(OfflinePlayer p : Bukkit.getServer().getOfflinePlayers()){
            identityManager.upsertMc(p);
        }
    }
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////SEND
    private void sendMessage(Identity author, String content){
        Component msg = PluginEntryPoint.getFormatter().formatMcMsg(author, content);
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
        sendMessage(Identity.server, PluginEntryPoint.getFormatter().mcPluginDisabled());
    }

    @Override
    protected void onPluginEnable(ServerEnableEvent e) {
        sendMessage(Identity.server, PluginEntryPoint.getFormatter().mcPluginEnabled());
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
        ServerEvent ev = new GMessageEvent(identityManager.resolve(e.getPlayer()),new GatewayMessage(body),this);

        callEvent(ev);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        identityManager.upsertMc(e.getPlayer());

        if(sendConnectionReminder && !identityManager.isLinkedMc(Identity.get(e.getPlayer()))){
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                e.getPlayer().sendMessage(Objects.requireNonNull(PluginEntryPoint.getFormatter().mcConnectionReminder()));
            }, 20L * 3); // 3s delay
        }

        Identity player = identityManager.resolve(e.getPlayer());
        ServerEvent ev = new GJoinEvent(player, this);

        callEvent(ev);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Identity player = identityManager.resolve(e.getPlayer());
        ServerEvent ev = new GLeaveEvent(player, this);

        callEvent(ev);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){//todo killed by etc
        Identity player = identityManager.resolve(e.getPlayer());
        Identity killer = null;

        try{
            var k = e.getEntity().getKiller();
            if(k!=null)
                killer =  identityManager.resolve(k);
        }catch(Exception ignored){}

        String dm;
        if(e.deathMessage()==null){
            dm = null;
        }else{
            dm = serializer.serialize(Objects.requireNonNull(e.deathMessage()));
        }

        callEvent(new GDeathEvent(player, killer, dm, this));
    }


    ///  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////COMMANDS
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {//todo Brigadeer for Config names etc
            sender.sendMessage("Players only.");
            return true;
        }
        Identity.Mc mci = new Identity.Mc(p.getUniqueId());

        try {
            if (cmd.getName().equalsIgnoreCase("status")) {
                p.sendMessage(PluginEntryPoint.getFormatter().minecraftStatus(mci));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("disconnect")) {
                identityManager.clearMc(mci);
                p.sendMessage(PluginEntryPoint.getFormatter().minecraftStatus(mci));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("connect")) {
                if (args.length == 0) {
                    return false;
                }

                String claimId = args[0];
                //todo resolve if name
                Identity.Dc claim = new Identity.Dc(claimId);

                identityManager.claimMcDc(mci,claim);
                p.sendMessage(PluginEntryPoint.getFormatter().minecraftStatus(mci));
            }
        } catch (Exception ex) {
            sender.sendMessage(Component.text(PluginEntryPoint.getFormatter().mcCommandError(ex.getMessage()), NamedTextColor.RED));
            return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String @NotNull [] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (!cmd.getName().equalsIgnoreCase("connect")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        Identity.Mc mci = new Identity.Mc(p.getUniqueId());

        try {
            List<String> claimers = identityManager.claimsOnMc(mci);
            if (claimers.isEmpty()) return Collections.emptyList();

            String prefix = args[0].toLowerCase();
            ArrayList<String> out = new ArrayList<>();
            for (String claim : claimers) {
                //var name = claim.name();
                //if (name.toLowerCase().startsWith(prefix)) out.add(name);
                if (claim.startsWith(prefix)) out.add(claim);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
