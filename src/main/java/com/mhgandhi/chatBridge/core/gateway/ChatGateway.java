package com.mhgandhi.chatBridge.core.gateway;

import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.events.*;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.*;

import java.util.logging.Logger;

public abstract class ChatGateway {
    private final EventDispatcher dispatcher;
    protected final Logger logger;
    private final EventRouter router;

    protected final IdentityManager identityManager;
    protected ChatGateway(Logger pLogger, EventRouter pRouter, IdentityManager pIdentityManager){
        logger = pLogger;
        router = pRouter;
        identityManager = pIdentityManager;

        dispatcher = new EventDispatcher();
        dispatcher.on(GJoinEvent.class, this::onJoin);
        dispatcher.on(GLeaveEvent.class, this::onLeave);
        dispatcher.on(GMessageEvent.class, this::onMessage);
        dispatcher.on(LinkCreatedEvent.class, this::onLinkCreated);
        dispatcher.on(LinkDestroyedEvent.class, this::onLinkDestroyed);
        dispatcher.on(ServerDisableEvent.class, this::onPluginDisable);
        dispatcher.on(ServerEnableEvent.class, this::onPluginEnable);
        dispatcher.on(GDeathEvent.class, this::onDied);

        dispatcher.onDefault(e -> logger
                .severe("o no what to do with this plugin event " + e));
    }

    public final void handlePluginEvent(ServerEvent pServerEvent){
        if(pServerEvent.isCancelled())return;
        if(pServerEvent instanceof GatewayEvent ge && ge.getSource()==this)return;

        onPluginEvent(pServerEvent);//todo or directly disperse into different events
    }

    //always sync
    protected final void callEvent(ServerEvent pe){
        router.publish(pe);
    }

    private void onPluginEvent(ServerEvent ServerEvent) {
        dispatcher.dispatch(ServerEvent);
    }

    protected abstract void onJoin(GJoinEvent e);
    protected abstract void onLeave(GLeaveEvent e);
    protected abstract void onMessage(GMessageEvent e);
    protected abstract void onLinkCreated(LinkCreatedEvent e);
    protected abstract void onLinkDestroyed(LinkDestroyedEvent e);
    protected abstract void onPluginDisable(ServerDisableEvent e);
    protected abstract void onPluginEnable(ServerEnableEvent e);
    protected abstract void onDied(GDeathEvent gde);
}
