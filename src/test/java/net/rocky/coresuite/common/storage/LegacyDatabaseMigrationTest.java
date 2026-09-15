package net.rocky.coresuite.common.storage;

import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDatabaseMigrationTest {
    private Connection database() throws SQLException { return DriverManager.getConnection("jdbc:sqlite::memory:"); }
    private void sql(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement()) { s.execute(sql); } }
    private int count(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            r.next(); return r.getInt(1);
        }
    }
    @Test void copiesBatchesUsingColumnNamesAndKeepsSource() throws Exception {
        try (Connection source = database(); Connection target = database()) {
            sql(source, "CREATE TABLE coresuite_fishing (uuid TEXT PRIMARY KEY, xp INTEGER)");
            sql(target, "CREATE TABLE coresuite_fishing (xp INTEGER, uuid TEXT PRIMARY KEY, extra INTEGER DEFAULT 9)");
            for (int i=0; i<201; i++) sql(source, "INSERT INTO coresuite_fishing VALUES ('" + i + "', " + i + ")");
            LegacyDatabaseMigration.copy(source, target);
            assertEquals(201, count(source, "coresuite_fishing"));
            assertEquals(201, count(target, "coresuite_fishing"));
            try (Statement s = target.createStatement(); ResultSet r = s.executeQuery("SELECT xp, extra FROM coresuite_fishing WHERE uuid='200'")) {
                assertTrue(r.next()); assertEquals(200, r.getInt(1)); assertEquals(9, r.getInt(2));
            }
            assertTrue(target.getAutoCommit());
        }
    }
    @Test void refusesUncoveredSourceTablesBeforeWriting() throws Exception {
        try (Connection source = database(); Connection target = database()) {
            sql(source, "CREATE TABLE coresuite_fishing (uuid TEXT)");
            sql(source, "INSERT INTO coresuite_fishing VALUES ('alice')");
            sql(source, "CREATE TABLE coresuite_fish_bag (uuid TEXT)");
            sql(target, "CREATE TABLE coresuite_fishing (uuid TEXT)");
            assertThrows(SQLException.class, () -> LegacyDatabaseMigration.copy(source, target));
            assertEquals(0, count(target, "coresuite_fishing"));
        }
    }
    @Test void neverOverwritesExistingDestinationRows() throws Exception {
        try (Connection source = database(); Connection target = database()) {
            for (Connection c : new Connection[]{source, target}) sql(c, "CREATE TABLE coresuite_fishing (uuid TEXT, xp INTEGER)");
            sql(source, "INSERT INTO coresuite_fishing VALUES ('alice',1)");
            sql(target, "INSERT INTO coresuite_fishing VALUES ('alice',999)");
            assertThrows(SQLException.class, () -> LegacyDatabaseMigration.copy(source, target));
            try (Statement s = target.createStatement(); ResultSet r = s.executeQuery("SELECT xp FROM coresuite_fishing")) {
                assertTrue(r.next()); assertEquals(999, r.getInt(1));
            }
        }
    }
    @Test void failureInLaterTableRollsBackEarlierTable() throws Exception {
        try (Connection source = database(); Connection target = database()) {
            sql(source, "CREATE TABLE coresuite_fishing (uuid TEXT)");
            sql(source, "INSERT INTO coresuite_fishing VALUES ('alice')");
            sql(source, "CREATE TABLE coresuite_playtime (uuid TEXT, minutes INTEGER)");
            sql(source, "INSERT INTO coresuite_playtime VALUES ('alice',-1)");
            sql(target, "CREATE TABLE coresuite_fishing (uuid TEXT)");
            sql(target, "CREATE TABLE coresuite_playtime (uuid TEXT, minutes INTEGER CHECK(minutes>=0))");
            assertThrows(SQLException.class, () -> LegacyDatabaseMigration.copy(source, target));
            assertEquals(0, count(target, "coresuite_fishing"));
            assertEquals(0, count(target, "coresuite_playtime"));
            assertEquals(1, count(source, "coresuite_playtime"));
            assertTrue(target.getAutoCommit());
        }
    }
    @Test void missingDestinationTableAborts() throws Exception {
        try (Connection source = database(); Connection target = database()) {
            sql(source, "CREATE TABLE coresuite_fishing (uuid TEXT)");
            assertThrows(SQLException.class, () -> LegacyDatabaseMigration.copy(source, target));
            assertTrue(target.getAutoCommit());
        }
    }
}
