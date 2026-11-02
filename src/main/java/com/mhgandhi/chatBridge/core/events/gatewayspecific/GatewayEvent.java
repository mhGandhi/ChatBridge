package com.mhgandhi.chatBridge.core.events.gatewayspecific;

import com.mhgandhi.chatBridge.core.gateway.ChatGateway;
import com.mhgandhi.chatBridge.core.events.ServerEvent;

public class GatewayEvent extends ServerEvent {

    private final ChatGateway source;

    protected GatewayEvent(ChatGateway source) {
        this.source = source;
    }

    public ChatGateway getSource() { return source; }
}
