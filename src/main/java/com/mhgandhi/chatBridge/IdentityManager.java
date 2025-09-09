package com.mhgandhi.chatBridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mhgandhi.chatBridge.storage.Database;
import net.dv8tion.jda.api.entities.Member;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

public class IdentityManager {
    private final Database db;
    private final JavaPlugin plugin;
    //private JDA jda;
    private final boolean kickOnDisconnect;

    public IdentityManager(Database db, JavaPlugin plg, boolean kickOnDc) {
        this.db = db;
        this.plugin = plg;
        this.kickOnDisconnect = kickOnDc;
    }

    public void clearDc(Identity.Dc dci){

        if(kickOnDisconnect){
            Identity.Mc mci = getClaimDc(dci);
            if(mci!=null){
                Player p = Bukkit.getServer().getPlayer(mci.uuid);
                if(p!=null){
                    Bukkit.getScheduler().runTask(plugin, () ->
                            p.kick( ChatBridge.getFormatter().disconnectKick() )
                    );
                }
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
            plugin.getLogger().severe("Error resolving Mc UUID: "+e.getMessage());
            return null;
        }
    }

    public Identity.Dc getClaimMc(Identity.Mc mci) {
        try {
            String dc = db.getClaimedDiscordForMc(mci.toString()).orElse(null);
            if(dc==null)return null;
            return new Identity.Dc(dc);
        } catch (Exception e) {
            plugin.getLogger().severe("Error resolving Dc Id: "+e.getMessage());
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

    public Identity resolve(OfflinePlayer player) {
        Identity.Mc mci = new Identity.Mc(player.getUniqueId());
        if(isLinkedMc(mci)){
            return getClaimMc(mci);
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

    public void upsertMc(OfflinePlayer p) {
        if(!p.hasPlayedBefore())return;
        plugin.getLogger().info("Upsert player "+p.getName());
        upsertMcName(Identity.get(p),p.getName());
    }

    public String getMcName(Identity.Mc mci){
        if(mcNames.containsKey(mci.uuid)){
            return mcNames.get(mci.uuid);
        }else{
            plugin.getLogger().fine("cant resolve name of mc "+mci);//todo query with api async for next call
            return "["+mci+"]";
        }
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
        plugin.getLogger().info("Upsert member "+m.getUser().getName());
        Identity.Dc dci = Identity.get(m);
        upsertDcName(dci, m.getEffectiveName());
        upsertDcAvatarUrl(dci, m.getEffectiveAvatarUrl());
    }

    public String getDcName(Identity.Dc dci){
        if(dcNames.containsKey(dci.id)){
            return dcNames.get(dci.id);
        }else{
            plugin.getLogger().fine("cant resolve name of dc "+dci);//todo query with api async for next call
            return "["+dci+"]";
        }
    }

    public String getDcAvatar(Identity.Dc dci){

        if(dcAvatars.containsKey(dci.id)){
            return dcAvatars.get(dci.id);
        }else{
            plugin.getLogger().fine("cant resolve avatar of dc "+dci);//todo query with api async for next call
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