package com.mhgandhi.chatBridge.chat;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mhgandhi.chatBridge.ChatBridge;
import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.IdentityManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

public final class DiscordChat extends ListenerAdapter implements IChat {
    private static final String WEBHOOK_NAME="ChatBridge";

    private final JavaPlugin plugin;
    private BiConsumer<Identity, String> inboundHandler; // (authorDisplay, content)
    private final IdentityManager identityManager;

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
        this.identityManager = idRes;

        this.token = token;
        this.channelId = channelId;

        onR = onReady;
    }

    @Override
    public void setMessageCallback(BiConsumer<Identity, String> handler) {
        inboundHandler = handler;
    }

    public void start() throws LoginException {
        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.customStatus(ChatBridge.getFormatter().dcServerOnlineStatus()))
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
        identityManager.setJda(jda);

        registerCommands();

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

        if(onR!=null){
            final int timeout = 20*1000;//20sek timeout
            long startTime = System.currentTimeMillis();
            boolean timedOut = false;
            while (webhookClient == null && !timedOut) {//heheheha (dafür komme ich in die hölle) nvm jz mit timeout
                Thread.onSpinWait();
                if(startTime-System.currentTimeMillis()>timeout)timedOut = true;
            }
            if(timedOut)
                plugin.getLogger().warning("Timed out while waiting for webhookClient for "+timeout+"s");
            onR.run();
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

/// /////////////////////////////////////////////////////////////////////////////INBOUND
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent e) {
        if (mirrorChannel == null || !Objects.equals(e.getChannel().getId(), channelId)) return;
        if (e.getAuthor().isBot() || e.isWebhookMessage()) return;

        Message m = e.getMessage();
        String content = sanitizeAndFlatten(m);

        if (content.isBlank()) return;

        Member mb = e.getMember();
        if(mb==null)return;
        //identityManager.upsertDcName(mb.getId(), e.getGuild().getId(), mb.getEffectiveName());

        inboundHandler.accept(identityManager.resolve(mb), content);
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

        // Include referenced message (reply) short hint
        if (m.getReferencedMessage() != null) {
            String refSnippet = m.getReferencedMessage().getContentDisplay();
            if (refSnippet.length() > 60) refSnippet = refSnippet.substring(0, 57) + "...";
            text = "<hover:show_text:'"+refSnippet+"'>(↪)</hover> " + text;
        }

        return text;
    }

    /// //////////////////////////////////////////////////////////////////////OUTBOUND
    @Override
    public void sendMessage(Identity author, String content){
        if(content.isEmpty())return;

        if(author == Identity.server){
            sendViaWebhook("Server",null,content);//todo customizable via config
        }else if(author instanceof Identity.Mc mca){
            sendViaWebhook("[MC] "+identityManager.getMcName(mca), ChatBridge.getFormatter().getMcAvatar(mca), content);
        }else if(author instanceof Identity.Dc dca){
            sendViaWebhook("[MC] "+identityManager.getDcName(dca), null, content);
        }
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

    //////////////////////////////////////////////////////////////////////////////////////////////////////COMMANDS todo seperate class mby?
    private void registerCommands(){//todo global
        String guildId = "842749415978041354"; //todo was für per guild der shit ist per plugin/bot
        if (guildId != null && !guildId.isBlank()) {
            Guild g = jda.getGuildById(guildId);
            if (g != null) {
                g.updateCommands()
                        .addCommands(//todo constants for command names
                                Commands.slash(ChatBridge.getFormatter().dcCmdConnect_name(), ChatBridge.getFormatter().dcCmdConnect_desc())
                                        .addOption(OptionType.STRING, ChatBridge.getFormatter().dcCmdConnectArg_name(), ChatBridge.getFormatter().dcCmdConnectArg_desc(), true),
                                Commands.slash(ChatBridge.getFormatter().dcCmdDisconnect_name(), ChatBridge.getFormatter().dcCmdDisconnect_desc()),
                                Commands.slash(ChatBridge.getFormatter().dcCmdStatus_name(), ChatBridge.getFormatter().dcCmdStatus_desc())
                        ).queue();
            }
        }//todo else global
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        try {
            if(e.getName().equals(ChatBridge.getFormatter().dcCmdConnect_name())){
                handleConnect(e);
            } else if (e.getName().equals(ChatBridge.getFormatter().dcCmdDisconnect_name())) {
                handleDisconnect(e);
            } else if (e.getName().equals(ChatBridge.getFormatter().dcCmdStatus_name())) {
                handleStatus(e);
            }
        } catch (Exception ex) {
            e.reply(ChatBridge.getFormatter().dcCommandError(ex.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent e) throws Exception {
        e.replyEmbeds(ChatBridge.getFormatter().discordStatus( new Identity.Dc(e.getUser().getId()) )).setEphemeral(true).queue();
    }

    private void handleDisconnect(SlashCommandInteractionEvent e) throws Exception {
        Identity.Dc dci = new Identity.Dc(e.getUser().getId());
        identityManager.clearDc(dci);
        e.replyEmbeds( ChatBridge.getFormatter().discordStatus(dci) ).setEphemeral(true).queue();    }

    private void handleConnect(SlashCommandInteractionEvent e) throws Exception {
        OptionMapping option = e.getOption(ChatBridge.getFormatter().dcCmdConnectArg_name());//todo constant for option name
        if (option == null) {
            plugin.getLogger().severe("Idk how but someone managed to execute /connect without args on dc");
            return;
        }

        String raw = option.getAsString().trim();

        Identity.Dc dci = new Identity.Dc(e.getUser().getId());
        Identity.Mc mci;
        try{
            mci = Identity.Mc.fromString(raw);
        } catch (IllegalArgumentException ex) {
            mci = null;
        }

        if(mci!=null){
            identityManager.claimDcMc(dci, mci);

            e.replyEmbeds(ChatBridge.getFormatter().discordStatus(dci)).setEphemeral(true).queue();
        }else{
            e.deferReply(true).queue(hook -> {
                hook.editOriginal(ChatBridge.getFormatter().dcResolvingUUIDStatus()).queue();

                identityManager.resolveMcName(raw).thenAccept(
                        resolvedMci -> {
                            //hook.editOriginal("Done!").queue();
                            hook.deleteOriginal().queue();
                            identityManager.claimDcMc(dci, resolvedMci);
                            try {
                                hook.sendMessageEmbeds(ChatBridge.getFormatter().discordStatus(dci)).setEphemeral(true).queue();
                            } catch (Exception ex) {
                                plugin.getLogger().severe("Ex replying to user after deferring: "+ex.getMessage());
                            }
                        }
                ).exceptionally(
                        ex ->{
                            if(ex instanceof CompletionException ce){
                                if(ce.getCause() instanceof IllegalArgumentException){
                                    hook.editOriginal(ChatBridge.getFormatter().dcPlayerResolve_notExist(raw)).queue();
                                }else{
                                    hook.editOriginal(ChatBridge.getFormatter().dcPlayerResolve_api()).queue();
                                }
                            }else{
                                hook.editOriginal(ChatBridge.getFormatter().dcPlayerResolve_unknown()).queue();
                            }
                            return null;
                        }
                );
            });
        }
    }
}
