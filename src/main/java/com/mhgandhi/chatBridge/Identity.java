package com.mhgandhi.chatBridge;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class Identity {
    public static final Identity server = new Identity();

    private int justDoNotWantThisToBeRecognizedAsAUtilityClassIAmSorry;
    private Identity(){}

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

    public static Identity.Dc get(Member m){
        return get(m.getUser());
    }
    public static Identity.Dc get(User u){
        return new Dc(u.getId());
    }
    public static Identity.Mc get(OfflinePlayer p){
        return new Mc(p.getUniqueId());
    }
}
