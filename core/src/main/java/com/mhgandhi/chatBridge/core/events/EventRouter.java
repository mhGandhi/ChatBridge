package com.mhgandhi.chatBridge.core.events;

import com.mhgandhi.chatBridge.core.gateway.ChatGateway;

import java.util.ArrayList;
import java.util.List;

public final class EventRouter {
    private final List<ChatGateway> gateways = new ArrayList<>();

    public void register(ChatGateway gateway) {
        gateways.add(gateway);
    }

    public void publish(ServerEvent event) {
        for (ChatGateway gateway : gateways) {
            gateway.handlePluginEvent(event);
        }
    }
}
