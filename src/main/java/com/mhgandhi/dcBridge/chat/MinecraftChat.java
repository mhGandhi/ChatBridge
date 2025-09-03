// src/main/java/your/org/bridge/McChatListener.java
package com.mhgandhi.dcBridge.chat;

import com.mhgandhi.dcBridge.DcBridge;
import com.mhgandhi.dcBridge.Identity;
import com.mhgandhi.dcBridge.IdentityManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;

public final class MinecraftChat implements Listener, IChat {
    private final JavaPlugin plugin;
    private BiConsumer<Identity, String> inboundHandler;
    private final IdentityManager identityResolver;

    private final PlainTextComponentSerializer serializer;

    public MinecraftChat(JavaPlugin pPlugin, IdentityManager idRes) {
        this.plugin = pPlugin;
        this.identityResolver = idRes;

        this.serializer = PlainTextComponentSerializer.plainText();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if (e.isCancelled()) return;
        if(inboundHandler==null)return;

        String body = serializer.serialize(e.message());

        inboundHandler.accept(identityResolver.resolve(e.getPlayer()), body);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {//refresh meta on join
            identityResolver.upsert(e.getPlayer());
        });

        if(inboundHandler==null)return;
        Identity player = identityResolver.resolve(e.getPlayer());
        if(player.getDcIdentity()==null){
            inboundHandler.accept(Identity.server,"`➕` **" + player.getMcIdentity().name() + "** joined");//todo carry identity and dont resolve to dc here
        }else{
            inboundHandler.accept(Identity.server,"`➕` **" + player.getDcIdentity().name() + "** joined");//todo carry identity and dont resolve to dc here
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        if(inboundHandler==null)return;

        Identity player = identityResolver.resolve(e.getPlayer());
        if(player.getDcIdentity()==null){
            inboundHandler.accept(Identity.server,"`➖` **" + player.getMcIdentity().name() + "** left");//todo carry identity and dont resolve to dc here
        }else{
            inboundHandler.accept(Identity.server,"`➖` **" + player.getDcIdentity().name() + "** left");//todo carry identity and dont resolve to dc here
        }
    }

    public void sendMessage(Identity author, String content){
        Component msg = DcBridge.getFormatter().formatMcMsg(author, content);

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
}
