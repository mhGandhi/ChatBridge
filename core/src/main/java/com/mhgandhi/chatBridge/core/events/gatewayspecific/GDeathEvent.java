package com.mhgandhi.chatBridge.core.events.gatewayspecific;


import com.mhgandhi.chatBridge.core.Identity;
import com.mhgandhi.chatBridge.core.gateway.ChatGateway;

public class GDeathEvent extends GatewayEvent {
    private final Identity killer;
    private final Identity died;
    private final String deathMessage;

    public GDeathEvent(Identity died, Identity killer, String pDeathMessage, ChatGateway source) {
        super(source);

        this.killer = killer;
        this.died = died;
        this.deathMessage = pDeathMessage;
    }

    public GDeathEvent(Identity died, String pDeathMessage, ChatGateway source) {
        this(died, null, pDeathMessage, source);
    }

    public Identity getKiller() {
        return killer;
    }

    public Identity getDied(){
        return this.died;
    }

    public String getDeathMessage(){
        return this.deathMessage;
    }
}
