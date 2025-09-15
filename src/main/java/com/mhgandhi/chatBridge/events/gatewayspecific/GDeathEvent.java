package com.mhgandhi.chatBridge.events.gatewayspecific;


import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.gateway.ChatGateway;

public class GDeathEvent extends GatewayEvent {
    private final Identity died;

    public GDeathEvent(Identity died, ChatGateway source) {
        super(source);

        this.died = died;
    }

    public Identity getDied(){
        return this.died;
    }
}
