package com.mhgandhi.chatBridge.core.gateway;

import com.mhgandhi.chatBridge.platform.paper.PluginEntryPoint;
import com.mhgandhi.chatBridge.core.IdentityManager;
import com.mhgandhi.chatBridge.core.events.*;
import com.mhgandhi.chatBridge.core.events.gatewayspecific.*;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class ChatGateway {
    private final PluginEventDispatcher dispatcher;

    protected final JavaPlugin plugin;
    protected final IdentityManager identityManager;
    protected ChatGateway(JavaPlugin pPlugin, IdentityManager pIdentityManager){
        plugin = pPlugin;
        identityManager = pIdentityManager;

        dispatcher = new PluginEventDispatcher();
        dispatcher.on(GJoinEvent.class, this::onJoin);
        dispatcher.on(GLeaveEvent.class, this::onLeave);
        dispatcher.on(GMessageEvent.class, this::onMessage);
        dispatcher.on(LinkCreatedEvent.class, this::onLinkCreated);
        dispatcher.on(LinkDestroyedEvent.class, this::onLinkDestroyed);
        dispatcher.on(ServerDisableEvent.class, this::onPluginDisable);
        dispatcher.on(ServerEnableEvent.class, this::onPluginEnable);
        dispatcher.on(GDeathEvent.class, this::onDied);

        dispatcher.onDefault(e -> plugin.getLogger()
                .severe("o no what to do with this plugin event " + e));
    }

    public final void handlePluginEvent(ServerEvent pServerEvent){
        if(pServerEvent.isCancelled())return;
        if(pServerEvent instanceof GatewayEvent ge && ge.getSource()==this)return;

        onPluginEvent(pServerEvent);//todo or directly disperse into different events
    }

    //always sync
    protected final void callEvent(ServerEvent pe){
        PluginEntryPoint.callEvent(pe, plugin);
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
