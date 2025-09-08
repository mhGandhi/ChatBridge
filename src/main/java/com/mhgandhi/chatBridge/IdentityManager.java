package com.mhgandhi.chatBridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mhgandhi.chatBridge.storage.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.OfflinePlayer;

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
    private final Logger log;
    private JDA jda;

    //todo add cache for names, maybe wrap Strings instead of throwing them around without a condom

    public IdentityManager(Database db, Logger log) {
        this.db = db;
        this.log = log;
    }

    public void clearDc(String dc){
        try {
            db.clearDcClaim(dc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void clearMc(UUID uuid) {
        try {
            db.clearMcClaim(uuid.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void claimDcMc(String dc, UUID mc){
        try {
            db.linkDcToMc(dc,mc.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void claimMcDc(UUID mc, String dc){
        try {
            db.linkMcToDc(mc.toString(),dc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public UUID getClaimDc(String dId) {
        try {
            var sUUID = db.getClaimedMinecraftForDc(dId);
            if(!sUUID.isPresent())return null;
            return UUID.fromString(sUUID.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getClaimMc(UUID pUUID) {
        try {
            return db.getClaimedDiscordForMc(pUUID.toString()).orElse(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public Identity resolve(Member m){
        String id = m.getUser().getId();
        if(isLinkedDc(id)){
            UUID uuid = getClaimDc(id);
            return new Identity(getMcName(uuid),/*ChatBridge.getFormatter().getMcAvatar(uuid)*/null, Identity.Type.Minecraft);
        }else{
            return new Identity(m.getEffectiveName(),/*m.getEffectiveAvatarUrl()*/null, Identity.Type.Discord);
        }
    }

    public Identity resolve(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        if(isLinkedMc(uuid)){
            String dcId = getClaimMc(uuid);
            return new Identity(dcId,null, Identity.Type.Discord);//todo
        }else{
            return new Identity(player.getName(),ChatBridge.getFormatter().getMcAvatar(uuid), Identity.Type.Minecraft);
        }
    }


    public boolean isLinkedDc(String dcId){
        try {
            return db.isActiveByDc(dcId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isLinkedMc(UUID mcUUID){
        try {
            return db.isActiveByMc(mcUUID.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> claimsOnDc(String pId){
        try {
            return db.getClaimsOnDc(pId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> claimsOnMc(UUID uuid) {
        try {
            return db.getClaimsOnMc(uuid.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    //todo begone
    public void setJda(JDA d){
        jda = d;
    }


    /// ////////////////////////////////////////////////////////////////////////////////////CACHE

    private final Map<UUID,String> mcNames = new HashMap<>();

    public void upsertMcName(UUID uuid, String name){//called on join, on startup?, on connect?, on Resolve?
        mcNames.put(uuid, name);
    }

    public String getMcName(UUID pUUID){
        if(mcNames.containsKey(pUUID)){
            return mcNames.get(pUUID);
        }else{
            //todo query with api idk (what about async?)
            return null;
        }
    }

    private final Map<String,String> dcNames = new HashMap<>();//todo maybe dont concat keys?

    private String dcNameKey(String id, String guild){
        return id+(guild==null?"":guild);
    }
    public void upsertDcName(String pId, String pGuild, String pName){//called on command, on startup?, on Resolve?
        String key = dcNameKey(pId,pGuild);
        dcNames.put(key,pName);
    }

    public String getDcName(String pId){
        return getDcName(pId,null);
    }
    public String getDcName(String pId, String pGuild){
        String key = dcNameKey(pId,pGuild);

        if(dcNames.containsKey(key)){
            return dcNames.get(key);
        }else{
            //todo query with api idk (what about async?)
            return pGuild==null?null:getDcName(pId);
        }
    }


    //todo
    public CompletableFuture<String> resolveDcName(String name){
        throw new NotImplementedException();
    }

    public CompletableFuture<UUID> resolveMcName(String name) {
        HttpClient client = HttpClient.newHttpClient();
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
                    return UUID.fromString(dashed);
                });
    }
}