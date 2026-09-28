package net.rocky.coresuite.module.gambling;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class CasinoStatsPersistenceTest {
    @Test void gamesAddToExistingTotalsWithoutDependingOnCache() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE coresuite_casino_stats (uuid TEXT PRIMARY KEY,player_name TEXT,total_wagered REAL,total_won REAL,games_played INTEGER,biggest_win REAL,last_played BIGINT)");
            }
            for (double win : new double[]{300,0,100}) {
                try (PreparedStatement s = c.prepareStatement(CasinoStatsManager.recordSql(false))) {
                    s.setString(1,"alice"); s.setString(2,"Alice"); s.setDouble(3,50);
                    s.setDouble(4,win); s.setDouble(5,win); s.setLong(6,1000); s.executeUpdate();
                }
            }
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM coresuite_casino_stats")) {
                assertTrue(r.next()); assertEquals(150,r.getDouble("total_wagered"));
                assertEquals(400,r.getDouble("total_won")); assertEquals(3,r.getInt("games_played"));
                assertEquals(300,r.getDouble("biggest_win"));
            }
        }
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void totalsSurviveReopenAndContinueAccumulating() throws Exception {
        String url="jdbc:sqlite:"+directory.resolve("casino.db");
        try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE coresuite_casino_stats(uuid TEXT PRIMARY KEY,player_name TEXT,total_wagered REAL,total_won REAL,games_played INTEGER,biggest_win REAL,last_played BIGINT)");
        }
        for(int restart=0;restart<3;restart++) {
            try(var connection=DriverManager.getConnection(url);var statement=connection.prepareStatement(CasinoStatsManager.recordSql(false))) {
                statement.setString(1,"alice");statement.setString(2,"Alice");statement.setDouble(3,100);
                statement.setDouble(4,150);statement.setDouble(5,150);statement.setLong(6,1000+restart);statement.executeUpdate();
            }
        }
        try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement();var rows=statement.executeQuery("SELECT * FROM coresuite_casino_stats")) {
            assertTrue(rows.next());assertEquals(3,rows.getInt("games_played"));assertEquals(450,rows.getDouble("total_won"));assertEquals(300,rows.getDouble("total_wagered"));
        }
    }
}
