package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class FishBagManager {
    private final CoreSuitePlugin plugin;
    private final Map<UUID, ItemStack[]> cache = new ConcurrentHashMap<>();

    public FishBagManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadBag(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT inventory_data FROM coresuite_fish_bag WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String data = rs.getString("inventory_data");
                        if (data != null && !data.isEmpty()) {
                            ItemStack[] items = fromBase64(data);
                            cache.put(uuid, items);
                        } else {
                            cache.put(uuid, new ItemStack[450]);
                        }
                    } else {
                        cache.put(uuid, new ItemStack[450]);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fish bag for " + uuid, e);
                cache.put(uuid, new ItemStack[450]);
            }
        });
    }

    public void saveBag(UUID uuid) {
        ItemStack[] items = cache.get(uuid);
        if (items != null) {
            String data = toBase64(items);
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "REPLACE INTO coresuite_fish_bag (uuid, inventory_data) VALUES (?, ?)"
                     )) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, data);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save fish bag for " + uuid, e);
                }
            });
        }
    }

    public void saveBagSync(UUID uuid) {
        ItemStack[] items = cache.get(uuid);
        if (items == null) return;
        String data = toBase64(items);
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "REPLACE INTO coresuite_fish_bag (uuid, inventory_data) VALUES (?, ?)"
             )) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, data);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save fish bag (sync) for " + uuid, e);
        }
    }

    public void unloadBag(UUID uuid) {
        saveBagSync(uuid);
        cache.remove(uuid);
    }

    public ItemStack[] getBag(UUID uuid) {
        return cache.getOrDefault(uuid, new ItemStack[450]);
    }

    public void updateBag(UUID uuid, ItemStack[] items) {
        cache.put(uuid, items);
    }

    public void compactBag(UUID uuid) {
        ItemStack[] bag = getBag(uuid);
        if (bag == null) return;
        
        ItemStack[] newBag = new ItemStack[bag.length];
        int index = 0;
        for (ItemStack item : bag) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                newBag[index++] = item;
            }
        }
        updateBag(uuid, newBag);
    }

    public void sortBag(UUID uuid, CustomFishingModule module, boolean byRarity) {
        ItemStack[] bag = getBag(uuid);
        if (bag == null) return;

        java.util.List<ItemStack> fishList = new java.util.ArrayList<>();
        for (ItemStack item : bag) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                fishList.add(item);
            }
        }

        org.bukkit.NamespacedKey speciesKey = new org.bukkit.NamespacedKey(plugin, "fish_species");
        org.bukkit.NamespacedKey weightKey = new org.bukkit.NamespacedKey(plugin, "fish_weight");
        
        java.util.List<String> knownRarities = java.util.Arrays.asList("Mythic", "Legendary", "Epic", "Rare", "Uncommon", "Common");

        fishList.sort((a, b) -> {
            org.bukkit.inventory.meta.ItemMeta metaA = a.getItemMeta();
            org.bukkit.inventory.meta.ItemMeta metaB = b.getItemMeta();
            if (metaA == null || metaB == null) return 0;
            
            Double weightA = metaA.getPersistentDataContainer().get(weightKey, org.bukkit.persistence.PersistentDataType.DOUBLE);
            Double weightB = metaB.getPersistentDataContainer().get(weightKey, org.bukkit.persistence.PersistentDataType.DOUBLE);
            if (weightA == null) weightA = 0.0;
            if (weightB == null) weightB = 0.0;

            if (byRarity) {
                String speciesA = metaA.getPersistentDataContainer().get(speciesKey, org.bukkit.persistence.PersistentDataType.STRING);
                String speciesB = metaB.getPersistentDataContainer().get(speciesKey, org.bukkit.persistence.PersistentDataType.STRING);
                
                String rarityA = speciesA != null ? module.getConfig().getString("species." + speciesA + ".rarity", "Common") : "Common";
                String rarityB = speciesB != null ? module.getConfig().getString("species." + speciesB + ".rarity", "Common") : "Common";
                
                int rankA = knownRarities.indexOf(rarityA);
                if (rankA == -1) rankA = 99; // unknown rarity at the end
                int rankB = knownRarities.indexOf(rarityB);
                if (rankB == -1) rankB = 99;
                
                if (rankA != rankB) {
                    return Integer.compare(rankA, rankB); // ascending rank index, so Mythic (0) comes first
                }
                if (speciesA != null && speciesB != null && !speciesA.equals(speciesB)) {
                    return speciesA.compareTo(speciesB);
                }
            }
            // Sort by weight descending
            return Double.compare(weightB, weightA);
        });

        ItemStack[] newBag = new ItemStack[bag.length];
        for (int i = 0; i < fishList.size(); i++) {
            newBag[i] = fishList.get(i);
        }
        updateBag(uuid, newBag);
    }

    private String toBase64(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    private ItemStack[] fromBase64(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load item stacks.", e);
        }
    }

    public void openBag(org.bukkit.entity.Player player, int totalPages, int currentPage) {
        org.bukkit.inventory.Inventory bagInv = org.bukkit.Bukkit.createInventory(null, 54, "Fish Bag (Page " + (currentPage + 1) + ")");
        ItemStack[] items = getBag(player.getUniqueId());
        
        int start = currentPage * 45;
        for (int i = 0; i < 45; i++) {
            if (start + i < items.length) {
                bagInv.setItem(i, items[start + i]);
            }
        }
        
        // Fill bottom row with light gray glass
        ItemStack filler = new ItemStack(org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(net.rocky.coresuite.common.text.TextUtil.colorize(" "));
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 45; i < 54; i++) {
            bagInv.setItem(i, filler);
        }

        if (currentPage > 0) {
            ItemStack prev = new ItemStack(org.bukkit.Material.ARROW);
            org.bukkit.inventory.meta.ItemMeta meta = prev.getItemMeta();
            meta.displayName(net.rocky.coresuite.common.text.TextUtil.colorize("&aPrevious Page"));
            prev.setItemMeta(meta);
            bagInv.setItem(45, prev);
        }
        if (currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(org.bukkit.Material.ARROW);
            org.bukkit.inventory.meta.ItemMeta meta = next.getItemMeta();
            meta.displayName(net.rocky.coresuite.common.text.TextUtil.colorize("&aNext Page"));
            next.setItemMeta(meta);
            bagInv.setItem(53, next);
        }

        ItemStack sortRarityBtn = new ItemStack(org.bukkit.Material.EMERALD);
        org.bukkit.inventory.meta.ItemMeta sortRarityMeta = sortRarityBtn.getItemMeta();
        sortRarityMeta.displayName(net.rocky.coresuite.common.text.TextUtil.colorize("&a&lSort by Rarity"));
        sortRarityMeta.lore(java.util.Arrays.asList(net.rocky.coresuite.common.text.TextUtil.colorize("&7Click to sort all fish by rarity.")));
        sortRarityBtn.setItemMeta(sortRarityMeta);
        bagInv.setItem(47, sortRarityBtn);

        ItemStack backBtn = new ItemStack(org.bukkit.Material.BARRIER);
        org.bukkit.inventory.meta.ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.displayName(net.rocky.coresuite.common.text.TextUtil.colorize("&c&lBack"));
        backMeta.lore(java.util.Arrays.asList(net.rocky.coresuite.common.text.TextUtil.colorize("&7Return to the main menu.")));
        backBtn.setItemMeta(backMeta);
        bagInv.setItem(49, backBtn);

        ItemStack sortWeightBtn = new ItemStack(org.bukkit.Material.ANVIL);
        org.bukkit.inventory.meta.ItemMeta sortWeightMeta = sortWeightBtn.getItemMeta();
        sortWeightMeta.displayName(net.rocky.coresuite.common.text.TextUtil.colorize("&e&lSort by Weight"));
        sortWeightMeta.lore(java.util.Arrays.asList(net.rocky.coresuite.common.text.TextUtil.colorize("&7Click to sort all fish by weight.")));
        sortWeightBtn.setItemMeta(sortWeightMeta);
        bagInv.setItem(51, sortWeightBtn);
        
        player.openInventory(bagInv);
    }
}
