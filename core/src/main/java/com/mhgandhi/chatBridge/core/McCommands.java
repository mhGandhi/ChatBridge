package com.mhgandhi.chatBridge.core;

import com.mhgandhi.chatBridge.core.domain.CommandRegistrar;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;

public final class McCommands {
    private final IdentityManager identities;
    private final Formatter formatter;

    public McCommands(IdentityManager identities, Formatter formatter) {
        this.identities = identities;
        this.formatter = formatter;
    }


    ///////////////////// COMMANDS (nicht vergessen neue unten zu registrieren)
    public Component status(UUID playerId) {
        return formatter.minecraftStatus(new Identity.Mc(playerId));
    }

    public Component disconnect(UUID playerId) {
        identities.clearMc(new Identity.Mc(playerId));
        return status(playerId);
    }

    public Component connect(UUID playerId, String discordId) {
        //if(identities.isLinkedMc(new Identity.Mc(playerId))){
            //todo send "already linked" msg
        //}
        if(discordId == null){
            throw new RuntimeException("Please enter the Id of the Discord-User you are trying to link to your Minecraft account. It will autocomplete as soon as you link from the other side so you dont have to type all the numbers"); //todo formatter
        }

        identities.claimMcDc(
                new Identity.Mc(playerId),
                new Identity.Dc(discordId)
        );
        return status(playerId);
    }

    public List<String> suggestConnect(UUID playerId, String prefix) {
        return identities.claimsOnMc(new Identity.Mc(playerId))
                .stream()
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
    /////////////////////


    public void registerInto(CommandRegistrar registrar) {
        registrar.registerStatusCommand(this::status);
        registrar.registerDisconnectCommand(this::disconnect);
        registrar.registerConnectCommand(
                this::connect,
                this::suggestConnect
        );
        //alle Commands registrieren!
    }
}
