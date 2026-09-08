package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public class HarvestLeaderboardManager {
    private final CoreSuitePlugin plugin;
    private final SeasonManager seasonManager;
    private final EconomyManager economy;
    private final FileConfiguration config;

    public HarvestLeaderboardManager(CoreSuitePlugin plugin, SeasonManager seasonManager, EconomyManager economy, FileConfiguration config) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.economy = economy;
        this.config = config;

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_harvest_leaderboard (" +
            "uuid TEXT NOT NULL, " +
            "player_name TEXT NOT NULL, " +
            "season_id INTEGER NOT NULL, " +
            "total_sold INTEGER NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (uuid, season_id))"
        );
    }

    public void recordSell(UUID uuid, String playerName, int seasonId, int amount) {
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().execute(
                "INSERT INTO coresuite_harvest_leaderboard (uuid, player_name, season_id, total_sold) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid, season_id) DO UPDATE SET total_sold = total_sold + ?, player_name = ?", 
                uuid.toString(), playerName, seasonId, amount, amount, playerName
            );
        });
    }

    public void getTopSellers(int seasonId, int limit, Consumer<List<LeaderboardEntry>> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT player_name, total_sold FROM coresuite_harvest_leaderboard WHERE season_id = ? ORDER BY total_sold DESC LIMIT ?")) {
                stmt.setInt(1, seasonId);
                stmt.setInt(2, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        list.add(new LeaderboardEntry(rs.getString("player_name"), rs.getInt("total_sold"), rank++));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error fetching leaderboard", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }

    public void distributeSeasonRewards(int seasonId) {
        // Implement distribute logic to top 3 sellers
        // Note: Needs a mechanism to avoid paying out multiple times (e.g., config flag)
        getTopSellers(seasonId, 3, entries -> {
            for (LeaderboardEntry entry : entries) {
                String rankKey = String.valueOf(entry.rank);
                if (config.contains("leaderboard-rewards." + rankKey)) {
                    double money = config.getDouble("leaderboard-rewards." + rankKey + ".money");
                    List<String> cmds = config.getStringList("leaderboard-rewards." + rankKey + ".commands");
                    String broadcast = config.getString("leaderboard-rewards." + rankKey + ".broadcast");
                    
                    if (money > 0) {
                        economy.deposit(Bukkit.getOfflinePlayer(entry.playerName), money);
                    }
                    for (String cmd : cmds) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", entry.playerName));
                    }
                    if (broadcast != null && !broadcast.isEmpty()) {
                        String msg = broadcast.replace("{player}", entry.playerName).replace("{amount}", String.valueOf(entry.totalSold));
                        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                            TextUtil.sendMessage(p, msg);
                        }
                    }
                }
            }
        });
    }

    public static class LeaderboardEntry {
        public final String playerName;
        public final int totalSold;
        public final int rank;

        public LeaderboardEntry(String playerName, int totalSold, int rank) {
            this.playerName = playerName;
            this.totalSold = totalSold;
            this.rank = rank;
        }
    }
}
