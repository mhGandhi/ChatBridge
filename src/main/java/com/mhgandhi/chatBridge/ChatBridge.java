package com.mhgandhi.chatBridge;

import com.mhgandhi.chatBridge.chat.DiscordChat;
import com.mhgandhi.chatBridge.chat.MinecraftChat;
import com.mhgandhi.chatBridge.storage.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.BiConsumer;

//todo keep list of IChats; message events with fields message, sender, sourceChat; and like that it would be nicer

public final class ChatBridge extends JavaPlugin {
    //public static final int MSG_LIMIT = 2000;//todo implement msg limit (2000?) in the gateways (mby seperate limits?)
    private DiscordChat discordChat;
    private MinecraftChat mcChat;

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

        {///////////////////////DC CHAT
            Runnable onReady = ()->{//todo abstract mby
                mcChat.sendMessage(Identity.server, formatter.mcPluginEnabled());
                discordChat.sendMessage(Identity.server, formatter.dcPluginEnabled());
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
            String sc = getConfig().getString("format.minecraftChat.messages.connectionReminder","");
            mcChat = new MinecraftChat(this, identityManager, !sc.isBlank());

            BiConsumer<Identity, String> mcChatHandler = (author, text) -> {
                if(discordChat !=null&&discordChat.isReady()){
                    //todo resolve mentions?
                    discordChat.sendMessage(author, text);
                }
            };
            mcChat.setMessageCallback(mcChatHandler);

            getServer().getPluginManager().registerEvents(mcChat, this);

            try{
                Objects.requireNonNull(getCommand("connect")).setExecutor(mcChat);
                Objects.requireNonNull(getCommand("connect")).setTabCompleter(mcChat);
                Objects.requireNonNull(getCommand("disconnect")).setExecutor(mcChat);
                Objects.requireNonNull(getCommand("status")).setExecutor(mcChat);
            } catch (Exception e) {
                //ohh no
            }
        }
    }

    private void abort(Exception e){
        getLogger().severe("ChatBridge disabled due to error: " + e.getMessage());
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        if(formatter!=null){
            if(mcChat!=null)mcChat.sendMessage(Identity.server, formatter.mcPluginDisabled());
            if(discordChat!=null)discordChat.sendMessage(Identity.server, formatter.dcPluginDisabled());
        }

        if (discordChat != null) {
            discordChat.stop();
        }

        if(db!=null)
            db.close();

        formatter = null;
    }
}
