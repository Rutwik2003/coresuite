package net.rocky.coresuite.module.playtimerewards;

import java.sql.*;
import java.util.*;

/** All methods run on the serial database worker; callers own the connection. */
final class PlaytimeStore {
    record Data(int afk, int adjustment) {}

    static void initialize(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS coresuite_playtime (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64), afk_seconds INTEGER DEFAULT 0, adjust_seconds INTEGER DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS coresuite_playtime_claims (uuid VARCHAR(36), reward_id VARCHAR(191), claim_time BIGINT, PRIMARY KEY (uuid, reward_id))");
        }
    }

    static Data load(Connection c, UUID uuid, String name, boolean mysql) throws SQLException {
        String insert = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (PreparedStatement s = c.prepareStatement(insert + " INTO coresuite_playtime (uuid, player_name) VALUES (?, ?)")) {
            s.setString(1, uuid.toString()); s.setString(2, name); s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement("SELECT afk_seconds, adjust_seconds FROM coresuite_playtime WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new SQLException("Missing playtime row after initialization");
                return new Data(r.getInt(1), r.getInt(2));
            }
        }
    }

    static Map<String, Long> claims(Connection c, UUID uuid) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        try (PreparedStatement s = c.prepareStatement("SELECT reward_id, claim_time FROM coresuite_playtime_claims WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            try (ResultSet r = s.executeQuery()) { while (r.next()) result.put(r.getString(1), r.getLong(2)); }
        }
        return result;
    }

    // Negative timestamps are durable reservations. Never replay an uncertain external payout.
    static boolean reserve(Connection c, UUID uuid, String id, long now, long cooldown, boolean mysql) throws SQLException {
        String insert = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (PreparedStatement s = c.prepareStatement(insert + " INTO coresuite_playtime_claims (uuid,reward_id,claim_time) VALUES (?,?,0)")) {
            s.setString(1, uuid.toString()); s.setString(2, id); s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement("UPDATE coresuite_playtime_claims SET claim_time=? WHERE uuid=? AND reward_id=? AND (claim_time=0 OR (claim_time>0 AND ? > 0 AND claim_time <= ?))")) {
            s.setLong(1, -now); s.setString(2, uuid.toString()); s.setString(3, id);
            s.setLong(4, cooldown); s.setLong(5, now - cooldown);
            return s.executeUpdate() == 1;
        }
    }

    static void finish(Connection c, UUID uuid, String id, long reservation, long result) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE coresuite_playtime_claims SET claim_time=? WHERE uuid=? AND reward_id=? AND claim_time=?")) {
            s.setLong(1, result); s.setString(2, uuid.toString()); s.setString(3, id); s.setLong(4, -reservation);
            if (s.executeUpdate() != 1) throw new SQLException("Playtime claim reservation changed unexpectedly: " + uuid + "/" + id);
        }
    }
}
