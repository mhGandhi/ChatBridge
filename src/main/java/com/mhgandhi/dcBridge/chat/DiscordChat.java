package com.mhgandhi.dcBridge.chat;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mhgandhi.dcBridge.DcBridge;
import com.mhgandhi.dcBridge.Identity;
import com.mhgandhi.dcBridge.IdentityManager;
import com.mhgandhi.dcBridge.storage.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class DiscordChat extends ListenerAdapter implements IChat {
    private static final String WEBHOOK_NAME="ChatBridge";

    private final JavaPlugin plugin;
    private BiConsumer<Identity, String> inboundHandler; // (authorDisplay, content)
    private final IdentityManager identityResolver;

    private final String token;
    private final String channelId;

    private net.dv8tion.jda.api.JDA jda;
    private volatile WebhookClient webhookClient;

    private volatile TextChannel mirrorChannel;

    public JDA getJDA(){
        return this.jda;
    }

    private final Runnable onR;

    public DiscordChat(JavaPlugin plugin, IdentityManager idRes, String token, String channelId, Runnable onReady) {
        this.plugin = plugin;
        this.identityResolver = idRes;

        this.token = token;
        this.channelId = channelId;

        onR = onReady;
    }

    @Override
    public void setMessageCallback(BiConsumer<Identity, String> handler) {
        inboundHandler = handler;
    }

    @Override
    public void sendMessage(Identity author, String content){
        if(author==Identity.server){
            sendViaWebhook("Server",null,content);//todo customizable via config
        }else if(author.getDcIdentity()!=null){
            Identity.DcIdentity di = author.getDcIdentity();
            sendViaWebhook(di.name(),di.avatarURL(),content);
        }else if(author.getMcIdentity()!=null){
            Identity.McIdentity mi = author.getMcIdentity();
            sendViaWebhook("[MC]"+mi.name(),mi.avatarURL(),content);
        }else{
            plugin.getLogger().severe("Unknown Identity for message: "+content);
        }
    }

    public void start() throws LoginException {
        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.customStatus(DcBridge.getFormatter().dcServerOnlineStatus()))
                .addEventListeners(this);

        jda = builder.build();
    }

    public void stop() {
        if (webhookClient != null) {
            try { webhookClient.close(); } catch (Exception ignored) {}
            webhookClient = null;
        }
        if (jda != null) {//todo change status
            try {
                jda.shutdownNow();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onReady(@NotNull net.dv8tion.jda.api.events.session.ReadyEvent event) {
        registerCommands();

        TextChannel ch = event.getJDA().getTextChannelById(channelId);
        if (ch == null) {
            plugin.getLogger().severe("Discord channel_id " + channelId + " not found. Disabling plugin.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        mirrorChannel = ch;

        assertWebhook();//todo catch rte?

        //todo postpone until webhook ready
        if(onR!=null){
            onR.run();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent e) {
        // Only mirror from the configured channel
        if (mirrorChannel == null || !Objects.equals(e.getChannel().getId(), channelId)) return;
        // Skip bot/webhook messages
        if (e.getAuthor().isBot() || e.isWebhookMessage()) return;

        Message m = e.getMessage();
        String content = sanitizeAndFlatten(m);

        if (content.isBlank()) return;

        if(e.getMember()!=null){
            inboundHandler.accept(identityResolver.resolve(e.getMember()), content);
        }else{
            inboundHandler.accept(identityResolver.resolve(e.getAuthor()), content);
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


    // add to DiscordGateway.java
    public boolean isReady() {
        return mirrorChannel != null && jda != null && jda.getStatus().isInit();
    }


    private void sendViaWebhook(String username, String avatarUrl, String content) {
        if (webhookClient == null) {
            plugin.getLogger().warning("Webhook not ready, sending msg over Bot");
            sendToDiscord("**" + username + "**: " + content);
            return;
        }

        WebhookMessageBuilder mb = new WebhookMessageBuilder()
                .setUsername(username)
                .setContent(content);

        if(avatarUrl!=null)mb.setAvatarUrl(avatarUrl);
        webhookClient.send(mb.build());
    }

    private void sendToDiscord(String content) {
        if (!isReady()) {
            plugin.getLogger().warning("Discord channel not yet ready; ["+content+"]" );
            return;
        }
        // JDA is async-safe; do not call from Bukkit main thread if you do heavy work, but sending is fine.
        mirrorChannel.sendMessage(content)
                .queue(null, err -> plugin.getLogger().warning("Failed to send to Discord: " + err.getMessage()));
    }


    private String sanitizeAndFlatten(Message m) {
        String text = m.getContentDisplay(); // resolves mentions to names

        // Append attachment URLs (images/files)
        if (!m.getAttachments().isEmpty()) {
            StringJoiner atts = new StringJoiner(" ");
            m.getAttachments().forEach(att -> atts.add(att.getUrl()));
            if (!atts.toString().isBlank()) {
                if (!text.isBlank()) text += " ";
                text += atts;
            }
        }

        // Include referenced message (reply) short hint todo remove?
        if (m.getReferencedMessage() != null) {
            String refAuthor = m.getReferencedMessage().getAuthor().getName();
            String refSnippet = m.getReferencedMessage().getContentDisplay();
            if (refSnippet.length() > 60) refSnippet = refSnippet.substring(0, 57) + "...";
            text = "↪ " + refAuthor + ": " + refSnippet + " | " + text;
        }

        // Hard cap to something sensible (Paper side can handle very long, but keep it tidy)
        if (text.length() > 800) {
            text = text.substring(0, 797) + "...";
        }

        return text;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////COMMANDS todo seperate class mby?
    private void registerCommands(){//todo global
        String guildId = "842749415978041354"; //todo was für per guild der shit ist per plugin/bot
        if (guildId != null && !guildId.isBlank()) {
            Guild g = jda.getGuildById(guildId);
            if (g != null) {
                g.updateCommands()
                        .addCommands(//todo constants for command names
                                Commands.slash("connect", DcBridge.getFormatter().dcCmdDesc_connect())
                                        .addOption(OptionType.STRING, "target", DcBridge.getFormatter().dcCmdDesc_connectArg(), true),
                                Commands.slash("disconnect", DcBridge.getFormatter().dcCmdDesc_status()),
                                Commands.slash("status", DcBridge.getFormatter().dcCmdDesc_disconnect())
                        ).queue();
            }
        }//todo else global
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {//todo
        //always upsert metadata into database on command use
        if(e.getMember()==null){
            identityResolver.upsert(e.getUser());//todo always member? just dont cache name tf
        }else{
            identityResolver.upsert(e.getMember());
        }

        try {
            switch (e.getName()) {
                case "connect" -> handleConnect(e);
                case "disconnect" -> handleDisconnect(e);
                case "status" -> handleStatus(e);
            }
        } catch (Exception ex) {
            e.reply(DcBridge.getFormatter().dcCommandError(ex.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent e) throws Exception {
        String dcId = e.getUser().getId();
        e.replyEmbeds(DcBridge.getFormatter().buildDiscordFeedback(identityResolver.getDb(), dcId)).setEphemeral(true).queue();
    }

    private void handleDisconnect(SlashCommandInteractionEvent e) throws Exception {
        String dcId = e.getUser().getId();
        identityResolver.clearDc(dcId);
        e.replyEmbeds(DcBridge.getFormatter().buildDiscordFeedback(identityResolver.getDb(), dcId)).setEphemeral(true).queue();
    }

    private void handleConnect(SlashCommandInteractionEvent e) throws Exception {
        String dcId = e.getUser().getId();
        var option = e.getOption("target");

        if (option == null) {
            e.reply(DcBridge.getFormatter().dcMissingPlayerArg()).setEphemeral(true).queue();
            return;
        }

        String raw = option.getAsString().trim();
        String mcUuid = identityResolver.resolveMcUuid(raw); // UUID string or name lookup
        if (mcUuid == null) {
            e.reply(DcBridge.getFormatter().dcUnableToResolveUUID(raw)).setEphemeral(true).queue();
            return;
        }

        refreshMcMeta(identityResolver.getDb(), java.util.UUID.fromString(mcUuid));

        identityResolver.getDb().dcClaimsMinecraft(dcId, mcUuid);
        e.replyEmbeds(DcBridge.getFormatter().buildDiscordFeedback(identityResolver.getDb(), dcId)).setEphemeral(true).queue();
    }


    /** Handy to update MC meta when we only know UUID (pulls last known name if online) */
    public static void refreshMcMeta(Database db, UUID mcUuid) {
        // Try to enrich name & skin if you have a skin provider; this keeps it simple
        OfflinePlayer off = Bukkit.getOfflinePlayer(mcUuid);
        var name = off.getName() != null ? off.getName() : "unknown";
        String skin = null; // plug your skin-face provider URL here if you have one todo
        try { db.upsertMcMeta(mcUuid.toString(), name, skin); } catch (Exception ignored) {}
    }
}
