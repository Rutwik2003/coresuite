package net.rocky.coresuite.module.playtimerewards;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlaytimeStoreTest {
    private Connection database() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        PlaytimeStore.initialize(c);
        return c;
    }
    @Test void reservationBlocksDuplicateAndUncertainPayoutEvenAfterCooldown() throws Exception {
        try (Connection c = database()) {
            UUID uuid = UUID.randomUUID();
            assertTrue(PlaytimeStore.reserve(c, uuid, "reward", 1000, 100, false));
            assertFalse(PlaytimeStore.reserve(c, uuid, "reward", 5000, 100, false));
            assertEquals(-1000L, PlaytimeStore.claims(c, uuid).get("reward"));
            PlaytimeStore.finish(c, uuid, "reward", 1000, 1000);
            assertFalse(PlaytimeStore.reserve(c, uuid, "reward", 1050, 100, false));
            assertTrue(PlaytimeStore.reserve(c, uuid, "reward", 1100, 100, false));
        }
    }
    @Test void oneTimeRewardCannotRepeatAndCancelledDeliveryCanRetry() throws Exception {
        try (Connection c = database()) {
            UUID uuid = UUID.randomUUID();
            assertTrue(PlaytimeStore.reserve(c, uuid, "one", 1000, 0, false));
            PlaytimeStore.finish(c, uuid, "one", 1000, 0);
            assertTrue(PlaytimeStore.reserve(c, uuid, "one", 2000, 0, false));
            assertThrows(SQLException.class, () -> PlaytimeStore.finish(c, uuid, "one", 1000, 1000));
            PlaytimeStore.finish(c, uuid, "one", 2000, 2000);
            assertFalse(PlaytimeStore.reserve(c, uuid, "one", Long.MAX_VALUE / 2, 0, false));
        }
    }
    @Test void loadingExistingDataDoesNotResetPersistedProgress() throws Exception {
        try (Connection c = database()) {
            UUID uuid = UUID.randomUUID();
            assertEquals(new PlaytimeStore.Data(0,0), PlaytimeStore.load(c, uuid, "Alice", false));
            try (PreparedStatement s = c.prepareStatement("UPDATE coresuite_playtime SET afk_seconds=99, adjust_seconds=-42 WHERE uuid=?")) {
                s.setString(1, uuid.toString()); s.executeUpdate();
            }
            assertEquals(new PlaytimeStore.Data(99,-42), PlaytimeStore.load(c, uuid, "AliceNew", false));
        }
    }
    @Test void databaseFailureIsNotTreatedAsUnclaimed() throws Exception {
        try (Connection c = database()) {
            try (Statement s = c.createStatement()) { s.execute("DROP TABLE coresuite_playtime_claims"); }
            assertThrows(SQLException.class, () -> PlaytimeStore.claims(c, UUID.randomUUID()));
            assertThrows(SQLException.class, () -> PlaytimeStore.reserve(c, UUID.randomUUID(), "reward", 1000, 0, false));
        }
    }
}
