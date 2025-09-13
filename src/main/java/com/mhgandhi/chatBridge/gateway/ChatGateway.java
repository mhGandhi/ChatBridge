package com.mhgandhi.chatBridge.gateway;

import com.mhgandhi.chatBridge.gateway.events.gatewayspecific.GatewayEvent;
import com.mhgandhi.chatBridge.gateway.events.PluginEvent;

public abstract class ChatGateway {

    public final void handlePluginEvent(PluginEvent pPluginEvent){
        if(pPluginEvent.isCancelled())return;
        if(pPluginEvent instanceof GatewayEvent ge && ge.isCancelled())return;

        onPluginEvent(pPluginEvent);//todo or directly disperse into different events
    }

    protected abstract void onPluginEvent(PluginEvent gatewayEvent);
}
