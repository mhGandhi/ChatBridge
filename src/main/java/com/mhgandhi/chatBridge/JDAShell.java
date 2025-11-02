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

    private net.dv8tion.jda.api.JDA jda;
    private volatile WebhookClient webhookClient;
    private volatile TextChannel mirrorChannel;

    public JDAShell(JavaPlugin pPlugin, String pToken, String pChannelId, Runnable pOnReady){
        this.plugin = pPlugin;
        this.onReady = pOnReady;
        this.token = pToken;
        this.channelId = pChannelId;
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
                if(startTime-System.currentTimeMillis()>timeout)timedOut = true;
            }
            if(timedOut)
                plugin.getLogger().warning("Timed out while waiting for webhookClient for "+timeout/1000.0+"s; proceeding without webhook");
            onReady.run();
        }
    }

    private void assertWebhook() throws RuntimeException{
        try {
            mirrorChannel.retrieveWebhooks().queue(webhooks -> {
                Webhook existing = webhooks.stream()
                        .filter(w -> WEBHOOK_NAME.equalsIgnoreCase(w.getName()))
                        .findFirst().orElse(null);

                if (existing != null) {
                    createWebhookClient(existing.getUrl());
                    return;
                }
                mirrorChannel.createWebhook(WEBHOOK_NAME).queue(newHook -> {
                    createWebhookClient(newHook.getUrl());
                    plugin.getLogger().info("Created channel webhook for ChatBridge.");
                }, err -> plugin.getLogger().warning("Failed to create webhook (using bot fallback): " + err.getMessage()));
            }, err -> plugin.getLogger().warning("Failed to list webhooks: " + err.getMessage()));
        } catch (Exception e) {
            plugin.getLogger().warning("Something went wrong during Webhook Connection: "+ e.getMessage());
            throw new RuntimeException("Webhook not connected.");
        }
    }

    private void createWebhookClient(String url) {
        try {
            webhookClient = WebhookClient.withUrl(url);
            plugin.getLogger().info("Webhook client ready.");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not init WebhookClient: " + e.getMessage());
            webhookClient=null;
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
