package com.chatbridge.neoforge;

import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.events.*;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GMessageEvent;
import com.mhgandhi.chatBridge.core.gateway.MinecraftGateway;

import java.util.logging.Logger;

public class NeoForgeMinecraftGateway extends MinecraftGateway {
    protected NeoForgeMinecraftGateway(Logger pLogger, EventRouter pRouter, IdentityManager pIdentityManager, boolean pSendConnectionReminder, boolean pWhitelist) {
        super(pLogger, pRouter, pIdentityManager, pSendConnectionReminder, pWhitelist);
    }

    @Override
    protected void onMessage(GMessageEvent e) {

    }

    @Override
    protected void onLinkCreated(LinkCreatedEvent e) {

    }

    @Override
    protected void onLinkDestroyed(LinkDestroyedEvent e) {

    }

    @Override
    protected void onPluginDisable(ServerDisableEvent e) {

    }

    @Override
    protected void onPluginEnable(ServerEnableEvent e) {

    }


    //todo receive
}
