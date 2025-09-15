package com.mhgandhi.chatBridge.gateway;

import com.mhgandhi.chatBridge.IdentityManager;
import com.mhgandhi.chatBridge.events.gatewayspecific.GatewayEvent;
import com.mhgandhi.chatBridge.events.PluginEvent;
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

    protected abstract void onPluginEvent(PluginEvent pluginEvent);
}
