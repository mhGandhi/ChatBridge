package com.mhgandhi.dcBridge.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Optional;

public final class Database implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Path dbPath;
    private Connection conn;

    public Database(JavaPlugin plugin) { this.plugin = plugin; this.dbPath = plugin.getDataFolder().toPath().resolve("data.db"); }

    public void open() throws Exception {
        Files.createDirectories(plugin.getDataFolder().toPath());
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.addBatch("""
                CREATE TABLE IF NOT EXISTS mc_claims (
                  mc_uuid TEXT PRIMARY KEY,
                  mc_name TEXT NOT NULL,
                  skin_face_url TEXT,
                  claimed_dc_id TEXT,
                  claimed_at INTEGER,
                  updated_at INTEGER NOT NULL
                );""");
            st.addBatch("""
                CREATE TABLE IF NOT EXISTS dc_claims (
                  dc_id TEXT PRIMARY KEY,
                  dc_username TEXT NOT NULL,
                  dc_nick TEXT,
                  avatar_url TEXT,
                  claimed_mc_uuid TEXT,
                  claimed_at INTEGER,
                  updated_at INTEGER NOT NULL
                );""");
            st.addBatch("""
                CREATE VIEW IF NOT EXISTS active_links AS
                SELECT m.mc_uuid, d.dc_id, m.mc_name, d.dc_username, d.dc_nick, d.avatar_url, m.skin_face_url
                FROM mc_claims m
                JOIN dc_claims d
                  ON m.claimed_dc_id = d.dc_id
                 AND d.claimed_mc_uuid = m.mc_uuid;""");
            st.executeBatch();
            conn.commit();
        }
    }

    public void close() { try { if (conn != null) conn.close(); } catch (Exception ignored) {} }

///  //////////////////////////////////

    public void upsertMcMeta(String mcUuid, String mcName, String skinFaceUrl) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO mc_claims(mc_uuid, mc_name, skin_face_url, updated_at)
            VALUES(?,?,?,?)
            ON CONFLICT(mc_uuid) DO UPDATE SET
              mc_name=excluded.mc_name, skin_face_url=excluded.skin_face_url, updated_at=excluded.updated_at;""")) {
            ps.setString(1, mcUuid); ps.setString(2, mcName); ps.setString(3, skinFaceUrl); ps.setLong(4, now());
            ps.executeUpdate();
        }
        conn.commit();
    }

    public void mcClaimsDiscord(String mcUuid, String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO mc_claims(mc_uuid, mc_name, skin_face_url, claimed_dc_id, claimed_at, updated_at)
            VALUES(?, COALESCE((SELECT mc_name FROM mc_claims WHERE mc_uuid=?),'unknown'),
                   COALESCE((SELECT skin_face_url FROM mc_claims WHERE mc_uuid=?),NULL),
                   ?, ?, ?)
            ON CONFLICT(mc_uuid) DO UPDATE SET
              claimed_dc_id=excluded.claimed_dc_id, claimed_at=excluded.claimed_at, updated_at=excluded.updated_at;""")) {
            long t = now();
            ps.setString(1, mcUuid); ps.setString(2, mcUuid); ps.setString(3, mcUuid);
            ps.setString(4, dcId); ps.setLong(5, t); ps.setLong(6, t);
            ps.executeUpdate();
        }
        conn.commit();
    }

    public void upsertDcMeta(String dcId, String username, String nick, String avatarUrl) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO dc_claims(dc_id, dc_username, dc_nick, avatar_url, updated_at)
            VALUES(?,?,?,?,?)
            ON CONFLICT(dc_id) DO UPDATE SET
              dc_username=excluded.dc_username, dc_nick=excluded.dc_nick, avatar_url=excluded.avatar_url, updated_at=excluded.updated_at;""")) {
            ps.setString(1, dcId); ps.setString(2, username); ps.setString(3, nick); ps.setString(4, avatarUrl); ps.setLong(5, now());
            ps.executeUpdate();
        }
        conn.commit();
    }

    public void dcClaimsMinecraft(String dcId, String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO dc_claims(dc_id, dc_username, dc_nick, avatar_url, claimed_mc_uuid, claimed_at, updated_at)
            VALUES(?, COALESCE((SELECT dc_username FROM dc_claims WHERE dc_id=?),'unknown'),
                   COALESCE((SELECT dc_nick FROM dc_claims WHERE dc_id=?),NULL),
                   COALESCE((SELECT avatar_url FROM dc_claims WHERE dc_id=?),NULL),
                   ?, ?, ?)
            ON CONFLICT(dc_id) DO UPDATE SET
              claimed_mc_uuid=excluded.claimed_mc_uuid, claimed_at=excluded.claimed_at, updated_at=excluded.updated_at;""")) {
            long t = now();
            ps.setString(1, dcId); ps.setString(2, dcId); ps.setString(3, dcId); ps.setString(4, dcId);
            ps.setString(5, mcUuid); ps.setLong(6, t); ps.setLong(7, t);
            ps.executeUpdate();
        }
        conn.commit();
    }

///     ////////////////////////////////////////Queries

    public boolean whitelistAllow(String mcUuid) throws Exception {//todo take identity as argument
        if (isActiveByMc(mcUuid)) return true;
        //if (hasMcClaim(mcUuid)) return true; todo ???
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM dc_claims WHERE claimed_mc_uuid=? LIMIT 1")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean isActiveByMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM active_links WHERE mc_uuid=?")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean isActiveByDc(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM active_links WHERE dc_id=?")) {
            ps.setString(1, dcId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean hasMcClaim(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM mc_claims WHERE mc_uuid=? AND claimed_dc_id IS NOT NULL")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }


    public LinkedRow getActiveLinkByMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT mc_uuid, dc_id, mc_name, dc_username, dc_nick, avatar_url, skin_face_url
            FROM active_links WHERE mc_uuid=? LIMIT 1""")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapLinkedRow(rs);
            }
        }
    }

    public LinkedRow getActiveLinkByDc(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT mc_uuid, dc_id, mc_name, dc_username, dc_nick, avatar_url, skin_face_url
            FROM active_links WHERE dc_id=? LIMIT 1""")) {
            ps.setString(1, dcId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapLinkedRow(rs);
            }
        }
    }

    public void clearMcClaim(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
        UPDATE mc_claims SET claimed_dc_id=NULL, claimed_at=NULL, updated_at=? WHERE mc_uuid=?""")) {
            ps.setLong(1, now());
            ps.setString(2, mcUuid);
            ps.executeUpdate();
        }
        conn.commit();
    }

    public void clearDcClaim(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
        UPDATE dc_claims SET claimed_mc_uuid=NULL, claimed_at=NULL, updated_at=? WHERE dc_id=?""")) {
            ps.setLong(1, now());
            ps.setString(2, dcId);
            ps.executeUpdate();
        }
        conn.commit();
    }

    /** Discords that are currently claiming this mcUuid (used for MC /connect tab-complete) */
    public java.util.List<DcRow> findPendingDiscordClaimsForMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
        SELECT dc_id, dc_username, dc_nick, avatar_url, claimed_mc_uuid
        FROM dc_claims WHERE claimed_mc_uuid=?""")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.ArrayList<DcRow> out = new java.util.ArrayList<>();
                while (rs.next()) out.add(mapDc(rs));
                return out;
            }
        }
    }


    public Optional<McRow> getMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT mc_uuid, mc_name, skin_face_url, claimed_dc_id FROM mc_claims WHERE mc_uuid=?""")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(mapMc(rs)) : Optional.empty(); }
        }
    }

    public Optional<DcRow> getDc(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT dc_id, dc_username, dc_nick, avatar_url, claimed_mc_uuid FROM dc_claims WHERE dc_id=?""")) {
            ps.setString(1, dcId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(mapDc(rs)) : Optional.empty(); }
        }
    }

    private static LinkedRow mapLinkedRow(ResultSet rs) throws SQLException {
        return new LinkedRow(
                rs.getString("mc_uuid"),
                rs.getString("dc_id"),
                rs.getString("mc_name"),
                rs.getString("dc_username"),
                rs.getString("dc_nick"),
                rs.getString("avatar_url"),
                rs.getString("skin_face_url"));
    }
    private static McRow mapMc(ResultSet rs) throws SQLException {
        return new McRow(rs.getString("mc_uuid"), rs.getString("mc_name"), rs.getString("skin_face_url"), rs.getString("claimed_dc_id"));
    }
    private static DcRow mapDc(ResultSet rs) throws SQLException {
        return new DcRow(rs.getString("dc_id"), rs.getString("dc_username"), rs.getString("dc_nick"), rs.getString("avatar_url"), rs.getString("claimed_mc_uuid"));
    }
    private static long now() { return System.currentTimeMillis(); }

    /* Simple row records to move data upward */
    public record LinkedRow(String mcUuid, String dcId, String mcName, String dcUsername, String dcNick, String dcAvatarUrl, String mcSkinUrl) {}
    public record McRow(String mcUuid, String mcName, String skinFaceUrl, String claimedDcId) {}
    public record DcRow(String dcId, String dcUsername, String dcNick, String avatarUrl, String claimedMcUuid) {}
}
