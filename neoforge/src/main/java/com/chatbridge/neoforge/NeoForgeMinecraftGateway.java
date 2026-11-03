package com.chatbridge.neoforge;

import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.events.*;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.GMessageEvent;
import com.mhgandhi.chatBridge.core.gateway.MinecraftGateway;
import net.minecraft.server.MinecraftServer;

import java.util.logging.Logger;

public class NeoForgeMinecraftGateway extends MinecraftGateway {
    private final MinecraftServer server;

    protected NeoForgeMinecraftGateway(Logger pLogger, EventRouter pRouter, IdentityManager pIdentityManager, boolean pSendConnectionReminder, boolean pWhitelist, MinecraftServer server) {
        super(pLogger, pRouter, pIdentityManager, pSendConnectionReminder, pWhitelist);
        this.server = server;
    }

    @Override
    protected void onMessage(GMessageEvent e) {
        //todo
    }

    @Override
    protected void onLinkCreated(LinkCreatedEvent e) {
        //todo
    }

    @Override
    protected void onLinkDestroyed(LinkDestroyedEvent e) {
        //todo
    }

    @Override
    protected void onPluginDisable(ServerDisableEvent e) {
        //todo
    }

    @Override
    protected void onPluginEnable(ServerEnableEvent e) {
        //todo
    }


    //todo receive
}
