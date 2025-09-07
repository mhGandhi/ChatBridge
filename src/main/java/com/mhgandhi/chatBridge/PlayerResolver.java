package com.mhgandhi.chatBridge;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class PlayerResolver {

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$");

    public static OfflinePlayer resolve(String input) {
        // Case 1: Input looks like a UUID
        if (UUID_REGEX.matcher(input).matches()) {
            try {
                UUID uuid = UUID.fromString(input.replace("-", ""));
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                if (op.hasPlayedBefore()) {
                    return op; // Known to server already
                } else {
                    // Query Mojang to fill in the name
                    String name = fetchNameFromMojang(uuid);
                    if (name == null) return null;
                    // Construct wrapper with known UUID
                    return Bukkit.getOfflinePlayer(uuid);
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Case 2: Treat as a username
        UUID uuid = fetchUuidFromMojang(input);
        if (uuid == null) return null;
        return Bukkit.getOfflinePlayer(uuid);
    }

    private static UUID fetchUuidFromMojang(String name) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() != 200) return null;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            String id = obj.get("id").getAsString();
            return UUID.fromString(id.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                    "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fetchNameFromMojang(UUID uuid) {
        try {
            String raw = uuid.toString().replace("-", "");
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + raw);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() != 200) return null;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            return obj.get("name").getAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
