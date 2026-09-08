package net.rocky.coresuite.module.gambling;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages player gambling stats, leaderboards, and SQLite persistence.
 */
public class CasinoStatsManager {

    public record PlayerCasinoStats(
        UUID uuid,
        String playerName,
        double totalWagered,
        double totalWon,
        int gamesPlayed,
        double biggestWin,
        long lastPlayed
    ) {
        public double getNetProfit() {
            return totalWon - totalWagered;
        }
    }

    private final CoreSuitePlugin plugin;
    private final Map<UUID, PlayerCasinoStats> statsCache = new ConcurrentHashMap<>();

    public CasinoStatsManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_casino_stats (" +
            "uuid TEXT PRIMARY KEY, " +
            "player_name TEXT NOT NULL, " +
            "total_wagered REAL NOT NULL DEFAULT 0, " +
            "total_won REAL NOT NULL DEFAULT 0, " +
            "games_played INTEGER NOT NULL DEFAULT 0, " +
            "biggest_win REAL NOT NULL DEFAULT 0, " +
            "last_played BIGINT NOT NULL" +
            ")"
        );
    }

    public PlayerCasinoStats getStats(UUID uuid, String fallbackName) {
        return statsCache.computeIfAbsent(uuid, id ->
            new PlayerCasinoStats(id, fallbackName, 0, 0, 0, 0, System.currentTimeMillis())
        );
    }

    public void unloadPlayer(UUID uuid) {
        statsCache.remove(uuid);
    }

    public void clearCache() {
        statsCache.clear();
    }

    public void loadPlayer(UUID uuid, String name, Runnable onLoaded) {
        plugin.getDatabaseManager().executeAsync(() -> {
            String sql = "SELECT * FROM coresuite_casino_stats WHERE uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerCasinoStats stats = new PlayerCasinoStats(
                            uuid,
                            rs.getString("player_name"),
                            rs.getDouble("total_wagered"),
                            rs.getDouble("total_won"),
                            rs.getInt("games_played"),
                            rs.getDouble("biggest_win"),
                            rs.getLong("last_played")
                        );
                        statsCache.put(uuid, stats);
                    } else {
                        statsCache.put(uuid, new PlayerCasinoStats(uuid, name, 0, 0, 0, 0, System.currentTimeMillis()));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load casino stats for " + uuid + ": " + e.getMessage());
            }
            if (onLoaded != null) {
                Bukkit.getScheduler().runTask(plugin, onLoaded);
            }
        });
    }

    public void recordGame(UUID uuid, String name, double wagered, double won) {
        PlayerCasinoStats current = getStats(uuid, name);
        double newWagered = current.totalWagered + wagered;
        double newWon = current.totalWon + won;
        int newPlayed = current.gamesPlayed + 1;
        double newBiggest = Math.max(current.biggestWin, won);
        long now = System.currentTimeMillis();

        PlayerCasinoStats updated = new PlayerCasinoStats(
            uuid, name, newWagered, newWon, newPlayed, newBiggest, now
        );
        statsCache.put(uuid, updated);

        plugin.getDatabaseManager().execute(
            "REPLACE INTO coresuite_casino_stats (uuid, player_name, total_wagered, total_won, games_played, biggest_win, last_played) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            uuid.toString(), name, newWagered, newWon, newPlayed, newBiggest, now
        );
    }

    public void getTopWinners(int limit, Consumer<List<PlayerCasinoStats>> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<PlayerCasinoStats> list = new ArrayList<>();
            String sql = "SELECT * FROM coresuite_casino_stats ORDER BY total_won DESC LIMIT ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new PlayerCasinoStats(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getDouble("total_wagered"),
                            rs.getDouble("total_won"),
                            rs.getInt("games_played"),
                            rs.getDouble("biggest_win"),
                            rs.getLong("last_played")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load casino top winners: " + e.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }
}
