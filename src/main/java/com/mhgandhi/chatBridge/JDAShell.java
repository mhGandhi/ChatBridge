package com.mhgandhi.chatBridge;

import club.minnced.discord.webhook.WebhookClient;
import com.mhgandhi.chatBridge.platform.paper.PluginEntryPoint;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class JDAShell extends ListenerAdapter {
    private static final String WEBHOOK_NAME = "ChatBridge";

    private final Runnable onReady;
    private final Logger logger;
    private final Game game;

    private final String token;
    private final String channelId;

    private final long savedWebhookID;
    private final String savedWebhookToken;

    private net.dv8tion.jda.api.JDA jda;
    private volatile WebhookClient webhookClient;
    private volatile TextChannel mirrorChannel;

    public JDAShell(Logger pLogger, Game pGame, String pToken, String pChannelId, long pSavedWebhookID, String pSavedWebhookToken, Runnable pOnReady){
        this.logger = pLogger;
        this.game = pGame;
        this.onReady = pOnReady;
        this.token = pToken;
        this.channelId = pChannelId;
        this.savedWebhookID = pSavedWebhookID;
        this.savedWebhookToken = pSavedWebhookToken;
    }

    public void start() throws Exception{
        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS
                )
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.customStatus(PluginEntryPoint.getFormatter().dcServerOnlineStatus()))
                .addEventListeners(this);

        jda = builder.build();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        TextChannel ch = event.getJDA().getTextChannelById(channelId);
        if (ch == null) {
            logger.severe("Discord channel_id " + channelId + " not found. Disabling plugin.");
            game.disableCB();
            return;
        }
        mirrorChannel = ch;

        try{
            assertWebhook();
        } catch (RuntimeException e) {
            logger.severe("Exception while setting up Webhook: "+e.getMessage());
        }

        if(onReady!=null){
            final int timeout = 8*1000;//8sek timeout
            long startTime = System.currentTimeMillis();
            boolean timedOut = false;
            while (webhookClient == null && !timedOut) {//heheheha (dafür komme ich in die hölle) nvm jz mit timeout
                Thread.onSpinWait();
                if(System.currentTimeMillis() > timeout+startTime) timedOut = true;
            }
            if(timedOut)
                logger.warning("Timed out while waiting for webhookClient for "+timeout/1000.0+"s; proceeding without webhook");

            onReady.run();
        }
    }

    private void assertWebhook() throws RuntimeException {//todo remove hardcoding for release ofc
        if (savedWebhookID != 0L && savedWebhookToken != null && !savedWebhookToken.isBlank()) {
            if(createWebhookClient(savedWebhookID, savedWebhookToken)) {
                return;
            }else{
                logger.warning("Saved webhook credentials invalid; recreating webhook...");
            }
        }else{
            logger.info("No saved webhook credentials found; creating webhook...");
        }

        createWebhookAndClient();
    }

    private void createWebhookAndClient() {
        logger.info("No valid saved webhook found. Creating a new webhook named '" + WEBHOOK_NAME + "'...");

        TextChannel ch = mirrorChannel;
        if (ch == null) {
            logger.severe("mirrorChannel is null; cannot create webhook.");
            return;
        }

        ch.createWebhook(WEBHOOK_NAME).queue((Webhook webhook) -> {
            long id = 0L;
            String token = null;

            try {
                id = webhook.getIdLong();
                token = webhook.getToken(); // token is available on creation
            } catch (Exception e) {
                logger.severe("Webhook created but could not read id/token: " + e.getMessage());
            }

            if (id == 0L || token == null || token.isBlank()) {
                logger.severe("Webhook token missing after creation; cannot initialize WebhookClient.");
                webhookClient = null;
                return;
            }

            final long idToSave = id;
            final String tokenToSave = token;
            // 1) Save credentials to config on Bukkit main thread
            game.saveWebhookCredentials(idToSave, tokenToSave);

            // 2) Init client
            if (createWebhookClient(id, token)) {
                logger.info("Created and connected new webhook successfully (id=" + id + ").");
            } else {
                logger.severe("Created webhook but failed to initialize WebhookClient (id=" + id + ").");
            }

        }, err -> {
            logger.severe("Failed to create webhook (missing Manage Webhooks permission?): " + err.getMessage());
            webhookClient = null;
        });
    }


    private boolean createWebhookClient(long id, String token) {
        try {
            webhookClient = WebhookClient.withId(id, token);
            logger.info("Webhook client ready.");
            return true;
        } catch (Exception e) {
            logger.warning("Could not init WebhookClient [" + id + "|token.len="+token.length()+"]: " + e.getMessage());
            webhookClient = null;
            return false;
        }
    }

    public void stop() {
        if (webhookClient != null) {
            try { webhookClient.close(); } catch (Exception ignored) {}
            webhookClient = null;
        }

        if (jda != null) {
            try {
                jda.shutdownNow();
            } catch (Exception ignored) {}
        }

        mirrorChannel = null;
    }

    public WebhookClient getWebhook(){
        return this.webhookClient;
    }

    public TextChannel getMirrorChannel(){
        return this.mirrorChannel;
    }

    public void addDcListener(EventListener el){
        jda.addEventListener(el);
    }

}
