package com.mhgandhi.chatBridge.events.gatewayspecific;

import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.gateway.ChatGateway;

public class GJoinEvent extends GatewayEvent {
    private final Identity joined;

    public GJoinEvent(Identity pJoined, ChatGateway source) {
        super(source);

        this.joined = pJoined;
    }

    public Identity getJoined(){return this.joined;}
}
