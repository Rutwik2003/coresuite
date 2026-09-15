package net.rocky.coresuite.module.banknotes;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankNoteStoreTest {
    private Connection database() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE coresuite_banknotes (id TEXT PRIMARY KEY, amount REAL, owner TEXT, redeemed INTEGER DEFAULT 0, redeemed_by TEXT, issue_time BIGINT, redeem_time BIGINT)");
            stmt.execute("CREATE TABLE coresuite_banknote_deliveries (note_id TEXT PRIMARY KEY, recipient TEXT)");
        }
        return conn;
    }
    private int state(Connection conn, String id) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT redeemed FROM coresuite_banknotes WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }
    @Test void repeatedAttemptsCannotReserveTheSameNoteTwice() throws Exception {
        try (Connection conn = database()) {
            BankNoteStore.createPending(conn, "BN-test", 25, "owner", "recipient");
            BankNoteStore.activate(conn, "BN-test", "recipient");
            assertEquals(0, BankNoteStore.reserve(conn, "BN-test", "first").state());
            assertEquals(2, BankNoteStore.reserve(conn, "BN-test", "second").state());
            BankNoteStore.finish(conn, "BN-test", true);
            assertEquals(1, BankNoteStore.reserve(conn, "BN-test", "third").state());
        }
    }
    @Test void rejectedPayoutReleasesReservationButUncertainPayoutStaysReserved() throws Exception {
        try (Connection conn = database()) {
            BankNoteStore.createPending(conn, "BN-test", 25, "owner", "recipient");
            BankNoteStore.activate(conn, "BN-test", "recipient");
            BankNoteStore.reserve(conn, "BN-test", "player");
            assertEquals(2, state(conn, "BN-test"));
            BankNoteStore.finish(conn, "BN-test", false);
            assertEquals(0, state(conn, "BN-test"));
            assertEquals(0, BankNoteStore.reserve(conn, "BN-test", "retry").state());
        }
    }
    @Test void failedDeliveryRecordRollsBackActivation() throws Exception {
        try (Connection conn = database()) {
            BankNoteStore.createPending(conn, "BN-test", 25, "owner", "recipient");
            try (Statement stmt = conn.createStatement()) { stmt.execute("DROP TABLE coresuite_banknote_deliveries"); }
            assertThrows(SQLException.class, () -> BankNoteStore.activate(conn, "BN-test", "recipient"));
            assertEquals(3, state(conn, "BN-test"));
            assertTrue(conn.getAutoCommit());
        }
    }
    @Test void invalidXpCannotReserveAndPendingIssueCannotRedeem() throws Exception {
        try (Connection conn = database()) {
            BankNoteStore.createPending(conn, "XP-test", (double)Integer.MAX_VALUE + 1, "owner", "recipient");
            assertEquals(3, BankNoteStore.reserve(conn, "XP-test", "player").state());
            BankNoteStore.activate(conn, "XP-test", "recipient");
            assertEquals(-1, BankNoteStore.reserve(conn, "XP-test", "player").state());
            assertEquals(0, state(conn, "XP-test"));
        }
    }
    @Test void separateConnectionsCannotBothReserve(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        String url = "jdbc:sqlite:" + folder.resolve("notes.db");
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE coresuite_banknotes (id TEXT PRIMARY KEY, amount REAL, redeemed INTEGER, redeemed_by TEXT, redeem_time BIGINT)");
            stmt.execute("INSERT INTO coresuite_banknotes VALUES ('BN-race', 10, 0, NULL, NULL)");
        }
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> claim = () -> {
                try (Connection conn = DriverManager.getConnection(url)) {
                    start.await();
                    return BankNoteStore.reserve(conn, "BN-race", Thread.currentThread().getName()).state();
                }
            };
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            start.countDown();
            var results = java.util.List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS), second.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(state -> state == 0).count());
            assertEquals(1, results.stream().filter(state -> state == 2).count());
        }
    }
}
