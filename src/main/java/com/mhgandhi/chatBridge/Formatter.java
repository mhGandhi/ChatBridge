package com.mhgandhi.chatBridge;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.*;
import java.util.List;

public class Formatter {//todo const for format?
    private final MiniMessage mm;
    private final IdentityManager imgr;


    public Formatter(FileConfiguration pConf, IdentityManager pImgr){
        mm = MiniMessage.miniMessage();
        imgr = pImgr;

        //todo hook up config
    }

    public String mcPluginEnabled(){return "ChatBridge enabled";}

    public String mcPluginDisabled(){return "ChatBridge disabled";}

    public Component formatMcMsg(Identity identity, String msg){
        if(identity==Identity.server){
            return Component
                    .text(msg)
                    .color(NamedTextColor.YELLOW);
        }else if(identity.getMcIdentity()!=null){
            return Component
                    .text("[D]", NamedTextColor.BLUE)
                    .append(Component.text(" <"+identity.getMcIdentity().name()+"> "+msg));
        }else if(identity.getDcIdentity()!=null){
            return Component
                    .text("[D]", NamedTextColor.BLUE)
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

    public Component minecraftStatus(Identity pIdentity) {

        //todo from config
        String tempLinked = "<green>✅ Linked</green>\n" +
                "<hover:show_text:'<yellow><uuid></yellow>'><mcname></hover> <-> " +
                "<hover:show_text:'<yellow><dcid></yellow>'><dcname></hover>";//todo show dc nick?

        String tempNotLinked = "<red>❕ Not linked</red>\n" +
                "Use /connect to link (for comfort do it on discord first)";

        String tempToDc = "<yellow>❕ Not linked</yellow>\n" +
                "You are currently claiming <hover:show_text:'<yellow><dcid></yellow>'><dcname></hover>\n" +
                "Do /connect <mcname> in discord to complete the link.";

        String tempFromDc = "<yellow>❕ Not linked</yellow>\n" +
                "Your account is currently being claimed by: [<dcclaims>]\n"+
                "Do /connect <any of the claiming discord users> to complete a link.";

        String tempFromBoth = "<yellow>❕ Not linked\n</yellow>" +
                "You are currently claiming <hover:show_text:'<yellow><dcid></yellow>'><dcname></hover>\n" +
                "Your account is currently being claimed by: [<dcclaims>]\n"+
                "u know how the command works by now lil bro";

        Identity.McIdentity mcI = pIdentity.getMcIdentity();//todo assert !=null
        Identity.DcIdentity dcI = pIdentity.getDcIdentity();

        if (dcI!=null) {
            return mm.deserialize(
                    tempLinked,
                    Placeholder.unparsed("uuid", mcI.uuid()),
                    Placeholder.unparsed("mcname", mcI.name()),
                    Placeholder.unparsed("dcid", dcI.id()),
                    Placeholder.unparsed("dcname", dcI.name())//todo centralize variables for config mby
            );
        }

        List<Identity.DcIdentity> dcClaimingMe = imgr.incomingClaims(mcI);
        Identity.DcIdentity claiming = imgr.outgoingClaim(mcI);

        if(dcClaimingMe.isEmpty()){
            if(claiming == null){//no link
                return mm.deserialize(
                        tempNotLinked
                );
            }else{//link to discord
                return mm.deserialize(
                        tempToDc,
                        Placeholder.unparsed("dcid", claiming.id()),
                        Placeholder.unparsed("dcname",claiming.name())
                );
            }
        }else{
            String claims = "";
            for (int i = 0; i < dcClaimingMe.size(); i++) {
                Identity.DcIdentity r = dcClaimingMe.get(i);
                claims += r.name();
                if(i<dcClaimingMe.size()-1)claims+=", ";
            }

            if(claiming==null){//link from discord
                return mm.deserialize(
                        tempFromDc,
                        Placeholder.unparsed("dcclaims", claims)
                );
            }else{//link from and to discord but not matching
                return mm.deserialize(
                        tempFromBoth,
                        Placeholder.unparsed("dcclaims", claims),
                        Placeholder.unparsed("dcid", claiming.id()),
                        Placeholder.unparsed("dcname",claiming.name())
                );
            }
        }
    }

    /// ///7/////////////////////////////////////////////////////////////////////////////////////////////////
    public String dcUnableToResolvePlayer(String player){return "❌ Could not resolve **%s** to a Minecraft Player. Check for typos and try joining the server once.".formatted(player);}

    public String dcMissingPlayerArg(){return "Please Enter a Player name or UUID as argument";}

    public String dcCommandError(String error){return "❌ Error: %s".formatted(error);}

    public String dcCmdDesc_connect(){return "Link to a Minecraft account";}

    public String dcCmdDesc_connectArg(){return "Minecraft UUID or player name";}

    public String dcCmdDesc_status(){return "Remove your Discord→MC claim";}

    public String dcCmdDesc_disconnect(){return "Show your link status";}

    public String dcServerOnlineStatus() {return "Server Online!";}

    public String dcPluginEnabled(){return "`🟢` **ChatBridge enabled**";}

    public String dcPluginDisabled(){return "`🔴` **ChatBridge disabled**";}

    public MessageEmbed discordStatus(Identity pIdentity) throws Exception {
        //todo
        Identity.McIdentity mcI = pIdentity.getMcIdentity();
        Identity.DcIdentity dcI = pIdentity.getDcIdentity();//todo assert !=null

        var eb = new EmbedBuilder().setTitle("Connection status").setColor(new Color(0x5fb95f));

        eb.setAuthor(dcI.name(), null, dcI.avatarURL());

        if (mcI != null) {
            eb.setDescription("✅ Your Discord is **linked** to this Minecraft account.");
            eb.addField("Minecraft", "**%s** (`%s`)".formatted(mcI.name(), mcI.uuid()), false);
            eb.addField("Discord", "<@%s> — **%s**".formatted(dcI.id(), dcI.name()), false);
            if (mcI.avatarURL() != null) eb.setThumbnail(mcI.avatarURL());
            return eb.build();
        }

        // Not fully linked: show current claims and hints
        Identity.McIdentity claimed = imgr.outgoingClaim(dcI);



        eb.setDescription("❕ Your Discord is **not linked** yet.");
        eb.addField("Your claim", claimed != null ? ("➡ Minecraft UUID: `" + claimed.uuid() + "`") : "None", false);//todo let people claim uuid even if it doesnt resolve


        if (claimed != null) {

            eb.addField("Minecraft name", claimed.name(), true);
            if (claimed.avatarURL() != null) eb.setThumbnail(claimed.avatarURL());

            eb.addField("Next step", "Run **/connect " + dcI.name() + "** in Minecraft to confirm.", false);
        } else {
            eb.addField("How to link", "Use `/connect <mc-uuid or name>` here.", false);
        }
        return eb.build();
    }


}
