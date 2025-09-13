package com.mhgandhi.chatBridge.gateway.events.gatewayspecific;

import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.gateway.ChatGateway;

public class GJoinEvent extends GatewayEvent {
    private final Identity joined;

    public GJoinEvent(Identity pJoined, ChatGateway source, boolean async) {
        super(source, async);

        this.joined = pJoined;
    }

    public Identity getJoined(){return this.joined;}
}
