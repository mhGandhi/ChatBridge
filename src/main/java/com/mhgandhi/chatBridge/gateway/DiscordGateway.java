package com.mhgandhi.chatBridge.gateway;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mhgandhi.chatBridge.ChatBridge;
import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.IdentityManager;
import com.mhgandhi.chatBridge.events.LinkCreatedEvent;
import com.mhgandhi.chatBridge.events.LinkDestroyedEvent;
import com.mhgandhi.chatBridge.events.PluginDisableEvent;
import com.mhgandhi.chatBridge.events.PluginEnableEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GMessageEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletionException;

public class DiscordGateway extends ChatGateway implements EventListener {

    private final net.dv8tion.jda.api.JDA jda;
    private volatile WebhookClient webhookClient;
    private volatile TextChannel mirrorChannel;

    protected DiscordGateway(JavaPlugin pPlugin, IdentityManager pIdentityManager, JDA pJda, WebhookClient pWebhook, TextChannel pTextChannel) {
        super(pPlugin, pIdentityManager);

        jda = pJda;
        webhookClient = pWebhook;
        mirrorChannel = pTextChannel;

        jda.addEventListener(this);

        registerCommands();
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {//todo nicer dispatch? add Listener?
        if(genericEvent instanceof SlashCommandInteractionEvent se){
            onSlashCommandInteraction(se);
            return;
        }
        if(genericEvent instanceof GuildMemberJoinEvent gmje){
            onGuildMemberJoin(gmje);
            return;
        }
        if(genericEvent instanceof GuildMemberUpdateNicknameEvent gmune){
            onGuildMemberUpdateNickname(gmune);
            return;
        }
        if(genericEvent instanceof GuildMemberUpdateAvatarEvent gmuae){
            onGuildMemberUpdateAvatar(gmuae);
            return;
        }
        if(genericEvent instanceof MessageReceivedEvent mre){
            onMessageReceived(mre);
        }
    }
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////SEND
    private void sendMessage(Identity author, String content){
        if(content.isEmpty())return;

        if(author == Identity.server){
            sendViaWebhook("Server",null,content);//todo customizable via config
        }else if(author instanceof Identity.Mc mca){
            sendViaWebhook("[MC] "+identityManager.getMcName(mca), ChatBridge.getFormatter().getMcAvatar(mca), content);
        }else if(author instanceof Identity.Dc dca){
            sendViaWebhook(identityManager.getDcName(dca), identityManager.getDcAvatar(dca), content);
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
        mirrorChannel.sendMessage(content)
                .queue(null, err -> plugin.getLogger().warning("Failed to send to Discord: " + err.getMessage()));
    }

    @Override
    protected void onJoin(GJoinEvent e) {
        sendMessage(Identity.server, ChatBridge.getFormatter().dcPlayerJoined(e.getJoined()));
    }

    @Override
    protected void onLeave(GLeaveEvent e) {
        sendMessage(Identity.server, ChatBridge.getFormatter().dcPlayerLeft(e.getLeft()));
    }

    @Override
    protected void onMessage(GMessageEvent e) {
        sendMessage(e.getSender(), e.getMessage().getContent());
    }

    @Override
    protected void onLinkCreated(LinkCreatedEvent e) {
        //todo
    }

    @Override
    protected void onLinkDestroyed(LinkDestroyedEvent e) {
        //todo
    }

    @Override
    protected void onPluginDisable(PluginDisableEvent e) {
        sendMessage(Identity.server, ChatBridge.getFormatter().dcPluginDisabled());
    }

    @Override
    protected void onPluginEnable(PluginEnableEvent e) {
        sendMessage(Identity.server, ChatBridge.getFormatter().dcPluginEnabled());
    }

    //todo death

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////RECEIVE
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        identityManager.upsertDc(event.getMember());
    }

    public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
        identityManager.upsertDc(event.getMember());
    }

    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        identityManager.upsertDc(event.getMember());
    }

    public void onMessageReceived(@NotNull MessageReceivedEvent e) {
        if (mirrorChannel == null || !Objects.equals(e.getChannel().getId(), mirrorChannel.getId())) return;
        if (e.getAuthor().isBot() || e.isWebhookMessage()) return;
        //todo cancel only for this bot and this webhook /\

        Message m = e.getMessage();
        String content = sanitizeAndFlatten(m);

        if (content.isBlank()) return;

        Member mb = e.getMember();
        if(mb==null)return;

        callEvent(new GMessageEvent(identityManager.resolve(mb),new GatewayMessage(content),this));
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

        //todo spoiler hover

        return text;
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////COMMANDS
    private void registerCommands(){//todo global
        Guild g = mirrorChannel.getGuild();
        g.updateCommands()
                .addCommands(
                        Commands.slash(ChatBridge.getFormatter().dcCmdConnect_name(), ChatBridge.getFormatter().dcCmdConnect_desc())
                                .addOption(OptionType.STRING, ChatBridge.getFormatter().dcCmdConnectArg_name(), ChatBridge.getFormatter().dcCmdConnectArg_desc(), true),
                        Commands.slash(ChatBridge.getFormatter().dcCmdDisconnect_name(), ChatBridge.getFormatter().dcCmdDisconnect_desc()),
                        Commands.slash(ChatBridge.getFormatter().dcCmdStatus_name(), ChatBridge.getFormatter().dcCmdStatus_desc())
                ).queue();
    }

    private void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        if(e.getMember()!=null)
            identityManager.upsertDc(e.getMember());

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

    private void handleStatus(SlashCommandInteractionEvent e) {
        e.replyEmbeds(ChatBridge.getFormatter().discordStatus( new Identity.Dc(e.getUser().getId()) )).setEphemeral(true).queue();
    }

    private void handleDisconnect(SlashCommandInteractionEvent e) {
        Identity.Dc dci = new Identity.Dc(e.getUser().getId());
        identityManager.clearDc(dci);
        e.replyEmbeds( ChatBridge.getFormatter().discordStatus(dci) ).setEphemeral(true).queue();    }

    private void handleConnect(SlashCommandInteractionEvent e) {
        OptionMapping option = e.getOption(ChatBridge.getFormatter().dcCmdConnectArg_name());
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
