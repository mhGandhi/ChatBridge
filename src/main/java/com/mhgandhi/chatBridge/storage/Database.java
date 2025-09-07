package com.mhgandhi.chatBridge.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Database implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Path dbPath;
    private Connection conn;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().toPath().resolve("data.db");
    }

    public void open() throws Exception {
        Files.createDirectories(plugin.getDataFolder().toPath());
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        conn.setAutoCommit(false);

        try (Statement st = conn.createStatement()) {
            st.addBatch("""
                CREATE TABLE IF NOT EXISTS mc_claims (
                  mc_uuid TEXT PRIMARY KEY,
                  claimed_dc_id TEXT
                );""");
            st.addBatch("""
                CREATE TABLE IF NOT EXISTS dc_claims (
                  dc_id TEXT PRIMARY KEY,
                  claimed_mc_uuid TEXT
                );""");
            st.addBatch("""
                CREATE VIEW IF NOT EXISTS active_links AS
                SELECT m.mc_uuid, d.dc_id
                  FROM mc_claims m
                  JOIN dc_claims d
                    ON m.claimed_dc_id = d.dc_id
                   AND d.claimed_mc_uuid = m.mc_uuid;""");
            st.executeBatch();
            conn.commit();
        }
    }

    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------
    // Linking (one-sided claims)
    // ------------------------------------------------------------

    /** Minecraft account mcUuid claims Discord account dcId. */
    public void linkMcToDc(String mcUuid, String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO mc_claims (mc_uuid, claimed_dc_id)
            VALUES (?, ?)
            ON CONFLICT(mc_uuid) DO UPDATE SET claimed_dc_id = excluded.claimed_dc_id;""")) {
            ps.setString(1, mcUuid);
            ps.setString(2, dcId);
            ps.executeUpdate();
        }
        conn.commit();
    }

    /** Discord account dcId claims Minecraft account mcUuid. */
    public void linkDcToMc(String dcId, String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO dc_claims (dc_id, claimed_mc_uuid)
            VALUES (?, ?)
            ON CONFLICT(dc_id) DO UPDATE SET claimed_mc_uuid = excluded.claimed_mc_uuid;""")) {
            ps.setString(1, dcId);
            ps.setString(2, mcUuid);
            ps.executeUpdate();
        }
        conn.commit();
    }

    // ------------------------------------------------------------
    // Clearing (remove one-sided claims)
    // ------------------------------------------------------------

    public void clearMcClaim(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE mc_claims SET claimed_dc_id = NULL WHERE mc_uuid = ?")) {
            ps.setString(1, mcUuid);
            ps.executeUpdate();
        }
        conn.commit();
    }

    public void clearDcClaim(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE dc_claims SET claimed_mc_uuid = NULL WHERE dc_id = ?")) {
            ps.setString(1, dcId);
            ps.executeUpdate();
        }
        conn.commit();
    }

    // ------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------

    /** True if mcUuid and some dcId mutually claim each other (present in active_links). */
    public boolean isActiveByMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM active_links WHERE mc_uuid = ? LIMIT 1")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** True if dcId and some mcUuid mutually claim each other (present in active_links). */
    public boolean isActiveByDc(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM active_links WHERE dc_id = ? LIMIT 1")) {
            ps.setString(1, dcId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Returns the dcId that mcUuid is currently claiming (one-sided), if any. */
    public Optional<String> getClaimedDiscordForMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT claimed_dc_id FROM mc_claims WHERE mc_uuid = ?")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /** Returns the mcUuid that dcId is currently claiming (one-sided), if any. */
    public Optional<String> getClaimedMinecraftForDc(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT claimed_mc_uuid FROM dc_claims WHERE dc_id = ?")) {
            ps.setString(1, dcId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /** One-sided: which Discord IDs are currently claiming this Minecraft UUID? */
    public List<String> getClaimsOnMc(String mcUuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT dc_id FROM dc_claims WHERE claimed_mc_uuid = ?")) {
            ps.setString(1, mcUuid);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getString(1));
                return out;
            }
        }
    }

    /** One-sided: which Minecraft UUIDs are currently claiming this Discord ID? */
    public List<String> getClaimsOnDc(String dcId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT mc_uuid FROM mc_claims WHERE claimed_dc_id = ?")) {
            ps.setString(1, dcId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getString(1));
                return out;
            }
        }
    }
}
