package net.rocky.coresuite.module.rewardcodes;

import java.sql.*;
import java.util.*;

/** Called on CoreSuite's single database worker: checks and reservation stay together. */
final class RewardCodeRedemption {
    static String reserve(Connection connection, UUID player, String name, String code, String ip,
                          boolean bypassIp, int maxUses) throws SQLException {
        String normalized = code.toLowerCase(Locale.ROOT);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (var query = connection.prepareStatement("SELECT uuid, ip FROM coresuite_reward_codes WHERE code = ?")) {
                query.setString(1, normalized);
                try (var rows = query.executeQuery()) {
                    int uses = 0;
                    while (rows.next()) {
                        uses++;
                        if (player.toString().equals(rows.getString("uuid"))) return "You have already redeemed this code!";
                        if (!bypassIp && ip != null && !ip.isBlank() && ip.equals(rows.getString("ip")))
                            return "This code has already been redeemed from your IP address!";
                    }
                    if (maxUses > 0 && uses >= maxUses) return "This reward code has reached its redemption limit.";
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO coresuite_reward_codes (uuid, player_name, code, time, ip) VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, player.toString()); insert.setString(2, name); insert.setString(3, normalized);
                insert.setLong(4, System.currentTimeMillis()); insert.setString(5, ip); insert.executeUpdate();
            }
            connection.commit();
            return null;
        } finally {
            connection.rollback();
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
