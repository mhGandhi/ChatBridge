package com.mhgandhi.chatBridge;

import com.mhgandhi.chatBridge.events.PluginDisableEvent;
import com.mhgandhi.chatBridge.events.PluginEnableEvent;
import com.mhgandhi.chatBridge.events.PluginEvent;
import com.mhgandhi.chatBridge.gateway.ChatGateway;
import com.mhgandhi.chatBridge.gateway.DiscordGateway;
import com.mhgandhi.chatBridge.gateway.MinecraftGateway;
import com.mhgandhi.chatBridge.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//todo keep list of IChats; message events with fields message, sender, sourceChat; and like that it would be nicer

public final class ChatBridge extends JavaPlugin {
    //public static final int MSG_LIMIT = 2000;//todo implement msg limit (2000?) in the gateways (mby seperate limits?)
    //private DiscordChat discordChat;
    //private MinecraftChat mcChat;
    private JDAShell jdaShell;
    private List<ChatGateway> gateways;

    private Database db;
    private static Formatter formatter;
    public static Formatter getFormatter(){return formatter;}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("discord.token", null);
        String channelId = getConfig().getString("discord.channel_id", null);
        boolean whitelist = getConfig().getBoolean("whitelist.enabled",false);

        if (token == null || token.isBlank() || channelId == null || channelId.isBlank()) {
            getLogger().severe("discord.token and discord.channel_id must be set in config.yml. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        IdentityManager identityManager;
        {///////database and iM
            db = new Database(this);
            try {
                db.open();
            } catch (Exception e) {
                abort(e);
                return;
            }
            identityManager = new IdentityManager(db, this, getConfig().getBoolean("whitelist.kick_on_disconnect",false));

            formatter = new Formatter(getConfig(), identityManager);
        }

        if(whitelist){
            PlayerRejecter pr = new PlayerRejecter(this, identityManager);
            getServer().getPluginManager().registerEvents(pr, this);
        }

        gateways = new ArrayList<>(2);
        getServer().getPluginManager().registerEvents(
                new Listener() {
                    @EventHandler
                    public void onPluginEvent(PluginEvent e){
                        for(ChatGateway cg : gateways){
                            cg.handlePluginEvent(e);
                        }
                    }
                }
                ,this
        );

        Runnable onJDAReady = ()->{//todo abstract mby
            MinecraftGateway mg = new MinecraftGateway(this, identityManager, getConfig().getBoolean("sendConnectionReminders",true));
            DiscordGateway dg = new DiscordGateway(this, identityManager, jdaShell.getWebhook(), jdaShell.getMirrorChannel());
            gateways.add(mg);
            gateways.add(dg);

            try{
                Objects.requireNonNull(getCommand("connect")).setExecutor(mg);
                Objects.requireNonNull(getCommand("connect")).setTabCompleter(mg);
                Objects.requireNonNull(getCommand("disconnect")).setExecutor(mg);
                Objects.requireNonNull(getCommand("status")).setExecutor(mg);
            } catch (Exception e) {
                //ohh no
            }
            getServer().getPluginManager().registerEvents(mg,this);
            jdaShell.addDcListener(dg);

            callEvent(new PluginEnableEvent(), this);
        };

        jdaShell = new JDAShell(this,token, channelId, onJDAReady);
        jdaShell.start();
    }

    private void abort(Exception e){
        getLogger().severe("ChatBridge disabled due to error: " + e.getMessage());
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        if(formatter!=null){
            //Todo fix already disabled
            PluginEvent e = new PluginDisableEvent();
            for(ChatGateway cg : gateways){
                cg.handlePluginEvent(e);
            }
        }

        if (jdaShell != null) {
            jdaShell.stop();
        }
        jdaShell = null;

        if(db!=null)
            db.close();
        db=null;

        formatter = null;
    }

    //always sync
    public static void callEvent(PluginEvent pe, JavaPlugin pPlugin){
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(pe);
        } else {
            Bukkit.getScheduler().runTask(pPlugin, () ->
                    Bukkit.getPluginManager().callEvent(pe)
            );
        }
    }
}
