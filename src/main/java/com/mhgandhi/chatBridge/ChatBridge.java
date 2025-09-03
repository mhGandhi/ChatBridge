package com.mhgandhi.chatBridge;

import com.mhgandhi.chatBridge.chat.DiscordChat;
import com.mhgandhi.chatBridge.chat.MinecraftChat;
import com.mhgandhi.chatBridge.storage.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;

public final class ChatBridge extends JavaPlugin {
    public static final int MSG_LIMIT = 2000;//todo implement in the gateways (mby seperate limits?)
    private DiscordChat discordChat;
    private MinecraftChat mcChat;

    private Database db;
    private IdentityManager identityManager;

    private static Formatter formatter;
    public static Formatter getFormatter(){return formatter;}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("discord.token", null);
        String channelId = getConfig().getString("discord.channel_id", null);
        boolean whitelist = getConfig().getBoolean("whitelist.enabled",false);

        formatter = new Formatter(getConfig());

        if (token == null || token.isBlank() || channelId == null || channelId.isBlank()) {
            getLogger().severe("discord.token and discord.channel_id must be set in config.yml. Disabling.");
            getServer().getPluginManager().disablePlugin(this);//todo only change functionality
            return;
        }

        {///////database and iM
            db = new Database(this);
            try {
                db.open();
            } catch (Exception e) {
                abort(e);
                return;
            }
            identityManager = new IdentityManager(db, getLogger());
        }

        {///////////////////////DC CHAT
            Runnable onReady = ()->{//todo abstract mby
                mcChat.sendMessage(Identity.server, "ChatBridge enabled");
                discordChat.sendMessage(Identity.server,"`🟢` **ChatBridge enabled**");
            };

            discordChat = new DiscordChat(this, identityManager, token, channelId, onReady);

            BiConsumer<Identity, String> dcHandler = (author, text) -> {
                if(mcChat==null){
                    //todo err
                    return;
                }

                mcChat.sendMessage(author, text);
            };
            discordChat.setMessageCallback(dcHandler);

            try{
                discordChat.start();
            } catch (Exception e) {
                abort(e);
                return;
            }
        }

        {///////////////////////MC CHAT
            mcChat = new MinecraftChat(this, identityManager);

            BiConsumer<Identity, String> mcChatHandler = (author, text) -> {
                if(discordChat ==null||!discordChat.isReady()){
                    //todo not ready
                    return;
                }

                //todo resolve mentions?
                discordChat.sendMessage(author, text);
            };
            mcChat.setMessageCallback(mcChatHandler);

            getServer().getPluginManager().registerEvents(mcChat, this);

            var mcCmds = new MinecraftLinkCommands(db, discordChat.getJDA());//todo chaos eindämmen wtf soll das
            getCommand("connect").setExecutor(mcCmds);
            getCommand("connect").setTabCompleter(mcCmds);
            getCommand("disconnect").setExecutor(mcCmds);
            getCommand("status").setExecutor(mcCmds);
        }

        if(whitelist){
            PlayerRejecter pr = new PlayerRejecter(this, db);
            getServer().getPluginManager().registerEvents(pr, this);
        }
    }

    private void abort(Exception e){
        getLogger().severe("ChatBridge disabled due to error: " + e.getMessage());
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        mcChat.sendMessage(Identity.server, "ChatBridge disabled");
        discordChat.sendMessage(Identity.server,"`🔴` **ChatBridge disabled**");

        if (discordChat != null) {
            discordChat.stop();
        }

        if(db!=null)db.close();

        formatter = null;
    }
}
