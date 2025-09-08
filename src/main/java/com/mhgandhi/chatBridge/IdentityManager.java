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

    public void clearDc(Identity.Dc dci){
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
            if(!sUUID.isPresent())return null;
            return Identity.Mc.fromString(sUUID.get());
        } catch (Exception e) {
            throw new RuntimeException(e);//todo do sth if invalid
        }
    }

    public String getClaimMc(Identity.Mc mci) {
        try {
            return db.getClaimedDiscordForMc(mci.toString()).orElse(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    public Identity resolve(OfflinePlayer player) {
        Identity.Mc mci = new Identity.Mc(player.getUniqueId());
        if(isLinkedMc(mci)){
            String dcId = getClaimMc(mci);
            return new Identity.Dc(dcId);//todo
        }else{
            return new Identity.Mc(player.getUniqueId());
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

    public List<String> claimsOnDc(Identity.Dc dci){
        try {
            return db.getClaimsOnDc(dci.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> claimsOnMc(Identity.Mc mci) {
        try {
            return db.getClaimsOnMc(mci.toString());
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

    public void upsertMcName(Identity.Mc mci, String name){//called on join, on startup?, on connect?, on Resolve?
        mcNames.put(mci.uuid, name);
    }

    public String getMcName(Identity.Mc mci){
        if(mcNames.containsKey(mci.uuid)){
            return mcNames.get(mci.uuid);
        }else{
            //todo query with api idk (what about async?)
            return null;
        }
    }

    private final Map<String,String> dcNames = new HashMap<>();

    public void upsertDcName(Identity.Dc dci, String pGuild, String pName){//called on command, on startup?, on Resolve?
        String key = dcNameKey(dci,pGuild);
        dcNames.put(key,pName);
    }

    public String getDcName(Identity.Dc dci){
        return getDcName(dci,null);
    }
    public String getDcName(Identity.Dc dci, String pGuild){
        String key = dcNameKey(dci,pGuild);

        if(dcNames.containsKey(key)){
            return dcNames.get(key);
        }else{
            //todo query with api idk (what about async?)
            return pGuild==null?null:getDcName(dci);
        }
    }
    private String dcNameKey(Identity.Dc mci, String guild){
        return mci.id+(guild==null?"":guild);
    }


    //todo
    public CompletableFuture<Identity.Dc> resolveDcName(String name){
        throw new NotImplementedException();
    }

    public CompletableFuture<Identity.Mc> resolveMcName(String name) {
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
                    return Identity.Mc.fromString(dashed);
                });
    }
}