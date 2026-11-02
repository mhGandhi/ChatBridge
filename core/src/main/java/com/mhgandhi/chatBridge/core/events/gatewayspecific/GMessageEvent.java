package com.mhgandhi.chatBridge.core.events.gatewayspecific;

import com.mhgandhi.chatBridge.core.Identity;
import com.mhgandhi.chatBridge.core.gateway.ChatGateway;
import com.mhgandhi.chatBridge.core.gateway.GatewayMessage;

public class GMessageEvent extends GatewayEvent {
    private final Identity sender;
    private final GatewayMessage message;

    //todo time?

    public GMessageEvent(Identity pSender, GatewayMessage pMsg, ChatGateway source) {
        super(source);

        this.sender = pSender;
        this.message = pMsg;
    }

    public Identity getSender(){return sender;}
    public GatewayMessage getMessage(){return message;}
}
