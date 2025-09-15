package com.mhgandhi.chatBridge.gateway;

import com.mhgandhi.chatBridge.ChatBridge;
import com.mhgandhi.chatBridge.IdentityManager;
import com.mhgandhi.chatBridge.events.*;
import com.mhgandhi.chatBridge.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GMessageEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GatewayEvent;
import org.bukkit.Bukkit;
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
        dispatcher.on(PluginDisableEvent.class, this::onPluginDisable);
        dispatcher.on(PluginEnableEvent.class, this::onPluginEnable);

        dispatcher.onDefault(e -> plugin.getLogger()
                .severe("o no what to do with this plugin event " + e));
    }

    public final void handlePluginEvent(PluginEvent pPluginEvent){
        if(pPluginEvent.isCancelled())return;
        if(pPluginEvent instanceof GatewayEvent ge && ge.getSource()==this)return;

        onPluginEvent(pPluginEvent);//todo or directly disperse into different events
    }

    //always sync
    protected final void callEvent(PluginEvent pe){
        ChatBridge.callEvent(pe, plugin);
    }

    private void onPluginEvent(PluginEvent pluginEvent) {
        dispatcher.dispatch(pluginEvent);
    }

    protected abstract void onJoin(GJoinEvent e);
    protected abstract void onLeave(GLeaveEvent e);
    protected abstract void onMessage(GMessageEvent e);
    protected abstract void onLinkCreated(LinkCreatedEvent e);
    protected abstract void onLinkDestroyed(LinkDestroyedEvent e);
    protected abstract void onPluginDisable(PluginDisableEvent e);
    protected abstract void onPluginEnable(PluginEnableEvent e);
}
