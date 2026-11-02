package com.mhgandhi.chatBridge.core.gateway;

import com.mhgandhi.chatBridge.core.Identity;
import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.domain.Player;
import com.mhgandhi.chatBridge.core.events.EventRouter;
import com.mhgandhi.chatBridge.core.events.ServerEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GDeathEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GMessageEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.UUID;
import java.util.logging.Logger;

public abstract class MinecraftGateway extends ChatGateway {
    private static MinecraftGateway instance = null;

    protected final PlainTextComponentSerializer serializer;

    protected final boolean sendConnectionReminder;
    protected final boolean whitelist;


    protected MinecraftGateway(Logger pLogger, EventRouter pRouter, IdentityManager pIdentityManager, boolean pSendConnectionReminder, boolean pWhitelist) {
        super(pLogger, pRouter, pIdentityManager);
        if(instance!=null){//todo mby throw sth?
            logger.severe("There may only be one instance of MinecraftGateway; overwriting existing");
        }
        instance = this;

        serializer = PlainTextComponentSerializer.plainText();

        sendConnectionReminder = pSendConnectionReminder;
        whitelist = pWhitelist;
        if(whitelist) logger.fine("REJECTING UNCLAIMED PLAYERS");
    }


    @Override
    protected final void onJoin(GJoinEvent e) {
        //msg already sent by mc
    }

    @Override
    protected final void onLeave(GLeaveEvent e) {
        //msg already sent by mc
    }

    @Override
    protected final void onDied(GDeathEvent gde) {
        //msg already sent by mc
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////RECEIVE

    protected final void chatMessage(Player p, String body){
        ServerEvent ev = new GMessageEvent(identityManager.resolve(p.getUniqueId()),new GatewayMessage(body),this);
        callEvent(ev);
    }

    protected final void playerJoined(Player p, Runnable sendReminder){
        identityManager.upsertMc(p);

        if(sendConnectionReminder && !identityManager.isLinkedMc(Identity.get(p))){
            sendReminder.run();
        }

        Identity player = identityManager.resolve(p.getUniqueId());
        ServerEvent ev = new GJoinEvent(player, this);

        callEvent(ev);
    }

    protected final void playerLeft(Player p){
        Identity player = identityManager.resolve(p.getUniqueId());
        ServerEvent ev = new GLeaveEvent(player, this);

        callEvent(ev);
    }

    protected final void playerDied(Player p, Player k, Component deathMessage){
        Identity player = identityManager.resolve(p.getUniqueId());
        Identity killer = k==null? null : identityManager.resolve(k.getUniqueId());

        String dm;
        if(deathMessage==null){
            dm = null;
        }else{
            dm = serializer.serialize(deathMessage);
        }

        callEvent(new GDeathEvent(player, killer, dm, this));
    }

    protected final boolean allowPlayer(UUID p){
        if(!whitelist)return true;

        Identity.Mc mci = new Identity.Mc(p);
        return !identityManager.claimsOnMc(mci).isEmpty();
    }
}
