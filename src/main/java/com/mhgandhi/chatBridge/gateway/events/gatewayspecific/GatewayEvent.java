package com.mhgandhi.chatBridge.gateway.events.gatewayspecific;

import com.mhgandhi.chatBridge.gateway.ChatGateway;
import com.mhgandhi.chatBridge.gateway.events.PluginEvent;

public class GatewayEvent extends PluginEvent {

    private final ChatGateway source;

    protected GatewayEvent(ChatGateway source, boolean async) {
        super(async);
        this.source = source;
    }

    public ChatGateway getSource() { return source; }
}
