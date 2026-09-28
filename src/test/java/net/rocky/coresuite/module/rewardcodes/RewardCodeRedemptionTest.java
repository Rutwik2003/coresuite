package net.rocky.coresuite.module.rewardcodes;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewardCodeRedemptionTest {
    @Test void reservesOnceEnforcesIpAndGlobalLimitAndLeavesConnectionUsable() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE coresuite_reward_codes(uuid TEXT, player_name TEXT, code TEXT, time INTEGER, ip TEXT, PRIMARY KEY(uuid,code))");
            }
            UUID first = UUID.randomUUID();
            assertNull(RewardCodeRedemption.reserve(connection, first, "First", "SUMMER", "ip1", false, 2));
            assertNotNull(RewardCodeRedemption.reserve(connection, first, "First", "summer", "ip1", true, 2));
            assertNotNull(RewardCodeRedemption.reserve(connection, UUID.randomUUID(), "Alt", "summer", "ip1", false, 2));
            assertNull(RewardCodeRedemption.reserve(connection, UUID.randomUUID(), "Second", "summer", "ip2", false, 2));
            assertNotNull(RewardCodeRedemption.reserve(connection, UUID.randomUUID(), "Third", "summer", "ip3", true, 2));
            assertTrue(connection.getAutoCommit());
            try (var statement=connection.createStatement(); var rows=statement.executeQuery("SELECT COUNT(*) FROM coresuite_reward_codes")) {
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
            }
        }
    }
}
