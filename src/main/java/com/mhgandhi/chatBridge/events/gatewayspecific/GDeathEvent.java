package com.mhgandhi.chatBridge.events.gatewayspecific;


import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.gateway.ChatGateway;

public class GDeathEvent extends GatewayEvent {
    private final Identity died;
    private final String deathMessage;

    public GDeathEvent(Identity died, String pDeathMessage, ChatGateway source) {
        super(source);

        this.died = died;
        this.deathMessage = pDeathMessage;
    }

    public Identity getDied(){
        return this.died;
    }

    public String getDeathMessage(){
        return this.deathMessage;
    }
}
