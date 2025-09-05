package com.mhgandhi.chatBridge;

import com.mhgandhi.chatBridge.storage.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
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


    public void claim(Identity.McIdentity m, Identity.DcIdentity d){
        try {
            db.mcClaimsDiscord(m.uuid(), d.id());
        } catch (Exception e) {
            //todo
        }
    }

    public void claim(Identity.DcIdentity d, Identity.McIdentity m){
        try {
            db.dcClaimsMinecraft(d.id(), m.uuid());
        } catch (Exception e) {
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
    public JDA getJda(){return jda;}
    public void setJda(JDA j){jda =j;}


    public Identity.DcIdentity resolveDcId(String pId){//todo build partial Ident
        Database.DcRow row = null;
        try {
            row = db.getDc(pId);
        } catch (Exception e) {
            //todo
        }

        if(row==null)return null;

        return new Identity.DcIdentity(row.dcId(),row.dcUsername(),row.avatarUrl());
    }

    public Identity resolveMcUUID(String pUUID){//todo build partial Ident, resolve to player/offline player?
        Database.LinkedRow row = null;
        try {
            row = db.getActiveLinkByMc(pUUID);
        } catch (Exception e) {
            //todo
        }

        if(row==null){
            Identity.McIdentity mc = null;
            try{
                Database.McRow mr = db.getMc(pUUID);

                if(mr!=null)mc = new Identity.McIdentity(mr.mcUuid(), mr.mcName(), mr.skinFaceUrl());//todo null?
            } catch (Exception e) {
                //todo
            }
            if(mc==null)return null;
            return Identity.of(mc,null);
        }

        Identity.McIdentity mc = new Identity.McIdentity(row.mcUuid(),row.mcName(),row.mcSkinUrl());
        Identity.DcIdentity dc = new Identity.DcIdentity(row.dcId(), row.dcUsername(), row.dcAvatarUrl());

        return Identity.of(mc,dc);
    }


    public Identity.McIdentity outgoingClaim(Identity.DcIdentity pFrom){
        Database.DcRow claim;
        try {
            claim = db.getDc(pFrom.id());
        } catch (Exception e) {
            //todo err
            return null;
        }

        if(claim == null)return null;

        return resolveMcUUID(claim.claimedMcUuid()).getMcIdentity();
    }
    public List<Identity.McIdentity> incomingClaims(Identity.DcIdentity pOn){
        List<Database.McRow> claims;
        try{
            claims = db.findPendingMinecraftClaimsForDc(pOn.id());//todo ts aint implemented (psst)
        } catch (Exception e) {
            //todo err
            return List.of();
        }

        if(claims.isEmpty())return List.of();

        List<Identity.McIdentity> ret = new java.util.ArrayList<>(claims.size());
        for(Database.McRow row : claims){
            ret.add(new Identity.McIdentity(row.mcUuid(),row.mcName(),row.skinFaceUrl()));
        }

        return ret;
    }

    public Identity.DcIdentity outgoingClaim(Identity.McIdentity pFrom){
        Database.McRow claim;
        try {
            claim = db.getMc(pFrom.uuid());
        } catch (Exception e) {
            //todo err
            return null;
        }

        if(claim == null)return null;

        return resolveDcId(claim.claimedDcId());
    }
    public List<Identity.DcIdentity> incomingClaims(Identity.McIdentity pOn){
        List<Database.DcRow> claims;
        try{
            claims = db.findPendingDiscordClaimsForMc(pOn.uuid());
        } catch (Exception e) {
            //todo err
            return List.of();
        }

        if(claims.isEmpty())return List.of();

        List<Identity.DcIdentity> ret = new java.util.ArrayList<>(claims.size());
        for(Database.DcRow row : claims){
            ret.add(new Identity.DcIdentity(row.dcId(),row.dcUsername(),row.avatarUrl()));
        }

        return ret;
    }

    //todo get nick (effective name) from (User, Guild), (User, Channel)


    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F-]{32,36}$");
    public UUID findMcPlayerUUID(String uuidOrName) {
        try {
            if (UUID_RE.matcher(uuidOrName).matches()) {
                UUID u = UUID.fromString(uuidOrName.replaceAll("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5"));
                return u;
            }
        } catch (Exception ignored) {}

        // Fall back to resolving by player name (uses the server’s known players)
        // If the name is online, prefer exact:
        Player pl = Bukkit.getPlayerExact(uuidOrName);
        if (pl != null) return pl.getUniqueId();

        // Offline cache:
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuidOrName);
        if (off != null && (off.hasPlayedBefore() || off.getUniqueId() != null) ) {
            return off.getUniqueId();
        }
        return null;
    }

    /** Handy to update MC meta when we only know UUID (pulls last known name if online) */
    public void refreshMcMeta(UUID mcUuid) {
        // Try to enrich name & skin if you have a skin provider; this keeps it simple
        OfflinePlayer off = Bukkit.getOfflinePlayer(mcUuid);
        var name = off.getName() != null ? off.getName() : "unknown";
        String skin = null; // plug your skin-face provider URL here if you have one todo
        try { db.upsertMcMeta(mcUuid.toString(), name, skin); } catch (Exception ignored) {}
    }
}
