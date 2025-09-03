package com.mhgandhi.dcBridge.chat;

import com.mhgandhi.dcBridge.Identity;

import java.util.function.BiConsumer;

public interface IChat {
    void sendMessage(Identity author, String content);
    void setMessageCallback(BiConsumer<Identity,String> handler);
    boolean isReady();

    //todo getAvatarURL?
}
