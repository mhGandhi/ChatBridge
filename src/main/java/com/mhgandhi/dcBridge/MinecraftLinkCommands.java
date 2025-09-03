package com.mhgandhi.dcBridge;

import com.mhgandhi.dcBridge.storage.Database;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class MinecraftLinkCommands implements CommandExecutor, TabCompleter {
    private final Database db;
    private final JDA jda;
    private static final Pattern SNOWFLAKE_RE = Pattern.compile("^\\d{16,22}$");

    public MinecraftLinkCommands(Database db, JDA jda) {
        this.db = db;
        this.jda = jda;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        //todo identityManager.upsert

        try {
            if (cmd.getName().equalsIgnoreCase("status")) {
                p.sendMessage(DcBridge.getFormatter().minecraftStatus(db, p.getUniqueId(), null));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("disconnect")) {
                db.clearMcClaim(p.getUniqueId().toString());
                p.sendMessage(DcBridge.getFormatter().minecraftStatus(db, p.getUniqueId(), null));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("connect")) {
                if (args.length == 0) {
                    p.sendMessage(DcBridge.getFormatter().minecraftStatus(db, p.getUniqueId(), null));
                    return true;
                }
                String raw = String.join(" ", args).trim(); // allow nick with spaces
                // Resolve Discord user: by ID (snowflake) or by name (best-effort, guild search if you have one)
                resolveDiscordUser(raw).thenAccept(optUser -> {
                    try {
                        if (optUser.isEmpty()) {
                            p.sendMessage("§cCould not resolve Discord user from: " + raw);
                            return;
                        }
                        User u = optUser.get();
                        // Upsert DC meta so status cards look nice todo identity mgr
                        db.upsertDcMeta(u.getId(), u.getName(), null, u.getEffectiveAvatarUrl());

                        db.mcClaimsDiscord(p.getUniqueId().toString(), u.getId());
                        // Show immediate feedback
                        p.sendMessage(DcBridge.getFormatter().minecraftStatus(db, p.getUniqueId(), u.getId()));
                    } catch (Exception ex) {
                        p.sendMessage("§cError: " + ex.getMessage());
                    }
                });
                return true;
            }
        } catch (Exception ex) {
            sender.sendMessage("§cError: " + ex.getMessage());
        }
        return true;
    }

    private CompletableFuture<Optional<User>> resolveDiscordUser(String raw) {//todo needs to be somewhere else
        // ID path
        if (SNOWFLAKE_RE.matcher(raw).matches()) {
            try {
                return jda.retrieveUserById(raw).submit().thenApply(Optional::of);
            } catch (Exception e) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        }
        // Name path (VERY heuristic; ideally search in your guild(s))
        // You can replace with guild-specific search for better results
        var hits = jda.getUserCache().stream()
                .filter(u -> u.getName().equalsIgnoreCase(raw))
                .findFirst();
        if (hits.isPresent()) return CompletableFuture.completedFuture(Optional.of(hits.get()));

        // Try a broader retrieve (this will hit API; beware rate limits)
        return jda.retrieveUserById(raw).submit()
                .thenApply(Optional::of)
                .exceptionally(x -> Optional.empty());
    }

    /** Tab complete: suggest Discord accounts already claiming this MC UUID */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (!cmd.getName().equalsIgnoreCase("connect")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();
        try {
            var claimers = db.findPendingDiscordClaimsForMc(p.getUniqueId().toString());
            if (claimers.isEmpty()) return Collections.emptyList();
            var prefix = args[0].toLowerCase();
            ArrayList<String> out = new ArrayList<>();
            for (var d : claimers) {
                var name = d.dcUsername();
                var id = d.dcId();
                if (name.toLowerCase().startsWith(prefix)) out.add(name);
                if (id.startsWith(prefix)) out.add(id);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
