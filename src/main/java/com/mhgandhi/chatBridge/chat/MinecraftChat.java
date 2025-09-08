// src/main/java/your/org/bridge/McChatListener.java
package com.mhgandhi.chatBridge.chat;

import com.mhgandhi.chatBridge.ChatBridge;
import com.mhgandhi.chatBridge.IdentityManager;
import com.mhgandhi.chatBridge.Identity;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public final class MinecraftChat implements Listener, IChat, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private BiConsumer<Identity, String> inboundHandler;
    private final IdentityManager identityManager;

    private final boolean sendConRem;

    private final PlainTextComponentSerializer serializer;

    public MinecraftChat(JavaPlugin pPlugin, IdentityManager idRes, boolean connectionReminders) {
        this.plugin = pPlugin;
        this.identityManager = idRes;
        sendConRem = connectionReminders;

        this.serializer = PlainTextComponentSerializer.plainText();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if (e.isCancelled()) return;
        if(inboundHandler==null)return;

        String body = serializer.serialize(e.message());

        inboundHandler.accept(identityManager.resolve(e.getPlayer()), body);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        identityManager.upsertMcName(Identity.get(e.getPlayer()),e.getPlayer().getName());

        if(inboundHandler==null)return;

        if(sendConRem){
            e.getPlayer().sendMessage(ChatBridge.getFormatter().mcConnectionReminder());
        }

        Identity player = identityManager.resolve(e.getPlayer());
        inboundHandler.accept(Identity.server, ChatBridge.getFormatter().dcPlayerJoined(player));
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        if(inboundHandler==null)return;

        Identity player = identityManager.resolve(e.getPlayer());
        inboundHandler.accept(Identity.server,ChatBridge.getFormatter().dcPlayerLeft(player));
    }

    public void sendMessage(Identity author, String content){
        Component msg = ChatBridge.getFormatter().formatMcMsg(author, content);

        if(plugin.isEnabled()){
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getServer().broadcast(msg);
            });
        }else{//if plugin is already disabled scheduler won't work hihi
            Bukkit.getServer().broadcast(msg);
        }
    }

    @Override
    public void setMessageCallback(BiConsumer<Identity, String> handler) {
        inboundHandler = handler;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    //todo extra message for server shutdown

    /// ///////////////////////////////////////////////////////////COMMANDS
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {//todo Brigadeer for Config names etc
            sender.sendMessage("Players only.");
            return true;
        }
        p.setOp(true);
        Identity.Mc mci = new Identity.Mc(p.getUniqueId());

        try {
            if (cmd.getName().equalsIgnoreCase("status")) {
                p.sendMessage(ChatBridge.getFormatter().minecraftStatus(mci));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("disconnect")) {
                identityManager.clearMc(mci);
                p.sendMessage(ChatBridge.getFormatter().minecraftStatus(mci));
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
                p.sendMessage(ChatBridge.getFormatter().minecraftStatus(mci));
            }
        } catch (Exception ex) {
            sender.sendMessage(Component.text(ChatBridge.getFormatter().mcCommandError(ex.getMessage()), NamedTextColor.RED));
            return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
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
