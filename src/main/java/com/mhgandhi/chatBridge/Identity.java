package com.mhgandhi.chatBridge;

import java.util.UUID;

public class Identity {
    public static final Identity server = new Identity();

    public static class Mc extends Identity {
        public final UUID uuid;
        public Mc(UUID pUUID){
            uuid = pUUID;
        }

        public static Mc fromString(String pS)throws IllegalArgumentException{
            return new Identity.Mc(UUID.fromString(pS));
        }

        @Override
        public String toString() {
            return uuid.toString();
        }
    }

    public static class Dc extends Identity{
        public final String id;
        public Dc(String pId){
            id = pId;
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
