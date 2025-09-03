package com.mhgandhi.chatBridge;

import com.mhgandhi.chatBridge.storage.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class IdentityManager {
    private final Database db;
    private final Logger log;
    private JDA jda;

    public IdentityManager(Database db, Logger log) {
        this.db = db;
        this.log = log;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /** From a Discord Member (has nickname). Only fill MC if ACTIVE link exists. */
    public Identity resolve(Member m) {
        Identity.McIdentity mc = null;
        try {
            var row = db.getActiveLinkByDc(m.getId());
            if (row!=null) {
                mc = new Identity.McIdentity(row.mcUuid(), row.mcName(), row.mcSkinUrl());
            }
        } catch (Exception e) {
            log.fine("resolve(Member): " + e.getMessage());
        }
        // Dc display = prefer nick, else username
        Identity.DcIdentity dc = new Identity.DcIdentity(m.getId(),m.getEffectiveName(),m.getEffectiveAvatarUrl());
        return new Identity(mc, dc);
    }

    /** From a Discord User (no nickname). Only fill MC if ACTIVE link exists. */
    public Identity resolve(User u) {
        Identity.McIdentity mc = null;
        try {
            var row = db.getActiveLinkByDc(u.getId());//todo maybe types for dcId and uuid?
            if (row!=null) {
                mc = new Identity.McIdentity(row.mcUuid(), row.mcName(), row.mcSkinUrl());
            }
        } catch (Exception e) {
            log.fine("resolve(User): " + e.getMessage());
        }
        Identity.DcIdentity dc = new Identity.DcIdentity(u.getId(),u.getName(),u.getEffectiveAvatarUrl());
        return new Identity(mc, dc);
    }

    public Identity resolve(Player p) {
        Identity.DcIdentity dc = null;
        try {
            var row = db.getActiveLinkByMc(p.getUniqueId().toString());
            if (row!=null) {
                // row.dcDisplay already computed by the view todo geht was noch nd so gut
                dc = new Identity.DcIdentity(row.dcId(), row.dcNick(), row.dcAvatarUrl());
            }
        } catch (Exception e) {
            log.fine("resolve(Player): " + e.getMessage());
        }

        Identity.McIdentity mc = new Identity.McIdentity(
                p.getUniqueId().toString(),
                p.getName(),
                "https://mc-heads.net/avatar/" + p.getUniqueId() + "/128");//todo centralize
        return new Identity(mc, dc);
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void upsert(Member m){//todo support different names across different servers
        if(m==null)return;
        try{
            db.upsertDcMeta(m.getId(), m.getUser().getName(), m.getEffectiveName(), m.getEffectiveAvatarUrl());
        } catch (Exception e) {
            //todo
        }
    }

    public void upsert(User u){
        if(u==null)return;
        try{
            db.upsertDcMeta(u.getId(), u.getName(), null, u.getEffectiveAvatarUrl());
        } catch (Exception e) {
            //todo
        }
    }

    public void upsert(Player player) {
        if(player==null)return;
        try{
            db.upsertMcMeta(player.getUniqueId().toString(), player.getName(), "https://mc-heads.net/avatar/" + player.getUniqueId() + "/128");//todo one spot for URL gen
        }catch (Exception e){
            //todo
        }

    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void clearDc(String id){
        try{
            db.clearDcClaim(id);
        } catch (Exception e) {
            //todo
        }
    }

    public void clearMc(String uuid){
        try{
            db.clearMcClaim(uuid);
        } catch (Exception e) {
            //todo
        }
    }

    //todo all these should go
    public Database getDb(){//todo remove and funnel methods instead
        return db;
    }
    public JDA getJda(){return jda;}
    public void setJda(JDA j){jda =j;}

    //todo DiscordIdentity from Id, name, (cached nick or nick in channel?)

    //todo McIdentity from UUID, name

    //todo claim from DcIdentity
    //todo claims on DcIdentity

    //todo claim from McIdentity
    //todo claims on McIdentity

    //todo get nick (eff nm) from (User, Guild), (User, Channel)


    //todo rename?
    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F-]{32,36}$");
    public String resolveMcUuid(String raw) {
        try {
            if (UUID_RE.matcher(raw).matches()) {
                UUID u = UUID.fromString(raw.replaceAll("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5"));
                return u.toString();
            }
        } catch (Exception ignored) {}

        // Fall back to resolving by player name (uses the server’s known players)
        // If the name is online, prefer exact:
        var pl = Bukkit.getPlayerExact(raw);
        if (pl != null) return pl.getUniqueId().toString();

        // Offline cache:
        OfflinePlayer off = Bukkit.getOfflinePlayer(raw);
        if (off != null && off.hasPlayedBefore() || (off != null && off.getUniqueId() != null)) {
            return off.getUniqueId().toString();
        }
        return null;
    }
}
