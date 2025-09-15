package com.mhgandhi.chatBridge.events.gatewayspecific;

import com.mhgandhi.chatBridge.Identity;
import com.mhgandhi.chatBridge.gateway.ChatGateway;

public class GLeaveEvent extends GatewayEvent {
    private final Identity left;

    public GLeaveEvent(Identity pLeft, ChatGateway source) {
        super(source);

        this.left = pLeft;
    }

    public Identity getLeft(){return this.left;}
}
