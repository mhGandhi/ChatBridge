package com.mhgandhi.chatBridge.core.gateway;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.core.events.*;
import com.mhgandhi.chatBridge.platform.paper.PluginEntryPoint;
import com.mhgandhi.chatBridge.core.Identity;
import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GDeathEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GMessageEvent;
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
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public class DiscordGateway extends ChatGateway implements EventListener {

    private volatile WebhookClient webhookClient;//todo tf is volatile
    private volatile TextChannel mirrorChannel;

    public DiscordGateway(Logger pLogger, EventRouter pRouter, IdentityManager pIdentityManager, WebhookClient pWebhook, TextChannel pTextChannel) {
        super(pLogger, pRouter, pIdentityManager);

        webhookClient = pWebhook;
        mirrorChannel = pTextChannel;

        try{
            mirrorChannel.getGuild().loadMembers(identityManager::upsertDc);
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }

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
            sendViaWebhook(Application.getFormatter().dcServerName(), Application.getFormatter().dcServerAvatarOverride(),content);
        }else if(author instanceof Identity.Mc mca){
            sendViaWebhook(Application.getFormatter().dcMcName(identityManager.getMcName(mca)), Application.getFormatter().getMcAvatar(mca), content);
        }else if(author instanceof Identity.Dc dca){
            sendViaWebhook(identityManager.getDcName(dca), identityManager.getDcAvatar(dca), content);
        }
    }

    private void sendViaWebhook(String username, String avatarUrl, String content) {
        if (webhookClient == null) {
            logger.warning("Webhook not ready, sending msg over Bot");
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
                .queue(null, err ->
                        logger.warning("Failed to send to Discord: " + err.getMessage()));
    }

    @Override
    protected void onJoin(GJoinEvent e) {
        sendMessage(Identity.server, Application.getFormatter().dcPlayerJoined(e.getJoined()));
    }

    @Override
    protected void onLeave(GLeaveEvent e) {
        sendMessage(Identity.server, Application.getFormatter().dcPlayerLeft(e.getLeft()));
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
    protected void onPluginDisable(ServerDisableEvent e) {
        sendMessage(Identity.server, Application.getFormatter().dcPluginDisabled());
    }

    @Override
    protected void onPluginEnable(ServerEnableEvent e) {
        sendMessage(Identity.server, Application.getFormatter().dcPluginEnabled());
    }

    @Override
    protected void onDied(GDeathEvent gde) {
        if(gde.getKiller()!=null){
            if(gde.getDeathMessage()==null){
                sendMessage(Identity.server, Application.getFormatter().dcPlayerKilled(gde.getDied(), gde.getKiller()));
            }else{
                sendMessage(Identity.server, Application.getFormatter().dcPlayerKilled(gde.getDied(), gde.getKiller())+"\n`"
                        +gde.getDeathMessage()+"`");
            }
        }else{
            if(gde.getDeathMessage()==null){
                sendMessage(Identity.server, Application.getFormatter().dcPlayerDied(gde.getDied()));
            }else{
                sendMessage(Identity.server, Application.getFormatter().dcPlayerDied(gde.getDied())+"\n`"
                        +gde.getDeathMessage()+"`");
            }
        }
    }

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
                        Commands.slash(Application.getFormatter().dcCmdConnect_name(), Application.getFormatter().dcCmdConnect_desc())
                                .addOption(OptionType.STRING, Application.getFormatter().dcCmdConnectArg_name(), Application.getFormatter().dcCmdConnectArg_desc(), true),
                        Commands.slash(Application.getFormatter().dcCmdDisconnect_name(), Application.getFormatter().dcCmdDisconnect_desc()),
                        Commands.slash(Application.getFormatter().dcCmdStatus_name(), Application.getFormatter().dcCmdStatus_desc())
                ).queue();
    }

    private void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        if(e.getMember()!=null)
            identityManager.upsertDc(e.getMember());

        try {
            if(e.getName().equals(Application.getFormatter().dcCmdConnect_name())){
                handleConnect(e);
            } else if (e.getName().equals(Application.getFormatter().dcCmdDisconnect_name())) {
                handleDisconnect(e);
            } else if (e.getName().equals(Application.getFormatter().dcCmdStatus_name())) {
                handleStatus(e);
            }
        } catch (Exception ex) {
            e.reply(Application.getFormatter().dcCommandError(ex.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent e) {
        e.replyEmbeds(Application.getFormatter().discordStatus( new Identity.Dc(e.getUser().getId()) )).setEphemeral(true).queue();
    }

    private void handleDisconnect(SlashCommandInteractionEvent e) {
        Identity.Dc dci = new Identity.Dc(e.getUser().getId());
        identityManager.clearDc(dci);
        e.replyEmbeds( Application.getFormatter().discordStatus(dci) ).setEphemeral(true).queue();    }

    private void handleConnect(SlashCommandInteractionEvent e) {
        OptionMapping option = e.getOption(Application.getFormatter().dcCmdConnectArg_name());
        if (option == null) {
            logger.severe("Idk how but someone managed to execute /connect without args on dc");
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

            e.replyEmbeds(Application.getFormatter().discordStatus(dci)).setEphemeral(true).queue();
        }else{
            e.deferReply(true).queue(hook -> {
                hook.editOriginal(Application.getFormatter().dcResolvingUUIDStatus()).queue();

                identityManager.resolveMcName(raw).thenAccept(
                        resolvedMci -> {
                            //hook.editOriginal("Done!").queue();
                            hook.deleteOriginal().queue();
                            identityManager.claimDcMc(dci, resolvedMci);
                            try {
                                hook.sendMessageEmbeds(Application.getFormatter().discordStatus(dci)).setEphemeral(true).queue();
                            } catch (Exception ex) {
                                logger.severe("Ex replying to user after deferring: "+ex.getMessage());
                            }
                        }
                ).exceptionally(
                        ex ->{
                            if(ex instanceof CompletionException ce){
                                if(ce.getCause() instanceof IllegalArgumentException){
                                    hook.editOriginal(Application.getFormatter().dcPlayerResolve_notExist(raw)).queue();
                                }else{
                                    hook.editOriginal(Application.getFormatter().dcPlayerResolve_api()).queue();
                                }
                            }else{
                                hook.editOriginal(Application.getFormatter().dcPlayerResolve_unknown()).queue();
                            }
                            return null;
                        }
                );
            });
        }
    }
}
