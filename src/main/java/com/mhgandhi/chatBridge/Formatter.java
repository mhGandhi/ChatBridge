package com.mhgandhi.chatBridge;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class Formatter {
    private final MiniMessage mm;
    private final IdentityManager imgr;

    // -------------------- Minecraft Chat: general messages --------------------
    private final String tPluginEnabled;
    private final String tPluginDisabled;
    private final String tServerMsg;
    private final String tLinkedMsg;
    private final String tUnlinkedMsg;

    // -------------------- Minecraft Chat: join/whitelist/login ----------------
    private final String tWhitelistReject;
    private final String tUnavailableReject;

    // -------------------- Minecraft Chat: command error -----------------------
    private final String tMcCmdError; // format: "Error: %s"

    // -------------------- Minecraft Chat: status response ---------------------
    // Uses MiniMessage placeholders <dcid> and <dcclaims> like your current method
    private final String tMcStatus_linked;         // "✅ Linked to <dcid>"
    private final String tMcStatus_notLinked;      // general not linked
    private final String tMcStatus_toDc;           // you are claiming <dcid>
    private final String tMcStatus_fromDc;         // claimed by [<dcclaims>]
    private final String tMcStatus_fromBoth;       // claiming + claimed
    private final String tMcConnectReminder;

    // -------------------- Minecraft Chat: avatar URL pattern ------------------
    private final String tAvatarApi; // e.g. "https://mc-heads.net/avatar/%s/128"

    // -------------------- Discord Chat: general messages ----------------------
    private final String sServerOnlineStatus;
    private final String sPlayerJoined;   // format: "%s"
    private final String sPlayerLeft;     // format: "%s"
    private final String sPluginEnabled;
    private final String sPluginDisabled;

    // -------------------- Discord Chat: command boilerplate -------------------
    private final String sDcCmdError; // format: "❌ Error: %s"

    private final String sDcCmdConnect_desc;
    private final String sDcCmdConnect_argDesc;
    private final String sDcCmdDisconnect_desc;
    private final String sDcCmdStatus_desc;

    // -------------------- Discord Chat: /connect resolve messages -------------
    private final String sDcResolve_status;
    private final String sDcResolve_err_notExist; // format: "%s"
    private final String sDcResolve_err_api;
    private final String sDcResolve_err_other;

    // -------------------- Discord Chat: /status embed strings -----------------
    private final String sDcStatus_title;
    private final String sDcStatus_linked;
    private final String sDcStatus_notLinked;
    private final String sDcStatus_info_title;
    private final String sDcStatus_info_content; // format: "%s" (uuid)
    private final String sDcStatus_instr_complete_title;
    private final String sDcStatus_instr_complete_content; // text only
    private final String sDcStatus_instr_mc_title;
    private final String sDcStatus_instr_mc_content; // format: "%s" (user id)

    public Formatter(FileConfiguration pConf, IdentityManager pImgr){
        mm = MiniMessage.miniMessage();
        imgr = pImgr;

        // ---------------- Minecraft Chat: general messages ----------------
        tPluginEnabled = pConf.getString("format.minecraftChat.messages.pluginEnabled",
                "ChatBridge Enabled!");
        tPluginDisabled = pConf.getString("format.minecraftChat.messages.pluginDisabled",
                "ChatBridge Disabled!");
        tServerMsg = pConf.getString("format.minecraftChat.messages.serverMsg",
                "<yellow><message></yellow>");
        tLinkedMsg = pConf.getString("format.minecraftChat.messages.linkedMsg",
                "<hover:show_text:'@<senderdc>'><blue>[D]</blue></hover> <<sender>> <message>");
        tUnlinkedMsg = pConf.getString("format.minecraftChat.messages.unlinkedMsg",
                "<blue>[D]</blue> @<sender>: <message>");
        tMcConnectReminder = pConf.getString("format.minecraftChat.messages.connectionReminder",
                "");


        // ---------------- Minecraft Chat: join/whitelist/login ------------
        tWhitelistReject = pConf.getString("format.minecraftChat.serverJoin.whitelistRejection",
                "Your account is not associated with a discord account yet.\nJoin our Discord and run /connect first.");
        tUnavailableReject = pConf.getString("format.minecraftChat.serverJoin.loginUnavailableRejection",
                "Login system is temporarily unavailable. Try again in a moment or contact Server Admin.");

        // ---------------- Minecraft Chat: command error -------------------
        tMcCmdError = pConf.getString("format.minecraftChat.commands.error_reply",
                "Error: %s");

        // ---------------- Minecraft Chat: status response -----------------
        tMcStatus_linked = pConf.getString("format.minecraftChat.commands.status.response.linked",
                "<green>✅ Linked to</green> <dcid>");
        tMcStatus_notLinked = pConf.getString("format.minecraftChat.commands.status.response.linked_toDc",
                "<red>❕ Not linked</red>\nUse /connect to link (for comfort do it on discord first)");
        tMcStatus_toDc = pConf.getString("format.minecraftChat.commands.status.response.linked_toDc",
                "<yellow>❕ Not linked</yellow>\nYou are currently claiming <dcid>\nDo /connect in discord to complete the link.");
        tMcStatus_fromDc = pConf.getString("format.minecraftChat.commands.status.response.linked_fromDc",
                "<yellow>❕ Not linked</yellow>\nYour account is currently being claimed by: [<dcclaims>]\nDo /connect <any of the claiming discord users> to complete a link.");
        tMcStatus_fromBoth = pConf.getString("format.minecraftChat.commands.status.response.linked_fromBothNoMatch",
                "<yellow>❕ Not linked\n</yellow>You are currently claiming <dcid>\nYour account is currently being claimed by: [<dcclaims>]\nu know how the command works by now lil bro");

        // ---------------- Minecraft Chat: avatar API ----------------------
        tAvatarApi = pConf.getString("format.minecraftChat.avatarApi",
                "https://mc-heads.net/avatar/%s/128");

        // ---------------- Discord Chat: general messages ------------------
        sServerOnlineStatus = pConf.getString("format.discordChat.server_online_status",
                "Server Online!");
        sPlayerJoined = pConf.getString("format.discordChat.messages.join",
                "`➕` **%s** joined the server");
        sPlayerLeft = pConf.getString("format.discordChat.messages.leave",
                "`➖` **%s** left the server");
        sPluginEnabled = pConf.getString("format.discordChat.messages.pluginEnabled",
                "`🟢` **ChatBridge enabled**");
        sPluginDisabled = pConf.getString("format.discordChat.messages.pluginDisabled",
                "`🔴` **ChatBridge disabled**");

        // ---------------- Discord Chat: command boilerplate ---------------
        sDcCmdError = pConf.getString("format.discordChat.commands.error_reply",
                "❌ Error: %s");
        sDcCmdConnect_desc = pConf.getString("format.discordChat.commands.connect.description",
                "Link to a Minecraft account");
        sDcCmdConnect_argDesc = pConf.getString("format.discordChat.commands.connect.arg_description",
                "Minecraft UUID or player name");
        sDcCmdDisconnect_desc = pConf.getString("format.discordChat.commands.disconnect.description",
                "Remove your Discord→MC claim");
        sDcCmdStatus_desc = pConf.getString("format.discordChat.commands.status.description",
                "Show your link status");

        // ---------------- Discord Chat: /connect resolve messages ---------
        sDcResolve_status = pConf.getString("format.discordChat.commands.connect.response.resolving_status",
                "Resolving Player Name to UUID...");
        sDcResolve_err_notExist = pConf.getString("format.discordChat.commands.connect.response.resolve_error.reason_notExist",
                "'%s' does not seem to be a player. Check for typos or enter your UUID directly.");
        sDcResolve_err_api = pConf.getString("format.discordChat.commands.connect.response.resolve_error.reason_api",
                "Could not Resolve name to an UUID due to API problems. Try again later or enter your UUID directly.");
        sDcResolve_err_other = pConf.getString("format.discordChat.commands.connect.response.resolve_error.reason_other",
                "Could not Resolve name to an UUID for an unspecified reason. Cry about it.");

        // ---------------- Discord Chat: /status embed strings -------------
        sDcStatus_title = pConf.getString("format.discordChat.commands.status.response.title",
                "Connection status");
        sDcStatus_linked = pConf.getString("format.discordChat.commands.status.response.linked",
                "✅ Your Discord is **linked** to this Minecraft account.");
        sDcStatus_notLinked = pConf.getString("format.discordChat.commands.status.response.not_linked",
                "❕ Your Discord is **not linked** yet.");
        sDcStatus_info_title = pConf.getString("format.discordChat.commands.status.response.info_field.title",
                "Claimed Minecraft UUID");
        sDcStatus_info_content = pConf.getString("format.discordChat.commands.status.response.info_field.content",
                "[%s]");
        sDcStatus_instr_complete_title = pConf.getString("format.discordChat.commands.status.response.instruction_field.complete.title",
                "How to link");
        sDcStatus_instr_complete_content = pConf.getString("format.discordChat.commands.status.response.instruction_field.complete.content",
                "Use `/connect <mc-uuid or name>` here.");
        sDcStatus_instr_mc_title = pConf.getString("format.discordChat.commands.status.response.instruction_field.minecraft.title",
                "Next step");
        sDcStatus_instr_mc_content = pConf.getString("format.discordChat.commands.status.response.instruction_field.minecraft.content",
                "Run **/connect %s** in Minecraft to confirm. (autocompletes)");


        dcCmdDisconnect_name = pConf.getString("format.discordChat.commands.disconnect.name",
                "disconnect");
        dcCmdStatus_name = pConf.getString("format.discordChat.commands.status.name",
                "status");
        dcCmdConnect_name =pConf.getString("format.discordChat.commands.connect.name",
                "connect");
        dcCmdConnectArg_name = pConf.getString("format.discordChat.commands.connect.arg_name",
                "player");


        tDisconnectKick = pConf.getString("format.minecraftChat.disconnectKickMsg", "cry");
    }


    public String mcCommandError(String message) {
        return tMcCmdError.formatted(message);
    }

    public String mcPluginEnabled(){return tPluginEnabled;}

    public String mcPluginDisabled(){return tPluginDisabled;}

    public Component mcConnectionReminder(){return mm.deserialize(tMcConnectReminder);}

    public Component formatMcMsg(Identity identity, String msg){
        if(msg.isEmpty())return null;

        if(identity == Identity.server){
            return mm.deserialize(
                    tServerMsg,
                    Placeholder.parsed("message",msg)
            );
        }else if(identity instanceof Identity.Mc mca){
            return mm.deserialize(
                    tLinkedMsg,
                    Placeholder.unparsed("sender",imgr.getMcName(mca)),
                    Placeholder.unparsed("senderdc", imgr.getDcName(imgr.getClaimMc(mca))),
                    Placeholder.parsed("message",msg)
            );
        }else if(identity instanceof Identity.Dc dca){
            return mm.deserialize(
                    tUnlinkedMsg,
                    Placeholder.unparsed("sender",imgr.getDcName(dca)),
                    Placeholder.parsed("message",msg)
            );
        }

        return null;
    }

    public Component whitelistReject(){return mm.deserialize(tWhitelistReject);}

    public Component loginUnavailableReject(){return mm.deserialize(tUnavailableReject);}

    public Component minecraftStatus(Identity.Mc mci) {
        boolean linked = imgr.isLinkedMc(mci);
        Identity.Dc claim = imgr.getClaimMc(mci);
        String claimsOn;
        {
            StringBuilder cob = new StringBuilder();
            List<String> claimsonlist = imgr.claimsOnMc(mci);
            if(!claimsonlist.isEmpty()){
                for (int i = 0; i < claimsonlist.size(); i++) {
                    String c = claimsonlist.get(i);
                    cob.append(imgr.getDcName(new Identity.Dc(c)));
                    if(i<claimsonlist.size()-1)
                        cob.append(", ");
                }
            }
            claimsOn = cob.toString();
        }

        if(linked){
            return mm.deserialize(tMcStatus_linked,
                    Placeholder.unparsed("dcid",claim.toString()),
                    Placeholder.unparsed("dcname",imgr.getDcName(claim))
            );
        }

        if(claimsOn.isEmpty()){
            if(claim==null){
                return mm.deserialize(tMcStatus_notLinked);
            }else{
                return mm.deserialize(tMcStatus_toDc,
                        Placeholder.unparsed("dcid",claim.toString()),
                        Placeholder.unparsed("dcname",imgr.getDcName(claim))
                );
            }
        }else{
            if(claim==null){
                return mm.deserialize(tMcStatus_fromDc,
                        Placeholder.unparsed("dcclaims", claimsOn)
                );
            }else{
                return mm.deserialize(tMcStatus_fromBoth,
                        Placeholder.unparsed("dcid",claim.toString()),
                        Placeholder.unparsed("dcclaims", claimsOn),
                        Placeholder.unparsed("dcname",imgr.getDcName(claim))
                );
            }
        }
    }

    /// ///7/////////////////////////////////////////////////////////////////////////////////////////////////
    public String dcResolvingUUIDStatus(){return sDcResolve_status;}
    public String dcPlayerResolve_notExist(String p){return sDcResolve_err_notExist.formatted(p);}
    public String dcPlayerResolve_api(){return sDcResolve_err_api;}
    public String dcPlayerResolve_unknown() {return sDcResolve_err_other;}

    public String dcCommandError(String error){return sDcCmdError.formatted(error);}

    public String dcCmdStatus_desc(){return sDcCmdStatus_desc;}

    private final String dcCmdStatus_name;
    public String dcCmdStatus_name(){return dcCmdStatus_name;}

    public String dcCmdDisconnect_desc(){return sDcCmdDisconnect_desc;}
    private final String dcCmdDisconnect_name;
    public String dcCmdDisconnect_name(){return dcCmdDisconnect_name;}

    public String dcCmdConnect_desc(){return sDcCmdConnect_desc;}
    private final String dcCmdConnect_name;
    public String dcCmdConnect_name(){return dcCmdConnect_name;}
    public String dcCmdConnectArg_desc(){return sDcCmdConnect_argDesc;}
    private final String dcCmdConnectArg_name;
    public String dcCmdConnectArg_name(){return dcCmdConnectArg_name;}


    public String dcServerOnlineStatus() {return sServerOnlineStatus;}

    public String dcPluginEnabled(){return sPluginEnabled;}

    public String dcPluginDisabled(){return sPluginDisabled;}

    public String dcPlayerJoined(Identity i){
        if(i instanceof Identity.Dc dci){
            return sPlayerJoined.formatted(imgr.getDcName(dci));
        }else if(i instanceof Identity.Mc mci){
            return sPlayerJoined.formatted(imgr.getMcName(mci));
        }
        return null;
    }

    public String dcPlayerLeft(Identity i){
        if(i instanceof Identity.Dc dci){
            return sPlayerLeft.formatted(imgr.getDcName(dci));
        }else if(i instanceof Identity.Mc mci){
            return sPlayerLeft.formatted(imgr.getMcName(mci));
        }
        return null;
    }

    public MessageEmbed discordStatus(Identity.Dc dci) {
        var eb = new EmbedBuilder().setTitle(sDcStatus_title);//title
        //eb.setAuthor(dcI.name(), null, dcI.avatarURL());

        boolean linked = imgr.isLinkedDc(dci);
        Identity.Mc claim = imgr.getClaimDc(dci);

        if(linked){
            eb.setDescription(sDcStatus_linked);//linked
            eb.setColor(Color.GREEN);
        }else{
            eb.setDescription(sDcStatus_notLinked);//notlinked
            eb.setColor(Color.GRAY);
        }

        if(claim!=null){//info field
            eb.addField(sDcStatus_info_title, sDcStatus_info_content.formatted(claim), false);
            eb.setThumbnail(getMcAvatar(claim));
            eb.setColor(Color.YELLOW);
        }

        if(!linked){
            if(claim == null){//instruction field
                eb.addField(sDcStatus_instr_complete_title, sDcStatus_instr_complete_content, false);//complete
            }else{
                eb.addField(sDcStatus_instr_mc_title, sDcStatus_instr_mc_content.formatted(dci.toString()), false);//minecraft
            }
        }

        return eb.build();
    }

    public String getMcAvatar(Identity.Mc mci){
        return tAvatarApi.formatted(mci.uuid.toString());
    }

    private final String tDisconnectKick;
    public @Nullable Component disconnectKick() {
        return mm.deserialize(tDisconnectKick);
    }
}