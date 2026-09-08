package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BaitManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final Map<UUID, ActiveBait> activeBaits = new ConcurrentHashMap<>();
    private final NamespacedKey baitKey;

    public static class ActiveBait {
        public String baitId;
        public int castsRemaining;
        public double catchBonus;
        public double weightBonus;
        public String rarityBias;

        public ActiveBait(String baitId, int castsRemaining, double catchBonus, double weightBonus, String rarityBias) {
            this.baitId = baitId;
            this.castsRemaining = castsRemaining;
            this.catchBonus = catchBonus;
            this.weightBonus = weightBonus;
            this.rarityBias = rarityBias;
        }
    }

    public BaitManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        this.baitKey = new NamespacedKey(plugin, "bait_id");
        initDatabase();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS fishingrpg_active_baits (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "bait_id VARCHAR(64)," +
                "casts_remaining INT," +
                "catch_bonus DOUBLE," +
                "weight_bonus DOUBLE," +
                "rarity_bias VARCHAR(32)" +
                ");");
    }

    public void loadPlayer(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM fishingrpg_active_baits WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ActiveBait bait = new ActiveBait(
                                rs.getString("bait_id"),
                                rs.getInt("casts_remaining"),
                                rs.getDouble("catch_bonus"),
                                rs.getDouble("weight_bonus"),
                                rs.getString("rarity_bias")
                        );
                        activeBaits.put(uuid, bait);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        ActiveBait bait = activeBaits.remove(uuid);
        if (bait != null) {
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "REPLACE INTO fishingrpg_active_baits (uuid, bait_id, casts_remaining, catch_bonus, weight_bonus, rarity_bias) " +
                             "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, bait.baitId);
                    ps.setInt(3, bait.castsRemaining);
                    ps.setDouble(4, bait.catchBonus);
                    ps.setDouble(5, bait.weightBonus);
                    ps.setString(6, bait.rarityBias);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public ItemStack createBaitItem(String baitId, FileConfiguration config) {
        String path = "baits." + baitId;
        String matName = config.getString(path + ".material", "WHEAT_SEEDS");
        Material material = Material.matchMaterial(matName);
        if (material == null) material = Material.WHEAT_SEEDS;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString(path + ".name", "&eBait");
            meta.displayName(TextUtil.colorize(name));

            List<String> lore = config.getStringList(path + ".lore");
            meta.lore(lore.stream().map(TextUtil::colorize).collect(Collectors.toList()));

            meta.getPersistentDataContainer().set(baitKey, PersistentDataType.STRING, baitId);

            String model = config.getString(path + ".model");
            if (model != null && !model.isEmpty()) {
                meta.setItemModel(NamespacedKey.fromString(model));
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean activateBait(Player player, String baitId, FileConfiguration config) {
        String path = "baits." + baitId;
        if (!config.contains(path)) return false;

        int casts = config.getInt(path + ".casts", 10);
        double catchBonus = config.getDouble(path + ".catch-bonus", 0.0);
        double weightBonus = config.getDouble(path + ".weight-bonus", 0.0);
        String rarityBias = config.getString(path + ".rarity-bias", null);

        ActiveBait bait = new ActiveBait(baitId, casts, catchBonus, weightBonus, rarityBias);
        activeBaits.put(player.getUniqueId(), bait);

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "REPLACE INTO fishingrpg_active_baits (uuid, bait_id, casts_remaining, catch_bonus, weight_bonus, rarity_bias) " +
                         "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, bait.baitId);
                ps.setInt(3, bait.castsRemaining);
                ps.setDouble(4, bait.catchBonus);
                ps.setDouble(5, bait.weightBonus);
                ps.setString(6, bait.rarityBias);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        String msg = config.getString("messages.bait-activated", "&aBait activated for %casts% casts!");
        TextUtil.sendMessage(player, msg.replace("%casts%", String.valueOf(casts)));
        return true;
    }

    public ActiveBait getActiveBait(UUID uuid) {
        return activeBaits.get(uuid);
    }

    public void consumeCast(UUID uuid, double baitSaverChance) {
        ActiveBait bait = activeBaits.get(uuid);
        if (bait == null) return;

        if (Math.random() < baitSaverChance) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) TextUtil.sendActionBar(p, "&a✦ Bait saved!");
            return;
        }

        bait.castsRemaining--;
        if (bait.castsRemaining <= 0) {
            activeBaits.remove(uuid);
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM fishingrpg_active_baits WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) TextUtil.sendMessage(p, "&cBait depleted.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.getPersistentDataContainer().has(baitKey, PersistentDataType.STRING)) {
                    e.setCancelled(true);
                    String baitId = meta.getPersistentDataContainer().get(baitKey, PersistentDataType.STRING);
                    // Assume we use plugin.getConfig()
                    if (activateBait(e.getPlayer(), baitId, plugin.getConfig())) {
                        item.setAmount(item.getAmount() - 1);
                        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    public void shutdown() {
        for (UUID uuid : activeBaits.keySet()) {
            unloadPlayer(uuid);
        }
        activeBaits.clear();
    }
}
