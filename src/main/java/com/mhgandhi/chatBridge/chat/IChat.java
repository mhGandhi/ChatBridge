package com.mhgandhi.chatBridge.chat;

import com.mhgandhi.chatBridge.Identity;

import java.util.function.BiConsumer;

public interface IChat {
    void sendMessage(Identity author, String content);
    void setMessageCallback(BiConsumer<Identity,String> handler);
    boolean isReady();

    //todo getAvatarURL?
}
