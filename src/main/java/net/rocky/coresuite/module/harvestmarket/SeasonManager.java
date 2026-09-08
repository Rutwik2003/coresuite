package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SeasonManager {
    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private Season currentSeason;

    public SeasonManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void initialize(Runnable callback) {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_harvest_seasons (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "crops TEXT NOT NULL, " +
            "started_at BIGINT NOT NULL, " +
            "ends_at BIGINT NOT NULL, " +
            "active INTEGER NOT NULL DEFAULT 1)"
        );

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_harvest_seasons WHERE active = 1 ORDER BY id DESC LIMIT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        String cropsStr = rs.getString("crops");
                        long startedAt = rs.getLong("started_at");
                        long endsAt = rs.getLong("ends_at");
                        
                        List<Material> crops = Arrays.stream(cropsStr.split(","))
                            .filter(s -> !s.isEmpty())
                            .map(Material::valueOf)
                            .collect(Collectors.toList());
                        
                        Season season = new Season(id, crops, startedAt, endsAt, true);
                        if (season.isExpired()) {
                            // Close async and let checkAndAdvance handle it on main thread next tick
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                this.currentSeason = season;
                                checkAndAdvance();
                                if (callback != null) callback.run();
                            });
                            return;
                        } else {
                            this.currentSeason = season;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error loading season", e);
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (this.currentSeason == null) {
                    createNewSeason(callback);
                } else {
                    if (callback != null) callback.run();
                }
            });
        });
    }

    public Season getCurrentSeason() {
        return currentSeason;
    }

    public boolean isInSeason(Material material) {
        if (currentSeason == null) return false;
        return currentSeason.getInSeasonCrops().contains(material);
    }

    public void checkAndAdvance() {
        if (currentSeason != null && currentSeason.isExpired()) {
            advanceSeason();
        }
    }

    public void advanceSeason() {
        if (currentSeason != null) {
            final int oldId = currentSeason.getId();
            plugin.getDatabaseManager().executeAsync(() -> {
                plugin.getDatabaseManager().execute("UPDATE coresuite_harvest_seasons SET active = 0 WHERE id = ?", oldId);
            });
        }
        createNewSeason(() -> {
            if (currentSeason != null) {
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    TextUtil.sendMessage(p, "&2\uD83C\uDF3E &aA new Harvest Season has begun!");
                    TextUtil.sendMessage(p, "&7This week's featured crops: " + currentSeason.getCropListFormatted());
                    TextUtil.sendMessage(p, "&7Sell them at the Market for &e" + config.getDouble("bonus-multiplier", 2.5) + "× &7the base price!");
                }
            }
        });
    }

    private void createNewSeason(Runnable callback) {
        Set<String> keys = config.getConfigurationSection("crop-prices").getKeys(false);
        List<Material> pool = new ArrayList<>();
        for (String k : keys) {
            try {
                pool.add(Material.valueOf(k.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        
        Collections.shuffle(pool);
        int perSeason = config.getInt("crops-per-season", 4);
        List<Material> selected = pool.stream().limit(perSeason).collect(Collectors.toList());
        
        long now = System.currentTimeMillis();
        long duration = config.getInt("season-duration-hours", 168) * 3600000L;
        long ends = now + duration;
        
        String cropString = selected.stream().map(Material::name).collect(Collectors.joining(","));
        
        plugin.getDatabaseManager().executeAsync(() -> {
            int newId = -1;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO coresuite_harvest_seasons (crops, started_at, ends_at, active) VALUES (?, ?, ?, 1)",
                     Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, cropString);
                stmt.setLong(2, now);
                stmt.setLong(3, ends);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        newId = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error creating season", e);
            }
            
            final int finalId = newId;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalId != -1) {
                    this.currentSeason = new Season(finalId, selected, now, ends, true);
                }
                if (callback != null) callback.run();
            });
        });
    }
}
