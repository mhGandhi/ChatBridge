// src/main/java/your/org/bridge/McChatListener.java
package com.mhgandhi.chatBridge.chat;

import com.mhgandhi.chatBridge.ChatBridge;
import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.IdentityManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public final class MinecraftChat implements Listener, IChat, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private BiConsumer<Identity, String> inboundHandler;
    private final IdentityManager identityManager;

    private final PlainTextComponentSerializer serializer;

    public MinecraftChat(JavaPlugin pPlugin, IdentityManager idRes) {
        this.plugin = pPlugin;
        this.identityManager = idRes;

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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {//refresh meta on join
            identityManager.upsert(e.getPlayer());
        });

        if(inboundHandler==null)return;
        Identity player = identityManager.resolve(e.getPlayer());
        if(player.getDcIdentity()==null){
            inboundHandler.accept(Identity.server,"`➕` **" + player.getMcIdentity().name() + "** joined");//todo carry identity and dont resolve to dc here
        }else{
            inboundHandler.accept(Identity.server,"`➕` **" + player.getDcIdentity().name() + "** joined");//todo carry identity and dont resolve to dc here
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        if(inboundHandler==null)return;

        Identity player = identityManager.resolve(e.getPlayer());
        if(player.getDcIdentity()==null){
            inboundHandler.accept(Identity.server,"`➖` **" + player.getMcIdentity().name() + "** left");//todo carry identity and dont resolve to dc here
        }else{
            inboundHandler.accept(Identity.server,"`➖` **" + player.getDcIdentity().name() + "** left");//todo carry identity and dont resolve to dc here
        }
    }

    public void sendMessage(Identity author, String content){
        Component msg = ChatBridge.getFormatter().formatMcMsg(author, content);

        if(msg !=null){
            if(plugin.isEnabled()){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getServer().broadcast(msg);
                });
            }else{//if plugin is already disabled scheduler won't work hihi
                Bukkit.getServer().broadcast(msg);
            }
        }else{
            plugin.getLogger().severe("Unknown Identity for message: "+content);
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

    private static final Pattern SNOWFLAKE_RE = Pattern.compile("^\\d{16,22}$");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        identityManager.upsert(p);

        try {
            if (cmd.getName().equalsIgnoreCase("status")) {
                p.sendMessage(ChatBridge.getFormatter().minecraftStatus(identityManager.resolve(p)));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("disconnect")) {
                identityManager.clearMc(p.getUniqueId().toString());
                p.sendMessage(ChatBridge.getFormatter().minecraftStatus(identityManager.resolve(p)));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("connect")) {
                if (args.length == 0) {
                    p.sendMessage(ChatBridge.getFormatter().minecraftStatus(identityManager.resolve(p)));
                    return true;
                }
                String raw = String.join(" ", args).trim(); // allow nick with spaces
                // Resolve Discord user: by ID (snowflake) or by name (best-effort, guild search if you have one)
                resolveDiscordUser(raw).thenAccept(optUser -> {
                    try {
                        if (optUser.isEmpty()) {
                            p.sendMessage("§cCould not resolve Discord user from: " + raw);
                            return;
                        }
                        User u = optUser.get();

                        // Upsert DC meta so status cards look nice
                        identityManager.upsert(u);


                        Identity.McIdentity mI = identityManager.resolve(p).getMcIdentity();
                        Identity.DcIdentity dI = identityManager.resolve(u).getDcIdentity();//todo bah

                        identityManager.claim(mI,dI);

                        // Show immediate feedback
                        p.sendMessage(ChatBridge.getFormatter().minecraftStatus(identityManager.resolve(p)));
                    } catch (Exception ex) {
                        p.sendMessage("§cError: " + ex.getMessage());
                    }
                });
                return true;
            }
        } catch (Exception ex) {
            sender.sendMessage("§cError: " + ex.getMessage());
        }
        return true;
    }

    private CompletableFuture<Optional<User>> resolveDiscordUser(String raw) {//todo needs to be somewhere else
        // ID path
        if (SNOWFLAKE_RE.matcher(raw).matches()) {
            try {
                return identityManager.getJda().retrieveUserById(raw).submit().thenApply(Optional::of);
            } catch (Exception e) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        }
        // Name path (VERY heuristic; ideally search in your guild(s))
        // You can replace with guild-specific search for better results
        var hits = identityManager.getJda().getUserCache().stream()
                .filter(u -> u.getName().equalsIgnoreCase(raw))
                .findFirst();
        if (hits.isPresent()) return CompletableFuture.completedFuture(Optional.of(hits.get()));

        // Try a broader retrieve (this will hit API; beware rate limits)
        return identityManager.getJda().retrieveUserById(raw).submit()
                .thenApply(Optional::of)
                .exceptionally(x -> Optional.empty());
    }

    /** Tab complete: suggest Discord accounts already claiming this MC UUID */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (!cmd.getName().equalsIgnoreCase("connect")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();
        try {
            Identity sI = identityManager.resolve(p);
            List<Identity.DcIdentity> claimers = identityManager.incomingClaims(sI.getMcIdentity());
            if (claimers.isEmpty()) return Collections.emptyList();
            var prefix = args[0].toLowerCase();
            ArrayList<String> out = new ArrayList<>();
            for (var d : claimers) {
                var name = d.name();
                var id = d.id();
                if (name.toLowerCase().startsWith(prefix)) out.add(name);
                if (id.startsWith(prefix)) out.add(id);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
