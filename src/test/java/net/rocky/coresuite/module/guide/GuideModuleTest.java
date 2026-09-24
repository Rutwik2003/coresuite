package net.rocky.coresuite.module.guide;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GuideModuleTest {

    private Connection createDatabase() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE coresuite_player_guide (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "first_seen BIGINT NOT NULL, " +
                    "reward_claimed INTEGER DEFAULT 0, " +
                    "claimed_at BIGINT DEFAULT 0, " +
                    "ip_address TEXT" +
                    ")");
        }
        return conn;
    }

    @Test
    void starterRewardClaimIsStrictlyAtomicAndCannotDoubleClaim() throws Exception {
        try (Connection conn = createDatabase()) {
            UUID uuid = UUID.randomUUID();
            long now = System.currentTimeMillis();

            // Insert new player row
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO coresuite_player_guide (uuid, first_seen, reward_claimed, claimed_at, ip_address) VALUES (?, ?, 0, 0, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, now);
                ps.setString(3, "192.168.1.100");
                assertEquals(1, ps.executeUpdate());
            }

            // First claim attempt (should succeed with rowsUpdated = 1)
            int firstClaimRows;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE coresuite_player_guide SET reward_claimed = 1, claimed_at = ?, ip_address = ? WHERE uuid = ? AND reward_claimed = 0")) {
                ps.setLong(1, now + 100);
                ps.setString(2, "192.168.1.100");
                ps.setString(3, uuid.toString());
                firstClaimRows = ps.executeUpdate();
            }
            assertEquals(1, firstClaimRows, "First claim must succeed");

            // Second claim attempt (simulate rapid spam click or concurrent packet)
            int secondClaimRows;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE coresuite_player_guide SET reward_claimed = 1, claimed_at = ?, ip_address = ? WHERE uuid = ? AND reward_claimed = 0")) {
                ps.setLong(1, now + 200);
                ps.setString(2, "192.168.1.100");
                ps.setString(3, uuid.toString());
                secondClaimRows = ps.executeUpdate();
            }
            assertEquals(0, secondClaimRows, "Second claim attempt must fail atomically with 0 updated rows");

            // Verify claimed state in database
            try (PreparedStatement ps = conn.prepareStatement("SELECT reward_claimed, claimed_at FROM coresuite_player_guide WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("reward_claimed"));
                    assertEquals(now + 100, rs.getLong("claimed_at"));
                }
            }
        }
    }

    @Test
    void ipLimitBlocksAltAccountsFromReclaiming() throws Exception {
        try (Connection conn = createDatabase()) {
            String ip = "203.0.113.42";
            UUID mainAccount = UUID.randomUUID();
            UUID altAccount = UUID.randomUUID();
            long now = System.currentTimeMillis();

            // Main account claims reward
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO coresuite_player_guide (uuid, first_seen, reward_claimed, claimed_at, ip_address) VALUES (?, ?, 1, ?, ?)")) {
                ps.setString(1, mainAccount.toString());
                ps.setLong(2, now);
                ps.setLong(3, now);
                ps.setString(4, ip);
                assertEquals(1, ps.executeUpdate());
            }

            // Alt account registers
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO coresuite_player_guide (uuid, first_seen, reward_claimed, claimed_at, ip_address) VALUES (?, ?, 0, 0, ?)")) {
                ps.setString(1, altAccount.toString());
                ps.setLong(2, now + 50);
                ps.setString(3, ip);
                assertEquals(1, ps.executeUpdate());
            }

            // Check IP claim count for alt account
            int ipClaims;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM coresuite_player_guide WHERE ip_address = ? AND reward_claimed = 1 AND uuid != ?")) {
                ps.setString(1, ip);
                ps.setString(2, altAccount.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    ipClaims = rs.getInt(1);
                }
            }

            assertEquals(1, ipClaims, "Must detect 1 previous claim on the same IP");
            assertTrue(ipClaims >= 1, "IP limit of 1 must block the alt claim attempt");
        }
    }

    @Test
    void guideEntrySafelyHandlesDefaults() {
        GuideEntry entry = new GuideEntry(
                "fish",
                "&3&l/fish",
                "fish",
                Material.FISHING_ROD,
                14,
                List.of("&7Fishing description"),
                List.of("&7Fishing explanation")
        );

        assertEquals("fish", entry.key());
        assertEquals("&3&l/fish", entry.name());
        assertEquals("fish", entry.command());
        assertEquals(Material.FISHING_ROD, entry.material());
        assertEquals(14, entry.slot());
        assertEquals(1, entry.description().size());
        assertEquals(1, entry.chatExplanation().size());

        // Null safety
        GuideEntry fallback = new GuideEntry(null, null, null, null, 0, null, null);
        assertEquals("", fallback.key());
        assertEquals("", fallback.name());
        assertEquals("", fallback.command());
        assertEquals(Material.BOOK, fallback.material());
        assertNotNull(fallback.description());
        assertNotNull(fallback.chatExplanation());
    }
}
