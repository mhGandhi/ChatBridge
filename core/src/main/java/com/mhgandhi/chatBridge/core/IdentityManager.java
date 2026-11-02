package com.mhgandhi.chatBridge.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mhgandhi.chatBridge.core.domain.Game;
import com.mhgandhi.chatBridge.core.domain.Player;
import com.mhgandhi.chatBridge.core.storage.Database;
import net.dv8tion.jda.api.entities.Member;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public class IdentityManager {
    private final Database db;
    private final Logger logger;
    private final Game game;
    //private JDA jda;
    private final boolean kickOnDisconnect;

    public IdentityManager(Database db, Logger lgg, Game pGame, boolean kickOnDc) {
        this.db = db;
        this.logger = lgg;
        this.game = pGame;
        this.kickOnDisconnect = kickOnDc;
    }

    public void clearDc(Identity.Dc dci){

        if(kickOnDisconnect){
            Identity.Mc mci = getClaimDc(dci);
            if(mci!=null){
                game.kick(mci.uuid);
            }
        }

        try {
            db.clearDcClaim(dci.id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void clearMc(Identity.Mc mci) {
        try {
            db.clearMcClaim(mci.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void claimDcMc(Identity.Dc dci, Identity.Mc mci){
        try {
            db.linkDcToMc(dci.id, mci.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void claimMcDc(Identity.Mc mci, Identity.Dc dci){
        try {
            db.linkMcToDc(mci.toString(),dci.id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Identity.Mc getClaimDc(Identity.Dc dci) {
        try {
            var sUUID = db.getClaimedMinecraftForDc(dci.id);
            return sUUID.map(Identity.Mc::fromString).orElse(null);
        } catch (Exception e) {
            logger.severe("Error resolving Mc UUID: "+e.getMessage());
            return null;
        }
    }

    public Identity.Dc getClaimMc(Identity.Mc mci) {
        try {
            String dc = db.getClaimedDiscordForMc(mci.toString()).orElse(null);
            if(dc==null)return null;
            return new Identity.Dc(dc);
        } catch (Exception e) {
            logger.severe("Error resolving Dc Id: "+e.getMessage());
            return null;
        }
    }


    public Identity resolve(Member m){
        Identity.Dc dci = new Identity.Dc(m.getUser().getId());
        if(isLinkedDc(dci)){
            return getClaimDc(dci);
        }else{
            return new Identity.Dc(m.getUser().getId());
        }
    }

    public Identity resolve(UUID uuid) {
        Identity.Mc mci = new Identity.Mc(uuid);
        if(isLinkedMc(mci)){
            return getClaimMc(mci);
        }else{
            return new Identity.Mc(uuid);
        }
    }


    public boolean isLinkedDc(Identity.Dc dci){
        try {
            return db.isActiveByDc(dci.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isLinkedMc(Identity.Mc mci){
        try {
            return db.isActiveByMc(mci.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    public List<String> claimsOnDc(Identity.Dc dci){
//        try {
//            return db.getClaimsOnDc(dci.toString());
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    public List<String> claimsOnMc(Identity.Mc mci) {
        try {
            return db.getClaimsOnMc(mci.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    //public void setJda(JDA d){
    //    jda = d;
    //}


    /// ////////////////////////////////////////////////////////////////////////////////////CACHE

    private final Map<UUID,String> mcNames = new HashMap<>();

    public void upsertMcName(Identity.Mc mci, String name){//called on join, on startup?, on connect?, on Resolve?
        mcNames.put(mci.uuid, name);
    }

    public void upsertMc(Player p) {
        if(!p.hasPlayedBefore())return;
        logger.info("Upsert player "+p.getName());
        upsertMcName(Identity.get(p),p.getName());
    }

    public String getMcName(Identity.Mc mci){
        String cached = mcNames.get(mci.uuid);
        if (cached != null && !cached.isBlank()) return cached;

        Player online = game.getPlayer(mci.uuid);
        if (online != null) {
            String name = online.getName();
            if (name != null && !name.isBlank()) {
                mcNames.put(mci.uuid, name);
                return name;
            }
        }

        Player off = game.getOfflinePlayer(mci.uuid);
        String name = off.getName(); // can be null
        if (name != null && !name.isBlank()) {
            mcNames.put(mci.uuid, name);
            return name;
        }

        logger.fine("cant resolve name of mc "+mci);//todo query with api async for next call
        return "["+mci+"]";
    }

    private final Map<String,String> dcNames = new HashMap<>();
    private final Map<String,String> dcAvatars = new HashMap<>();

    public void upsertDcName(Identity.Dc dci, String pName){//called on command, on startup?, on Resolve?
        dcNames.put(dci.id,pName);
    }

    public void upsertDcAvatarUrl(Identity.Dc dci, String pAvatarUrl){
        dcAvatars.put(dci.id, pAvatarUrl);
    }

    public void upsertDc(Member m){
        logger.info("Upsert member "+m.getUser().getName());
        Identity.Dc dci = Identity.get(m);
        upsertDcName(dci, m.getEffectiveName());
        upsertDcAvatarUrl(dci, m.getEffectiveAvatarUrl());
    }

    public String getDcName(Identity.Dc dci){
        if(dcNames.containsKey(dci.id)){
            return dcNames.get(dci.id);
        }else{
            logger.fine("cant resolve name of dc "+dci);//todo query with api async for next call
            return "["+dci+"]";
        }
    }

    public String getDcAvatar(Identity.Dc dci){

        if(dcAvatars.containsKey(dci.id)){
            return dcAvatars.get(dci.id);
        }else{
            logger.fine("cant resolve avatar of dc "+dci);//todo query with api async for next call
            return null;
        }
    }

    //todo
//    public CompletableFuture<Identity.Dc> resolveDcName(String name){
//        throw new NotImplementedException();
//    }

    public CompletableFuture<Identity.Mc> resolveMcName(String name) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            String url = "https://api.mojang.com/users/profiles/minecraft/" + name;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 204 || response.statusCode() == 404) {
                            throw new CompletionException(
                                    new IllegalArgumentException("No player with name: " + name));
                        }
                        if (response.statusCode() != 200) {
                            throw new CompletionException(
                                    new RuntimeException("Mojang API error: " + response.statusCode()));
                        }

                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        String id = json.get("id").getAsString(); // UUID without dashes, 32 hex chars
                        // Insert dashes in UUID string
                        String dashed = id.replaceFirst(
                                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                                "$1-$2-$3-$4-$5"
                        );

                        Identity.Mc mci = Identity.Mc.fromString(dashed);

                        upsertMcName(mci, name);

                        return mci;
                    });
        }
    }
}