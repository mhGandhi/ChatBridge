package com.mhgandhi.dcBridge;

import com.mhgandhi.dcBridge.storage.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.*;
import java.util.List;
import java.util.UUID;

public class Formatter {//todo const for format?
    private final MiniMessage mm;


    public Formatter(FileConfiguration pConf){
        mm = MiniMessage.miniMessage();

        //todo hook up config
    }

    public Component formatMcMsg(Identity identity, String msg){
        if(identity==Identity.server){
            return Component
                    .text(msg)
                    .color(TextColor.color(0,255,255));
        }else if(identity.getMcIdentity()!=null){
            return Component
                    .text("[D]")
                    .color(TextColor.color(0,0,255))
                    .append(Component.text(" <"+identity.getMcIdentity().name()+"> "+msg));
        }else if(identity.getDcIdentity()!=null){
            return Component
                    .text("[D]")
                    .color(TextColor.color(0,0,255))
                    .append(Component.text(" @"+identity.getDcIdentity().name()+": "+msg));
        }else{
            return null;
        }
    }

    public Component whitelistReject(){
        return mm.deserialize("Your account is not associated with a discord account yet.\nJoin our Discord and run /connect first.");//todo
    }

    public Component notYetOnlineReject(){
        return mm.deserialize("Login system is temporarily unavailable. Please try again in a moment.");//todo
    }

    public Component minecraftStatus(Database db, UUID mcUuid, String dcIdHint) throws Exception {

        //todo from config
        String tempLinked = "<green>✅ Linked</green>\n" +
                "<hover:show_text:'<yellow><uuid></yellow>'><mcname></hover> <-> " +
                "<hover:show_text:'<yellow><dcid></yellow>'><dcname></hover>";//todo show dc nick?

        String tempNotLinked = "❕ Not linked\n" +
                "Use /connect to link (for comfort do it on discord first)";

        String tempToDc = "❕ Not linked\n" +
                "You are currently claiming <hover:show_text:'<yellow><dcid></yellow>'><dcname></hover>\n" +
                "Do /connect <mcname> in discord to complete the link.";

        String tempFromDc = "❕ Not linked\n" +
                "Your account is currently being claimed by: [<dcclaims>]\n"+
                "Do /connect <any of the claiming discord users> to complete a link.";

        String tempFromBoth = "❕ Not linked\n" +
                "You are currently claiming <hover:show_text:'<yellow><dcid></yellow>'><dcname></hover>\n" +
                "Your account is currently being claimed by: [<dcclaims>]\n"+
                "u know how the command works by now lil bro";

        Database.LinkedRow link = db.getActiveLinkByMc(mcUuid.toString());
        if (link!=null) {
            return mm.deserialize(
                    tempLinked,
                    Placeholder.unparsed("uuid", link.mcUuid()),
                    Placeholder.unparsed("mcname", link.mcName()),
                    Placeholder.unparsed("dcid", link.dcId()),
                    Placeholder.unparsed("dcname", link.dcUsername())//todo centralize variables for config mby
            );
        }

        Database.McRow mc = db.getMc(mcUuid.toString()).orElse(null);
        List<Database.DcRow> dcClaimingMe = db.findPendingDiscordClaimsForMc(mcUuid.toString());

        if(dcClaimingMe.isEmpty()){
            if(mc==null){//no link
                return mm.deserialize(
                        tempNotLinked
                );
            }else{//link to discord
                return mm.deserialize(
                        tempToDc,
                        Placeholder.unparsed("uuid",mc.mcUuid()),
                        Placeholder.unparsed("mcname",mc.mcName())
                );
            }
        }else{
            String claims = "";
            for (int i = 0; i < dcClaimingMe.size(); i++) {
                Database.DcRow r = dcClaimingMe.get(i);
                claims += r.dcUsername();
                if(i<dcClaimingMe.size()-1)claims+=", ";
            }

            if(mc==null){//link from discord
                return mm.deserialize(
                        tempFromDc,
                        Placeholder.unparsed("dcclaims", claims)
                );
            }else{//link from and to discord but not matching
                return mm.deserialize(
                        tempFromBoth,
                        Placeholder.unparsed("dcclaims", claims),
                        Placeholder.unparsed("uuid",mc.mcUuid()),
                        Placeholder.unparsed("mcname",mc.mcName())
                );
            }
        }
    }

    /// ///7/////////////////////////////////////////////////////////////////////////////////////////////////
    public String dcUnableToResolveUUID(String uuid){return "❌ Could not resolve **%s** to a Minecraft UUID.".formatted(uuid);}

    public String dcMissingPlayerArg(){return "Please Enter a Player name or UUID as argument";}

    public String dcCommandError(String error){return "❌ Error: %s".formatted(error);}

    public String dcCmdDesc_connect(){return "Link to a Minecraft account";}

    public String dcCmdDesc_connectArg(){return "Minecraft UUID or player name";}

    public String dcCmdDesc_status(){return "Remove your Discord→MC claim";}

    public String dcCmdDesc_disconnect(){return "Show your link status";}

    public String dcServerOnlineStatus() {return "Server Online!";}

    //todo adjust parameters for status replies mby
    //todo
    public MessageEmbed buildDiscordFeedback(Database db, String dcId) throws Exception {
        var eb = new EmbedBuilder().setTitle("Connection status").setColor(new Color(0x5fb95f));
        var l = db.getActiveLinkByDc(dcId);

        if (l!=null) {
            eb.setDescription("✅ Your Discord is **linked** to this Minecraft account.");
            eb.addField("Minecraft", "**%s** (`%s`)".formatted(l.mcName(), l.mcUuid()), false);
            eb.addField("Discord", "<@%s> — **%s**%s".formatted(l.dcId(), l.dcUsername(),
                    l.dcNick() != null ? " (nick: " + l.dcNick() + ")" : ""), false);
            if (l.mcSkinUrl() != null) eb.setThumbnail(l.mcSkinUrl());
            if (l.dcAvatarUrl() != null) eb.setAuthor(l.dcUsername(), null, l.dcAvatarUrl());
            return eb.build();
        }

        // Not fully linked: show current claims and hints
        var dcRow = db.getDc(dcId).orElse(null);
        String claimed = (dcRow != null) ? dcRow.claimedMcUuid() : null;

        eb.setDescription("❕ Your Discord is **not linked** yet.");
        eb.addField("Your claim", claimed != null ? ("➡ Minecraft UUID: `" + claimed + "`") : "None", false);
        if (dcRow != null && dcRow.avatarUrl() != null) {
            eb.setAuthor(dcRow.dcUsername(), null, dcRow.avatarUrl());
        }

        if (claimed != null) {
            // If MC side also claimed you, it would be active; so hint user to run /connect in MC
            var mc = db.getMc(claimed).orElse(null);
            if (mc != null) {
                eb.addField("Minecraft name", mc.mcName(), true);
                if (mc.skinFaceUrl() != null) eb.setThumbnail(mc.skinFaceUrl());
            }
            eb.addField("Next step", "Run **/connect " + dcId + "** in Minecraft **or** **/connect " + (mc != null ? mc.mcName() : claimed) + "** from Discord to confirm.", false);
        } else {
            eb.addField("How to link", "Use `/connect <mc-uuid or name>` here **or** run `/connect <discord-id or name>` in Minecraft.", false);
        }
        return eb.build();
    }


}
