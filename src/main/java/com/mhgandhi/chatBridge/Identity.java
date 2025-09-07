package com.mhgandhi.chatBridge;

public record Identity(String name, String avatarUrl, Type type) {

    public enum Type{
        Minecraft,
        Discord,
        Server
    }

    public static final Identity server = new Identity("",null, Type.Server);
}
