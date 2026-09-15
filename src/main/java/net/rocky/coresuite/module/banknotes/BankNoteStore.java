package net.rocky.coresuite.module.banknotes;

import java.sql.*;

/** SQL-only settlement operations. Pending states survive a crash and are never auto-retried. */
final class BankNoteStore {
    static final int AVAILABLE = 0, REDEEMED = 1, REDEMPTION_PENDING = 2, ISSUE_PENDING = 3;
    record Reservation(double amount, int state) {}

    static Reservation reserve(Connection conn, String id, String player) throws SQLException {
        double amount;
        int state;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT amount, redeemed FROM coresuite_banknotes WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return new Reservation(0, -1);
                amount = rs.getDouble(1);
                state = rs.getInt(2);
            }
        }
        if (state != AVAILABLE) return new Reservation(amount, state);
        if (!Double.isFinite(amount) || amount <= 0 || (id.startsWith("XP-") && (amount > Integer.MAX_VALUE || amount != Math.floor(amount)))) return new Reservation(0, -1);
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_banknotes SET redeemed = 2, redeemed_by = ?, redeem_time = ? WHERE id = ? AND redeemed = 0")) {
            stmt.setString(1, player);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, id);
            return stmt.executeUpdate() == 1 ? new Reservation(amount, AVAILABLE) : new Reservation(amount, REDEMPTION_PENDING);
        }
    }

    static void finish(Connection conn, String id, boolean paid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_banknotes SET redeemed = ? WHERE id = ? AND redeemed = 2")) {
            stmt.setInt(1, paid ? REDEEMED : AVAILABLE);
            stmt.setString(2, id);
            if (stmt.executeUpdate() != 1) throw new SQLException("Reservation missing for " + id);
        }
    }

    static void createPending(Connection conn, String id, double amount, String owner, String recipient) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO coresuite_banknotes (id, amount, owner, issue_time, redeemed, redeemed_by) VALUES (?, ?, ?, ?, 3, ?)")) {
            stmt.setString(1, id); stmt.setDouble(2, amount); stmt.setString(3, owner);
            stmt.setLong(4, System.currentTimeMillis()); stmt.setString(5, recipient); stmt.executeUpdate();
        }
    }

    static void activate(Connection conn, String id, String recipient) throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_banknotes SET redeemed = 0 WHERE id = ? AND redeemed = 3")) {
                stmt.setString(1, id);
                if (stmt.executeUpdate() != 1) throw new SQLException("Pending issuance missing for " + id);
            }
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO coresuite_banknote_deliveries (note_id, recipient) VALUES (?, ?)")) {
                stmt.setString(1, id); stmt.setString(2, recipient); stmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) { conn.rollback(); throw e; }
        finally { conn.setAutoCommit(true); }
    }
}
