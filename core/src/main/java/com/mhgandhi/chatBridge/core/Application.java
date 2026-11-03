package com.mhgandhi.chatBridge.core;

import com.mhgandhi.chatBridge.core.domain.CommandRegistrar;
import com.mhgandhi.chatBridge.core.domain.Config;
import com.mhgandhi.chatBridge.core.domain.Game;
import com.mhgandhi.chatBridge.core.events.EventRouter;
import com.mhgandhi.chatBridge.core.events.ServerDisableEvent;
import com.mhgandhi.chatBridge.core.events.ServerEnableEvent;
import com.mhgandhi.chatBridge.core.events.ServerEvent;
import com.mhgandhi.chatBridge.core.gateway.ChatGateway;
import com.mhgandhi.chatBridge.core.gateway.DiscordGateway;
import com.mhgandhi.chatBridge.core.storage.Database;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.util.logging.Logger;

public final class Application implements AutoCloseable{
    private Database database;
    private IdentityManager identityManager;
    private static Formatter formatter;
    private final EventRouter eventRouter = new EventRouter();
    private final Logger logger;
    private JDAShell discord;
    private Game game;



    public IdentityManager getIdentityManager(){return identityManager;}
    public Logger getLogger(){return logger;}
    public EventRouter getEventRouter(){return eventRouter;}
    public static Formatter getFormatter(){return formatter;}
    public boolean confWhitelist = false; //todo
    public boolean confConnectionReminders = false;

    private boolean started = false;

    public Application(
            Config config,
            Game pGame,
            CommandRegistrar pCommandRegistrar,
            Logger pLogger
    ){
        this.logger = pLogger;
        this.game = pGame;

        /////// CONFIG
        String token = config.getString("discord.token", null);

        String channelId = config.getString("discord.channel_id", null);

        String savedWebhookIDs = config.getString("discord.webhook_id", null);
        long savedWebhookID = 0L;
        if(savedWebhookIDs != null) {
            try {
                savedWebhookID = Long.parseLong(savedWebhookIDs.trim());
            }catch (NumberFormatException e){
                logger.warning("discord.webhook_id is not a valid number; ignoring saved webhook id.");
            }
        }

        String savedWebhookToken = config.getString("discord.webhook_token", "").trim();

        boolean whitelist = config.getBoolean("whitelist.enabled",false);
        confWhitelist = whitelist; //todo

        confConnectionReminders = config.getBoolean("sendConnectionReminders",true);//todo

        if (token == null || token.isBlank()) {
            logger.severe("Missing discord.token in config.yml (must be the Discord BOT token). Disabling.");
            throw new IllegalArgumentException("Missing discord.token");
        }
        if (channelId == null || channelId.isBlank()) {
            logger.severe("Missing discord.channel_id in config.yml (numeric Discord channel ID). Disabling.");
            throw new IllegalArgumentException("Missing discord.channel_id");
        }
        ///////

        /////// DATABASE
        database = new Database(pGame);
        try {
            database.open();
        } catch (Exception e) {
            //abort(e);
            logger.severe("Database could not open. Disabling.");
            database.close();
            throw new IllegalStateException("Could not open ChatBridge database", e);
        }
        ///////

        /////// IDENTITY MANAGER
        identityManager = new IdentityManager(
                database, logger, pGame, config.getBoolean("whitelist.kick_on_disconnect",false)
        );
        ///////

        /////// FORMATTER
        formatter = new Formatter(config, identityManager);
        ///////

        /////// COMMANDS
        McCommands cmd = new McCommands(identityManager, formatter);
        cmd.registerInto(pCommandRegistrar);
        ///////



        /////// DISCORD
        Runnable onJDAReady = ()->{//todo abstract mby
            try{
                DiscordGateway dg = new DiscordGateway(getLogger(), eventRouter, identityManager, discord.getWebhook(), discord.getMirrorChannel());
                eventRouter.register(dg);

                discord.addDcListener(dg);

                eventRouter.publish(new ServerEnableEvent());
            }catch(Exception e){
                logger.severe("ChatBridge failed during JDA ready initialization:"+ e.getMessage());
                game.disableCB();
            }

        };
        discord = new JDAShell(getLogger(), game, token, channelId, savedWebhookID, savedWebhookToken, onJDAReady);
        ///////
    }

    public void start(){
        if(started)return;
        started = true;

        try {
            discord.start();
        }catch(InvalidTokenException e){
            getLogger().severe("Discord login failed: invalid bot token. " +
                    "Check config.yml: discord.token (BOT token, not client secret).");
            //abort
            game.disableCB();
        }catch (Exception e){
            getLogger().severe("Discord login failed while starting JDA: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            //abort
            game.disableCB();
        }
    }

    @Override
    public void close() throws Exception {
        if(formatter!=null){
            //Todo fix already disabled
            ServerEvent e = new ServerDisableEvent();
            eventRouter.publish(e);
        }

        if (discord != null) {
            discord.stop();
        }
        discord = null;

        if(database!=null)
            database.close();
        database=null;

        formatter = null;
    }

    public void registerGateway(ChatGateway cg){
        if(eventRouter==null)return;
        eventRouter.register(cg);
    }

    /*
    private void abort(Exception e){
        logger.log(java.util.logging.Level.SEVERE,
                "ChatBridge disabled due to error:", e);
        getServer().getPluginManager().disablePlugin(this);
    }
    */
}
