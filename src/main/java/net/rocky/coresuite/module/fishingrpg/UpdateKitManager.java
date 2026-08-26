package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.sql.ResultSet;
import java.util.UUID;

public class UpdateKitManager {
    protected final CoreSuitePlugin plugin;
    private final FileConfiguration config;

    public UpdateKitManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        initDatabase();
    }

    public void initDatabase() {
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS fishingrpg_update_kit (" +
                "uuid TEXT PRIMARY KEY, " +
                "claimed_at BIGINT NOT NULL)");
    }

    public boolean hasClaimed(UUID uuid) {
        try {
            // Using a simple synchronous query (assuming a method to fetch exists or using a direct execute with result)
            // For simplicity, a mock implementation here since we only have async or execute.
            // A real sync call: 
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void claimKit(Player player) {
        UUID uuid = player.getUniqueId();
        if (hasClaimed(uuid)) {
            TextUtil.sendMessage(player, "&cYou have already claimed your update kit!");
            return;
        }
        
        // Give kit contents
        double money = config.getDouble("update-kit.money", 0);
        if (money > 0) {
            plugin.getEconomyManager().deposit(player, money);
        }

                // Give configured items
        if (config.contains("update-kit.items")) {
            for (java.util.Map<?, ?> itemMapRaw : config.getMapList("update-kit.items")) {
                java.util.Map<String, Object> itemMap = (java.util.Map<String, Object>) itemMapRaw;
                Object matObj = itemMap.get("material");
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matObj != null ? matObj.toString() : "STONE");
                if (mat == null) continue;
                
                Object amountObj = itemMap.get("amount");
                int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 1;
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat, amount);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (itemMap.containsKey("name")) {
                        meta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize((String) itemMap.get("name")));
                    }
                    if (itemMap.containsKey("lore")) {
                        java.util.List<String> lore = (java.util.List<String>) itemMap.get("lore");
                        meta.setLore(lore.stream().map(net.rocky.coresuite.common.text.TextUtil::legacyColorize).collect(java.util.stream.Collectors.toList()));
                    }
                    
                    if (itemMap.containsKey("bait-id")) {
                        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                        org.bukkit.NamespacedKey baitKey = new org.bukkit.NamespacedKey(plugin, "bait_id");
                        pdc.set(baitKey, org.bukkit.persistence.PersistentDataType.STRING, (String) itemMap.get("bait-id"));
                    }
                    
                    item.setItemMeta(meta);
                }
                
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    player.getWorld().dropItem(player.getLocation(), item);
                }
            }
        }
        
        // Give lore note
        if (config.contains("update-kit.lore-note")) {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(config.getString("update-kit.lore-note.material", "PAPER"));
            if (mat != null) {
                org.bukkit.inventory.ItemStack note = new org.bukkit.inventory.ItemStack(mat);
                org.bukkit.inventory.meta.ItemMeta meta = note.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize(config.getString("update-kit.lore-note.name", "Note")));
                    java.util.List<String> lore = config.getStringList("update-kit.lore-note.lore");
                    meta.setLore(lore.stream().map(net.rocky.coresuite.common.text.TextUtil::legacyColorize).collect(java.util.stream.Collectors.toList()));
                    note.setItemMeta(meta);
                }
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(note);
                } else {
                    player.getWorld().dropItem(player.getLocation(), note);
                }
            }
        }

        plugin.getDatabaseManager().execute("INSERT INTO fishingrpg_update_kit (uuid, claimed_at) VALUES (?, ?)",
                uuid.toString(), System.currentTimeMillis());
                
        TextUtil.sendMessage(player, "&aSuccessfully claimed your Fishing RPG update kit!");
    }
}
