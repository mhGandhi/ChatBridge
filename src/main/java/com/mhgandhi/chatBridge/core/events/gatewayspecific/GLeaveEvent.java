package com.mhgandhi.chatBridge.core.events.gatewayspecific;

import com.mhgandhi.chatBridge.core.Identity;
import com.mhgandhi.chatBridge.core.gateway.ChatGateway;

public class GLeaveEvent extends GatewayEvent {
    private final Identity left;

    public GLeaveEvent(Identity pLeft, ChatGateway source) {
        super(source);

        this.left = pLeft;
    }

    public Identity getLeft(){return this.left;}
}
