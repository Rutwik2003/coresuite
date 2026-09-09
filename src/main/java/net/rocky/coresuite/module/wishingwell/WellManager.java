package net.rocky.coresuite.module.wishingwell;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import net.rocky.coresuite.common.text.TextUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class WellManager {
    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;

    private int currentRoundId = 1;
    private double currentPot = 0.0;
    private long nextDrawAt = 0L;   // epoch-ms when next draw fires
    private final Map<UUID, Double> currentEntries = new ConcurrentHashMap<>();
    private final List<WellHistoryEntry> recentHistory = new ArrayList<>();


    public WellManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_well_entries (" +
            "  uuid TEXT NOT NULL," +
            "  player_name TEXT NOT NULL," +
            "  round_id INTEGER NOT NULL," +
            "  amount REAL NOT NULL DEFAULT 0," +
            "  PRIMARY KEY (uuid, round_id)" +
            ");"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_well_rounds (" +
            "  round_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  pot_total REAL NOT NULL DEFAULT 0," +
            "  winner_uuid TEXT," +
            "  winner_name TEXT," +
            "  drawn_at BIGINT," +
            "  next_draw_at BIGINT" +
            ");"
        );
        loadCurrentRound();
        loadHistory();
    }

    /** Returns the epoch-ms timestamp when the next draw will fire. */
    public long getNextDrawAt() {
        return nextDrawAt;
    }

    /**
     * Called every 5 minutes by the scheduler.
     * Fires a draw if enough real-world time has elapsed since the last draw.
     */
    public void checkAndDraw() {
        if (nextDrawAt > 0 && System.currentTimeMillis() >= nextDrawAt) {
            performDraw();
        }
    }

    public void loadCurrentRound() {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                // Find latest un-drawn round
                int roundId = -1;
                double pot = 0;
                long storedNextDrawAt = 0;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT round_id, pot_total, next_draw_at FROM coresuite_well_rounds WHERE winner_uuid IS NULL ORDER BY round_id DESC LIMIT 1")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            roundId = rs.getInt("round_id");
                            pot = rs.getDouble("pot_total");
                            storedNextDrawAt = rs.getLong("next_draw_at");
                        }
                    }
                }

                long intervalMs = config.getInt("draw-interval-hours", 24) * 3600_000L;

                if (roundId == -1) {
                    // Create brand-new round, schedule draw intervalMs from now
                    long nextDraw = System.currentTimeMillis() + intervalMs;
                    try (PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO coresuite_well_rounds (pot_total, next_draw_at) VALUES (0, ?)")) {
                        insertStmt.setLong(1, nextDraw);
                        insertStmt.executeUpdate();
                    }
                    try (PreparedStatement selectStmt = conn.prepareStatement("SELECT last_insert_rowid() AS id")) {
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            if (rs.next()) roundId = rs.getInt("id");
                        }
                    }
                    pot = 0;
                    storedNextDrawAt = nextDraw;
                } else if (storedNextDrawAt == 0) {
                    // Old round with no next_draw_at — schedule from now
                    storedNextDrawAt = System.currentTimeMillis() + intervalMs;
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE coresuite_well_rounds SET next_draw_at = ? WHERE round_id = ?")) {
                        upd.setLong(1, storedNextDrawAt);
                        upd.setInt(2, roundId);
                        upd.executeUpdate();
                    }
                }

                final int finalRoundId = roundId;
                final double finalPot = pot;
                final long finalNextDraw = storedNextDrawAt;

                // Load entries for this round
                Map<UUID, Double> entries = new HashMap<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT uuid, amount FROM coresuite_well_entries WHERE round_id = ?")) {
                    stmt.setInt(1, finalRoundId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            entries.put(UUID.fromString(rs.getString("uuid")), rs.getDouble("amount"));
                        }
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    this.currentRoundId = finalRoundId;
                    this.currentPot = finalPot;
                    this.nextDrawAt = finalNextDraw;
                    this.currentEntries.clear();
                    this.currentEntries.putAll(entries);
                    plugin.getLogger().info("[WishingWell] Round " + finalRoundId + " loaded. Next draw at: "
                        + new java.util.Date(finalNextDraw));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error loading current round", e);
            }
        });
    }

    public void loadHistory() {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<WellHistoryEntry> history = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT winner_name, pot_total, drawn_at FROM coresuite_well_rounds WHERE winner_uuid IS NOT NULL ORDER BY drawn_at DESC LIMIT 5")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        history.add(new WellHistoryEntry(
                            rs.getString("winner_name"),
                            rs.getDouble("pot_total"),
                            rs.getLong("drawn_at")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error loading history", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.recentHistory.clear();
                this.recentHistory.addAll(history);
            });
        });
    }

    public double getCurrentPot() {
        return currentPot;
    }

    public int getCurrentRoundId() {
        return currentRoundId;
    }

    public int getContributorCount() {
        return currentEntries.size();
    }

    public double getPlayerContribution(UUID uuid) {
        return currentEntries.getOrDefault(uuid, 0.0);
    }

    public double getPlayerOddsPercent(UUID uuid) {
        return calculateOdds(uuid, currentEntries);
    }

    public double getProjectedOdds(UUID uuid, double throwAmount) {
        Map<UUID, Double> tempEntries = new HashMap<>(currentEntries);
        tempEntries.put(uuid, tempEntries.getOrDefault(uuid, 0.0) + throwAmount);
        return calculateOdds(uuid, tempEntries);
    }

    private double calculateOdds(UUID uuid, Map<UUID, Double> entries) {
        if (!entries.containsKey(uuid)) return 0.0;
        double totalWeight = 0;
        double playerWeight = 0;
        for (Map.Entry<UUID, Double> e : entries.entrySet()) {
            double w = Math.log10(e.getValue() + 1) * 1000;
            totalWeight += w;
            if (e.getKey().equals(uuid)) {
                playerWeight = w;
            }
        }
        if (totalWeight == 0) return 0.0;
        return (playerWeight / totalWeight) * 100.0;
    }

    public void throwMoney(Player player, double amount, Runnable onSuccess, Runnable onFail) {
        if (amount <= 0) {
            if (onFail != null) onFail.run();
            return;
        }
        if (plugin.getEconomyManager().withdraw(player, amount)) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            
            currentPot += amount;
            double newAmount = currentEntries.getOrDefault(uuid, 0.0) + amount;
            currentEntries.put(uuid, newAmount);

            // DB Update
            plugin.getDatabaseManager().execute(
                "INSERT INTO coresuite_well_entries (uuid, player_name, round_id, amount) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid, round_id) DO UPDATE SET amount = amount + excluded.amount",
                uuid.toString(), name, currentRoundId, amount
            );
            plugin.getDatabaseManager().execute(
                "UPDATE coresuite_well_rounds SET pot_total = ? WHERE round_id = ?",
                currentPot, currentRoundId
            );

            if (onSuccess != null) onSuccess.run();
        } else {
            if (onFail != null) onFail.run();
        }
    }

    public void performDraw() {
        if (currentEntries.isEmpty()) {
            // No entries, just reset time basically or do nothing. We'll do nothing.
            return;
        }

        plugin.getDatabaseManager().executeAsync(() -> {
            Map<UUID, Long> weights = new HashMap<>();
            for (Map.Entry<UUID, Double> e : currentEntries.entrySet()) {
                long w = (long)(Math.log10(e.getValue() + 1) * 1000);
                weights.put(e.getKey(), w);
            }
            long totalWeight = weights.values().stream().mapToLong(Long::longValue).sum();
            if (totalWeight == 0) return; // no entries
            
            long roll = ThreadLocalRandom.current().nextLong(totalWeight);
            UUID winner = null;
            long cumulative = 0;
            for (Map.Entry<UUID, Long> e : weights.entrySet()) {
                cumulative += e.getValue();
                if (roll < cumulative) { winner = e.getKey(); break; }
            }
            
            if (winner != null) {
                final UUID finalWinner = winner;
                final double finalPot = currentPot;
                
                // Get winner name
                String winnerName = "Unknown";
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT player_name FROM coresuite_well_entries WHERE uuid = ? AND round_id = ? LIMIT 1")) {
                    stmt.setString(1, finalWinner.toString());
                    stmt.setInt(2, currentRoundId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            winnerName = rs.getString("player_name");
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "DB error fetching winner name", e);
                }

                long now = System.currentTimeMillis();
                final String fWinnerName = winnerName;

                // Update DB
                plugin.getDatabaseManager().execute(
                    "UPDATE coresuite_well_rounds SET winner_uuid = ?, winner_name = ?, drawn_at = ? WHERE round_id = ?",
                    finalWinner.toString(), fWinnerName, now, currentRoundId
                );
                
                // Create new round with scheduled next draw time
                int nextRoundId = -1;
                long nextDraw = System.currentTimeMillis() + (config.getInt("draw-interval-hours", 24) * 3600_000L);
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("INSERT INTO coresuite_well_rounds (pot_total, next_draw_at) VALUES (0, ?)")) {
                    stmt.setLong(1, nextDraw);
                    stmt.executeUpdate();
                    try (PreparedStatement selectStmt = conn.prepareStatement("SELECT last_insert_rowid() AS id")) {
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            if (rs.next()) nextRoundId = rs.getInt("id");
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "DB error creating new round", e);
                }

                final int fNextRoundId = nextRoundId;
                final long fNextDraw   = nextDraw;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Deposit money
                    plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(finalWinner), finalPot);

                    // Reset cache
                    currentPot = 0;
                    currentEntries.clear();
                    if (fNextRoundId != -1) {
                        currentRoundId = fNextRoundId;
                        nextDrawAt = fNextDraw;
                        plugin.getLogger().info("[WishingWell] Next draw scheduled for: " + new java.util.Date(fNextDraw));
                    }

                    loadHistory();
                    
                    // Broadcast
                    if (config.getBoolean("announce-draw", true)) {
                        String msg = config.getString("messages.winner-broadcast", "&6✨ &e{player} &6won the Wishing Well pot of &a{amount}&6! &7Congratulations!");
                        msg = msg.replace("{player}", fWinnerName).replace("{amount}", plugin.getEconomyManager().format(finalPot));
                        Bukkit.broadcast(TextUtil.colorize(config.getString("messages.prefix", "&8[&b✨ Well&8] &r") + msg));
                    }
                    
                    Player winnerPlayer = Bukkit.getPlayer(finalWinner);
                    if (winnerPlayer != null && winnerPlayer.isOnline()) {
                        TextUtil.sendTitle(winnerPlayer, "&6&lJACKPOT!", "&eYou won the Wishing Well pot of &a" + plugin.getEconomyManager().format(finalPot) + "&e!", 10, 70, 20);
                    }
                    
                    // Fireworks if enabled
                    if (config.getBoolean("draw-fireworks", true) && config.getInt("npc-id", -1) != -1) {
                        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
                            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(config.getInt("npc-id"));
                            if (npc != null && npc.isSpawned()) {
                                Location loc = npc.getEntity().getLocation();
                                Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                                FireworkMeta fwm = fw.getFireworkMeta();
                                fwm.addEffect(FireworkEffect.builder().withColor(Color.YELLOW).withColor(Color.AQUA).with(FireworkEffect.Type.BALL_LARGE).build());
                                fwm.setPower(1);
                                fw.setFireworkMeta(fwm);
                            }
                        }
                    }
                });
            }
        });
    }

    public List<WellHistoryEntry> getRecentHistory() {
        return recentHistory;
    }

    public void clearCache() {
        currentEntries.clear();
        recentHistory.clear();
    }
}
