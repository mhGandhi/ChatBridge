package com.mhgandhi.chatBridge;

import club.minnced.discord.webhook.WebhookClient;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class JDAShell extends ListenerAdapter {
    private static final String WEBHOOK_NAME = "ChatBridge";

    private final JavaPlugin plugin;
    private final Runnable onReady;

    private final String token;
    private final String channelId;

    private final long savedWebhookID;
    private final String savedWebhookToken;

    private net.dv8tion.jda.api.JDA jda;
    private volatile WebhookClient webhookClient;
    private volatile TextChannel mirrorChannel;

    public JDAShell(JavaPlugin pPlugin, String pToken, String pChannelId, long pSavedWebhookID, String pSavedWebhookToken, Runnable pOnReady){
        this.plugin = pPlugin;
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
                .setActivity(Activity.customStatus(ChatBridge.getFormatter().dcServerOnlineStatus()))
                .addEventListeners(this);

        jda = builder.build();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        TextChannel ch = event.getJDA().getTextChannelById(channelId);
        if (ch == null) {
            plugin.getLogger().severe("Discord channel_id " + channelId + " not found. Disabling plugin.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        mirrorChannel = ch;

        try{
            assertWebhook();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Exception while setting up Webhook: "+e.getMessage());
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
                plugin.getLogger().warning("Timed out while waiting for webhookClient for "+timeout/1000.0+"s; proceeding without webhook");

            onReady.run();
        }
    }

    private void assertWebhook() throws RuntimeException {//todo remove hardcoding for release ofc
        if (savedWebhookID != 0L && savedWebhookToken != null && !savedWebhookToken.isBlank()) {
            if(createWebhookClient(savedWebhookID, savedWebhookToken)) {
                return;
            }else{
                plugin.getLogger().warning("Saved webhook credentials invalid; recreating webhook...");
            }
        }else{
            plugin.getLogger().info("No saved webhook credentials found; creating webhook...");
        }

        createWebhookAndClient();
    }

    private void createWebhookAndClient() {
        plugin.getLogger().info("No valid saved webhook found. Creating a new webhook named '" + WEBHOOK_NAME + "'...");

        TextChannel ch = mirrorChannel;
        if (ch == null) {
            plugin.getLogger().severe("mirrorChannel is null; cannot create webhook.");
            return;
        }

        ch.createWebhook(WEBHOOK_NAME).queue((Webhook webhook) -> {
            long id = 0L;
            String token = null;

            try {
                id = webhook.getIdLong();
                token = webhook.getToken(); // token is available on creation
            } catch (Exception e) {
                plugin.getLogger().severe("Webhook created but could not read id/token: " + e.getMessage());
            }

            if (id == 0L || token == null || token.isBlank()) {
                plugin.getLogger().severe("Webhook token missing after creation; cannot initialize WebhookClient.");
                webhookClient = null;
                return;
            }

            final long idToSave = id;
            final String tokenToSave = token;
            // 1) Save credentials to config on Bukkit main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    plugin.getConfig().set("discord.webhook_id", Long.toString(idToSave));
                    plugin.getConfig().set("discord.webhook_token", tokenToSave);
                    plugin.saveConfig();
                    plugin.getLogger().info("Saved webhook credentials to config.yml.");
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to save webhook credentials to config.yml: " + e.getMessage());
                }
            });

            // 2) Init client
            if (createWebhookClient(id, token)) {
                plugin.getLogger().info("Created and connected new webhook successfully (id=" + id + ").");
            } else {
                plugin.getLogger().severe("Created webhook but failed to initialize WebhookClient (id=" + id + ").");
            }

        }, err -> {
            plugin.getLogger().severe("Failed to create webhook (missing Manage Webhooks permission?): " + err.getMessage());
            webhookClient = null;
        });
    }


    private boolean createWebhookClient(long id, String token) {
        try {
            webhookClient = WebhookClient.withId(id, token);
            plugin.getLogger().info("Webhook client ready.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not init WebhookClient [" + id + "|token.len="+token.length()+"]: " + e.getMessage());
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
