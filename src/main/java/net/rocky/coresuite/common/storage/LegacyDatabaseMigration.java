package net.rocky.coresuite.common.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Conservative migration for the original schema; newer schemas need a full migration. */
final class LegacyDatabaseMigration {
    private static final Set<String> SUPPORTED = Set.of(
            "coresuite_playtime", "coresuite_fishing", "coresuite_fish_logbook",
            "coresuite_kills", "coresuite_referrals", "coresuite_reward_codes");

    private LegacyDatabaseMigration() {}

    static void copy(Connection source, Connection target) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = source.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (table.startsWith("sqlite_")) continue;
                if (!SUPPORTED.contains(table)) {
                    throw new SQLException("Automatic migration cannot cover source table: " + table);
                }
                tables.add(table);
            }
        }
        if (tables.isEmpty()) throw new SQLException("No supported source tables found");
        if (!target.getAutoCommit()) throw new SQLException("Migration requires a dedicated connection");
        target.setAutoCommit(false);
        try {
            // Check every destination before making any changes.
            for (String table : tables) {
                try (Statement stmt = target.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
                    if (rs.next()) throw new SQLException("Destination is not empty: " + table);
                }
            }
            for (String table : tables) copyTable(source, target, table);
            target.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                target.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            target.setAutoCommit(true);
        }
    }

    private static void copyTable(Connection source, Connection target, String table) throws SQLException {
        try (Statement stmt = source.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM `" + table + "`")) {
            ResultSetMetaData metadata = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add("`" + metadata.getColumnName(i).replace("`", "``") + "`");
                placeholders.add("?");
            }
            String sql = "INSERT INTO `" + table + "` (" + String.join(",", columns)
                    + ") VALUES (" + String.join(",", placeholders) + ")";
            try (PreparedStatement insert = target.prepareStatement(sql)) {
                int count = 0;
                while (rs.next()) {
                    for (int i = 1; i <= metadata.getColumnCount(); i++) insert.setObject(i, rs.getObject(i));
                    insert.addBatch();
                    if (++count % 100 == 0) {
                        insert.executeBatch();
                        insert.clearBatch();
                    }
                }
                insert.executeBatch();
            }
        }
    }
}
