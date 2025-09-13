package com.mhgandhi.chatBridge.gateway;

public class GatewayMessage {
    private final String content;//todo parse content

    public GatewayMessage(String pContent){
        this.content = pContent;
    }

    public String getContent(){return this.content;}
}
