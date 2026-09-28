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

    private final CoreSuitePlugin plugin;
    private final Map<UUID, PlayerCasinoStats> statsCache = new ConcurrentHashMap<>();

    private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();

    public CasinoStatsManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_casino_stats (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
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
        PlayerCasinoStats result = statsCache.get(uuid);
        if (result == null) {
            loadPlayer(uuid, fallbackName, null);
            // No synthetic zero totals while persisted data is still loading.
        }
        return result;
    }

    public void unloadPlayer(UUID uuid) {
        sessions.remove(uuid);
        statsCache.remove(uuid);
    }

    public void clearCache() {
        sessions.clear();
        statsCache.clear();
    }

    public void loadPlayer(UUID uuid, String name, Runnable onLoaded) {
        Object session = new Object();
        if (sessions.putIfAbsent(uuid, session) != null) return;
        refresh(uuid, name, session, onLoaded);
    }

    private void refresh(UUID uuid, String name, Object session, Runnable onLoaded) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_casino_stats WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                PlayerCasinoStats stats;
                try (ResultSet rs = stmt.executeQuery()) {
                    stats = rs.next() ? new PlayerCasinoStats(uuid, rs.getString("player_name"),
                            rs.getDouble("total_wagered"), rs.getDouble("total_won"), rs.getInt("games_played"),
                            rs.getDouble("biggest_win"), rs.getLong("last_played"))
                            : new PlayerCasinoStats(uuid, name, 0, 0, 0, 0, System.currentTimeMillis());
                }
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (sessions.get(uuid) != session) return;
                    statsCache.put(uuid, stats);
                    if (onLoaded != null) onLoaded.run();
                });
            } catch (SQLException e) {
                sessions.remove(uuid, session);
                plugin.getLogger().warning("Failed to load casino stats for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void recordGame(UUID uuid, String name, double wagered, double won) {
        if (!Double.isFinite(wagered) || !Double.isFinite(won) || wagered < 0 || won < 0) return;
        // Add to persisted totals, never replace them with a cold/partially loaded cache.
        plugin.getDatabaseManager().execute(recordSql(plugin.getDatabaseManager().isMySQL()),
                uuid.toString(), name, wagered, won, won, System.currentTimeMillis());
        Object session = sessions.get(uuid);
        if (session != null) refresh(uuid, name, session, null);
    }

    static String recordSql(boolean mysql) {
        String base = "INSERT INTO coresuite_casino_stats (uuid,player_name,total_wagered,total_won,games_played,biggest_win,last_played) VALUES (?,?,?,?,1,?,?) ";
        return base + (mysql
                ? "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), total_wagered=total_wagered+VALUES(total_wagered), total_won=total_won+VALUES(total_won), games_played=games_played+1, biggest_win=GREATEST(biggest_win,VALUES(biggest_win)), last_played=VALUES(last_played)"
                : "ON CONFLICT(uuid) DO UPDATE SET player_name=excluded.player_name, total_wagered=total_wagered+excluded.total_wagered, total_won=total_won+excluded.total_won, games_played=games_played+1, biggest_win=MAX(biggest_win,excluded.biggest_win), last_played=excluded.last_played");
    }

    public void getTopWinners(int limit, Consumer<List<PlayerCasinoStats>> callback, Runnable onFailure) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<PlayerCasinoStats> list = new ArrayList<>();
            String sql = "SELECT * FROM coresuite_casino_stats ORDER BY total_won DESC, uuid ASC LIMIT ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, Math.max(1, Math.min(100, limit)));
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
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, onFailure);
                return;
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }
}
