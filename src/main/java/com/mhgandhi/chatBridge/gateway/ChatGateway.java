package com.mhgandhi.chatBridge.gateway;

import com.mhgandhi.chatBridge.IdentityManager;
import com.mhgandhi.chatBridge.events.*;
import com.mhgandhi.chatBridge.events.gatewayspecific.GJoinEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GLeaveEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GMessageEvent;
import com.mhgandhi.chatBridge.events.gatewayspecific.GatewayEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class ChatGateway {
    protected final JavaPlugin plugin;
    protected final IdentityManager identityManager;
    protected ChatGateway(JavaPlugin pPlugin, IdentityManager pIdentityManager){
        plugin = pPlugin;
        identityManager = pIdentityManager;
    }

    public final void handlePluginEvent(PluginEvent pPluginEvent){
        if(pPluginEvent.isCancelled())return;
        if(pPluginEvent instanceof GatewayEvent ge && ge.getSource()==this)return;

        onPluginEvent(pPluginEvent);//todo or directly disperse into different events
    }

    //always sync
    protected final void callEvent(PluginEvent pe){
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(pe);
        } else {
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.getPluginManager().callEvent(pe)
            );
        }
    }

    private void onPluginEvent(PluginEvent pluginEvent) {
        if (pluginEvent instanceof GJoinEvent gje) {
            onJoin(gje);
            return;
        }
        if (pluginEvent instanceof GLeaveEvent gle) {
            onLeave(gle);
            return;
        }
        if (pluginEvent instanceof GMessageEvent gme) {
            onMessage(gme);
            return;
        }
        if (pluginEvent instanceof LinkCreatedEvent lce) {
            onLinkCreated(lce);
            return;
        }
        if (pluginEvent instanceof LinkDestroyedEvent lde) {
            onLinkDestroyed(lde);
            return;
        }
        if (pluginEvent instanceof PluginDisableEvent pde) {
            onPluginDisable(pde);
            return;
        }
        if (pluginEvent instanceof PluginEnableEvent pee) {
            onPluginEnable(pee);
            return;
        }
        plugin.getLogger().severe("o no what to do with this plugin event " + pluginEvent.toString());
    }

    protected abstract void onJoin(GJoinEvent e);
    protected abstract void onLeave(GLeaveEvent e);
    protected abstract void onMessage(GMessageEvent e);
    protected abstract void onLinkCreated(LinkCreatedEvent e);
    protected abstract void onLinkDestroyed(LinkDestroyedEvent e);
    protected abstract void onPluginDisable(PluginDisableEvent e);
    protected abstract void onPluginEnable(PluginEnableEvent e);
}
