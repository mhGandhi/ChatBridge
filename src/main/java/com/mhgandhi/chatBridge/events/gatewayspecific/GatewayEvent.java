package com.mhgandhi.chatBridge.events.gatewayspecific;

import com.mhgandhi.chatBridge.gateway.ChatGateway;
import com.mhgandhi.chatBridge.events.PluginEvent;

public class GatewayEvent extends PluginEvent {

    private final ChatGateway source;

    protected GatewayEvent(ChatGateway source) {
        this.source = source;
    }

    public ChatGateway getSource() { return source; }
}
