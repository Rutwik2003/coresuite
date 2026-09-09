package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SellManager {
    private final CoreSuitePlugin plugin;
    private final SeasonManager seasonManager;
    private final EconomyManager economy;
    private final FileConfiguration config;
    private final Map<UUID, Integer> quotaCache = new ConcurrentHashMap<>();

    public SellManager(CoreSuitePlugin plugin, SeasonManager seasonManager, EconomyManager economy, FileConfiguration config) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.economy = economy;
        this.config = config;

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_harvest_quota (" +
            "uuid TEXT NOT NULL, " +
            "season_id INTEGER NOT NULL, " +
            "items_sold INTEGER NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (uuid, season_id))"
        );
    }

    public void loadQuota(UUID uuid, Runnable callback) {
        if (seasonManager.getCurrentSeason() == null) {
            if (callback != null) callback.run();
            return;
        }
        int seasonId = seasonManager.getCurrentSeason().getId();
        plugin.getDatabaseManager().executeAsync(() -> {
            int sold = 0;
            try {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT items_sold FROM coresuite_harvest_quota WHERE uuid = ? AND season_id = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setInt(2, seasonId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            sold = rs.getInt("items_sold");
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "DB error loading quota", e);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error loading quota", t);
            } finally {
                final int finalSold = sold;
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    quotaCache.put(uuid, finalSold);
                    if (callback != null) callback.run();
                });
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        quotaCache.remove(uuid);
    }

    public void clearCache() {
        quotaCache.clear();
    }

    public int getQuotaUsed(UUID uuid) {
        return quotaCache.getOrDefault(uuid, 0);
    }

    public int getQuotaRemaining(UUID uuid) {
        int max = config.getInt("max-quota-per-season", 5000);
        return Math.max(0, max - getQuotaUsed(uuid));
    }

    public double calculatePrice(Material material, boolean isGolden) {
        double basePrice = config.getDouble("crop-prices." + material.name(), 0);
        if (isGolden) {
            return basePrice * config.getDouble("golden-multiplier", 25.0);
        }
        return basePrice;
    }

    public boolean isGoldenCrop(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "golden_crop");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public Map<Material, Double> getBasePrices() {
        Map<Material, Double> prices = new HashMap<>();
        if (config.contains("crop-prices")) {
            for (String key : config.getConfigurationSection("crop-prices").getKeys(false)) {
                try {
                    prices.put(Material.valueOf(key.toUpperCase()), config.getDouble("crop-prices." + key));
                } catch (Exception ignored) {}
            }
        }
        return prices;
    }

    public void sellCrops(Player player, Material material, int amount, boolean isGolden, Consumer<Double> onSuccess, Runnable onFail) {
        int has = countItems(player, material, isGolden);
        if (has < amount) {
            onFail.run();
            return;
        }

        removeItems(player, material, amount, isGolden);

        double totalEarned = 0;
        int seasonId = seasonManager.getCurrentSeason() != null ? seasonManager.getCurrentSeason().getId() : -1;
        boolean inSeason = seasonManager.isInSeason(material);
        
        if (isGolden) {
            totalEarned = amount * calculatePrice(material, true);
        } else if (inSeason) {
            int used = getQuotaUsed(player.getUniqueId());
            int max = config.getInt("max-quota-per-season", 5000);
            int remaining = Math.max(0, max - used);
            
            if (remaining >= amount) {
                totalEarned = amount * calculatePrice(material, false) * config.getDouble("bonus-multiplier", 2.5);
                updateQuota(player.getUniqueId(), seasonId, amount);
            } else if (remaining > 0) {
                totalEarned += remaining * calculatePrice(material, false) * config.getDouble("bonus-multiplier", 2.5);
                totalEarned += (amount - remaining) * calculatePrice(material, false);
                updateQuota(player.getUniqueId(), seasonId, amount);
            } else {
                totalEarned = amount * calculatePrice(material, false);
                updateQuota(player.getUniqueId(), seasonId, amount); // still record it
            }
        } else {
            totalEarned = amount * calculatePrice(material, false);
        }

        economy.deposit(player, totalEarned);
        onSuccess.accept(totalEarned);
    }

    private void updateQuota(UUID uuid, int seasonId, int amount) {
        if (seasonId == -1) return;
        int used = quotaCache.getOrDefault(uuid, 0) + amount;
        quotaCache.put(uuid, used);
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().execute(
                "INSERT INTO coresuite_harvest_quota (uuid, season_id, items_sold) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, season_id) DO UPDATE SET items_sold = items_sold + ?", 
                uuid.toString(), seasonId, amount, amount
            );
        });
    }

    private int countItems(Player player, Material material, boolean isGolden) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (isGolden == isGoldenCrop(item)) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount, boolean isGolden) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (isGolden == isGoldenCrop(item)) {
                    if (item.getAmount() <= left) {
                        left -= item.getAmount();
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - left);
                        left = 0;
                    }
                    if (left <= 0) break;
                }
            }
        }
    }
}
